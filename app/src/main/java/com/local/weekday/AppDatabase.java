package com.local.weekday;

import android.content.Context;
import androidx.room.*;
import com.local.weekday.Models.*;
import java.util.List;

@Database(
    entities = {
      Term.class,
      Course.class,
      Adjustment.class,
      Routine.class,
      School.class,
      Exam.class
    },
    version = 2,
    exportSchema = false)
public abstract class AppDatabase extends RoomDatabase implements AutoCloseable {
  @Dao
  public interface Store {
    @Query("SELECT * FROM exams WHERE termId=:term ORDER BY date,startTime,id")
    List<Exam> exams(long term);

    @Query("SELECT * FROM exams")
    List<Exam> allExams();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long save(Exam e);

    @Delete
    void delete(Exam e);

    @Query("DELETE FROM exams")
    void clearExams();

    @Query("SELECT * FROM terms ORDER BY id")
    List<Term> terms();

    @Query("SELECT * FROM courses WHERE termId=:term ORDER BY day,start,id")
    List<Course> courses(long term);

    @Query("SELECT * FROM courses")
    List<Course> allCourses();

    @Query("SELECT * FROM adjustments")
    List<Adjustment> adjustments();

    @Query("SELECT * FROM routines ORDER BY id")
    List<Routine> routines();

    @Query("SELECT * FROM schools ORDER BY name")
    List<School> schools();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long save(Term t);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long save(Course c);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long save(Adjustment a);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long save(Routine r);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long save(School s);

    @Delete
    void delete(Course c);

    @Delete
    void delete(School s);

    @Delete
    void delete(Adjustment a);

    @Query("DELETE FROM adjustments WHERE courseId=:id")
    void deleteAdjustments(long id);

    @Query("DELETE FROM adjustments WHERE courseId IN (SELECT id FROM courses WHERE termId=:term)")
    void clearAdjustments(long term);

    @Query("DELETE FROM courses WHERE termId=:term")
    void clearCourses(long term);

    @Query("DELETE FROM terms")
    void clearTerms();

    @Query("DELETE FROM courses")
    void clearAllCourses();

    @Query("DELETE FROM adjustments")
    void clearAllAdjustments();

    @Query("DELETE FROM routines")
    void clearRoutines();

    @Query("DELETE FROM schools")
    void clearSchools();
  }

  public abstract Store store();

  public static AppDatabase open(Context c) {
    return Room.databaseBuilder(c.getApplicationContext(), AppDatabase.class, "weekday.db")
        .addMigrations(MIGRATION_1_2)
        .build();
  }

  public static final androidx.room.migration.Migration MIGRATION_1_2 =
      new androidx.room.migration.Migration(1, 2) {
        @Override
        public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS exams (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                  + " termId INTEGER NOT NULL, name TEXT, date TEXT, startTime TEXT, endTime TEXT,"
                  + " room TEXT, note TEXT, remindMinutes INTEGER NOT NULL)");
          db.execSQL("CREATE INDEX IF NOT EXISTS index_exams_termId ON exams (termId)");
        }
      };
}
