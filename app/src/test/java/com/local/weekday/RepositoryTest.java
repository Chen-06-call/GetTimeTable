package com.local.weekday;

import static org.junit.Assert.*;

import android.content.Context;
import com.local.weekday.Models.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Exercises real Room SQL, JSON validation and restore transactions in a host Android runtime. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
public class RepositoryTest {
  Repository repo;
  ExecutorService worker;

  @Before
  public void setUp() {
    Context c = RuntimeEnvironment.getApplication();
    c.deleteDatabase("weekday.db");
    c.getSharedPreferences("appearance", 0).edit().clear().commit();
    repo = new Repository(c);
    worker = Executors.newSingleThreadExecutor();
  }

  @After
  public void tearDown() {
    repo.close();
    worker.shutdown();
  }

  interface Check {
    void run() throws Exception;
  }

  void background(Check check) throws Exception {
    worker
        .submit(
            () -> {
              try {
                check.run();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .get(40, TimeUnit.SECONDS);
  }

  @Test
  public void backupRestoresCoursesAdjustmentsSchoolsAndPreferences() throws Exception {
    background(
        () -> {
          repo.initialize();
          Term t = repo.store.terms().get(0);
          Course c = new Course();
          c.name = "数学";
          c.termId = t.id;
          c.weeks = CalendarEngine.allWeeks(t.weeks);
          repo.saveCourse(c);
          Adjustment a = new Adjustment();
          a.courseId = c.id;
          a.week = 2;
          a.canceled = true;
          repo.store.save(a);
          School school = new School();
          school.name = "学校";
          school.url = "https://example.edu";
          repo.store.save(school);
          repo.prefs.edit().putInt("days", 5).putInt("textSize", 14).commit();
          Exam exam = new Exam();
          exam.termId = t.id;
          exam.name = "数学期末";
          exam.date = "2026-12-30";
          exam.id = repo.store.save(exam);
          repo.prefs.edit().putBoolean("courseReminders", true).putInt("courseLead", 15).commit();
          JSONObject backup = repo.backup();
          repo.deleteCourse(c);
          assertEquals(0, repo.store.courses(t.id).size());
          repo.prefs.edit().putInt("days", 7).commit();
          repo.restore(backup);
          assertEquals("数学", repo.store.courses(t.id).get(0).name);
          assertEquals(1, repo.store.adjustments().size());
          assertTrue(repo.store.adjustments().get(0).canceled);
          assertTrue(
              repo.store.schools().stream().anyMatch(schoolRow -> schoolRow.name.equals("学校")));
          assertEquals(5, repo.prefs.getInt("days", 0));
          assertEquals(14, repo.prefs.getInt("textSize", 0));
          assertEquals("数学期末", repo.store.exams(t.id).get(0).name);
          assertEquals(30, repo.store.exams(t.id).get(0).remindMinutes);
          assertTrue(repo.prefs.getBoolean("courseReminders", false));
          assertEquals(15, repo.prefs.getInt("courseLead", 0));
          assertFalse(repo.snapshots().isEmpty());
        });
  }

  @Test
  public void invalidBackupCannotEraseExistingCourses() throws Exception {
    background(
        () -> {
          repo.initialize();
          Term t = repo.store.terms().get(0);
          Course c = new Course();
          c.name = "保留课程";
          c.termId = t.id;
          c.weeks = 1;
          repo.saveCourse(c);
          JSONObject bad = repo.backup();
          bad.getJSONArray("courses").getJSONObject(0).put("weeks", 1 << 20);
          try {
            repo.restore(bad);
            fail("invalid data must be rejected");
          } catch (IllegalArgumentException expected) {
          }
          assertEquals("保留课程", repo.store.courses(t.id).get(0).name);
        });
  }

  @Test
  public void versionOneBackupRemainsRestorable() throws Exception {
    background(
        () -> {
          repo.initialize();
          JSONObject old = repo.backup();
          old.put("version", 1);
          old.remove("exams");
          old.getJSONObject("preferences").remove("courseLead");
          old.getJSONObject("preferences").remove("courseReminders");
          Exam e = new Exam();
          e.termId = repo.store.terms().get(0).id;
          e.name = "临时考试";
          repo.store.save(e);
          repo.restore(old);
          assertTrue(repo.store.allExams().isEmpty());
          assertFalse(repo.prefs.getBoolean("courseReminders", false));
          assertEquals(1, repo.store.terms().size());
        });
  }

  @Test
  public void migrationFromVersionOnePreservesExistingSchedule() throws Exception {
    background(
        () -> {
          repo.initialize();
          Term t = repo.store.terms().get(0);
          Course c = new Course();
          c.termId = t.id;
          c.weeks = 1;
          c.name = "升级保留课程";
          repo.saveCourse(c);
          repo.db.close();
          Context context = RuntimeEnvironment.getApplication();
          android.database.sqlite.SQLiteDatabase raw =
              android.database.sqlite.SQLiteDatabase.openDatabase(
                  context.getDatabasePath("weekday.db").getPath(), null, 0);
          raw.execSQL("DROP TABLE exams");
          raw.setVersion(1);
          raw.close();
          try (AppDatabase upgraded = AppDatabase.open(context)) {
            assertEquals("升级保留课程", upgraded.store().courses(t.id).get(0).name);
            assertTrue(upgraded.store().allExams().isEmpty());
            Exam e = new Exam();
            e.termId = t.id;
            e.name = "新增考试";
            upgraded.store().save(e);
            assertEquals(1, upgraded.store().exams(t.id).size());
          }
        });
  }

  @Test
  public void invalidExamBackupCannotEraseExistingSchedule() throws Exception {
    background(
        () -> {
          repo.initialize();
          Term t = repo.store.terms().get(0);
          Exam e = new Exam();
          e.termId = t.id;
          e.name = "保留考试";
          repo.store.save(e);
          JSONObject bad = repo.backup();
          bad.getJSONArray("exams").getJSONObject(0).put("endTime", "08:00");
          assertThrows(IllegalArgumentException.class, () -> repo.restore(bad));
          assertEquals("保留考试", repo.store.exams(t.id).get(0).name);
        });
  }

  @Test
  public void importsCreateIndependentCoursebooksAndAppendOnlyToChosenTarget() throws Exception {
    background(
        () -> {
          repo.initialize();
          Term first = repo.store.terms().get(0);
          first.calibratedMonday = "2026-10-19";
          first.calibratedWeek = 6;
          repo.store.save(first);
          Course old = new Course();
          old.name = "原课程";
          old.termId = first.id;
          old.weeks = 1;
          repo.saveCourse(old);
          Exam exam = new Exam();
          exam.name = "原考试";
          exam.termId = first.id;
          repo.store.save(exam);
          Course added = new Course();
          added.name = "教务课程";
          added.weeks = 1;
          // Even a supplied database id must not overwrite the original course.
          added.id = old.id;
          Repository.ImportResult result =
              repo.importCourses(first.id, java.util.List.of(added, added.copy()), true, "教务课表");
          long secondId = result.termId;
          assertNotEquals(first.id, secondId);
          assertEquals(1, result.added);
          assertEquals(1, result.duplicates);
          assertEquals(secondId, repo.prefs.getLong("term", 0));
          assertEquals("原课程", repo.store.courses(first.id).get(0).name);
          assertEquals("教务课程", repo.store.courses(secondId).get(0).name);
          assertTrue(repo.store.exams(secondId).isEmpty());
          Term second = repo.store.terms().get(1);
          assertEquals(first.calibratedMonday, second.calibratedMonday);
          assertEquals(first.calibratedWeek, second.calibratedWeek);
          assertNotEquals(first.routineId, second.routineId);
          Routine copied =
              repo.store.routines().stream()
                  .filter(r -> r.id == second.routineId)
                  .findFirst()
                  .get();
          copied.slots = copied.slots.replace("08:00", "08:10");
          repo.store.save(copied);
          assertTrue(
              repo.store.routines().stream()
                  .filter(r -> r.id == first.routineId)
                  .findFirst()
                  .get()
                  .slots
                  .contains("08:00"));
          Repository.ImportResult appended =
              repo.importCourses(first.id, java.util.List.of(added), false, "");
          assertEquals(first.id, appended.termId);
          assertEquals(1, appended.added);
          assertEquals(2, repo.store.courses(first.id).size());
          assertEquals(
              1, repo.importCourses(first.id, java.util.List.of(added), false, "").duplicates);
          assertEquals(1, repo.store.courses(secondId).size());
          assertEquals(2, repo.store.terms().size());
          assertEquals(1, repo.store.exams(first.id).size());
          JSONObject backup = repo.backup();
          repo.restore(backup);
          assertEquals(2, repo.store.terms().size());
          assertEquals(2, repo.store.courses(first.id).size());
          assertEquals(1, repo.store.courses(secondId).size());
        });
  }

  @Test
  public void invalidOrEmptyImportDoesNotLeaveAnEmptyCoursebook() throws Exception {
    background(
        () -> {
          repo.initialize();
          long source = repo.store.terms().get(0).id;
          Course valid = new Course();
          valid.name = "有效课程";
          valid.weeks = 1;
          Course invalid = valid.copy();
          invalid.end = 25;
          assertThrows(
              IllegalArgumentException.class,
              () -> repo.importCourses(source, java.util.List.of(valid, invalid), true, "不应创建"));
          assertEquals(1, repo.store.terms().size());
          assertEquals(1, repo.store.routines().size());
          assertTrue(repo.store.allCourses().isEmpty());
          assertEquals(0, repo.importCourses(source, java.util.List.of(), true, "空课表").added);
          assertEquals(1, repo.store.terms().size());
        });
  }

}
