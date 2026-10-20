package com.local.weekday;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import com.local.weekday.Models.*;
import java.time.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
public class ReminderSchedulerTest {
  Context c;
  Repository repo;
  ExecutorService worker;

  @Before
  public void setup() {
    c = RuntimeEnvironment.getApplication();
    c.deleteDatabase("weekday.db");
    c.getSharedPreferences("appearance", 0).edit().clear().commit();
    ReminderScheduler.state(c).edit().clear().commit();
    repo = new Repository(c);
    worker = Executors.newSingleThreadExecutor();
  }

  @After
  public void teardown() {
    repo.close();
    worker.shutdown();
  }

  @Test
  public void nextAlarmRebuildsAfterExamEditAndCancelsWhenDisabled() throws Exception {
    worker
        .submit(
            () -> {
              repo.initialize();
              Exam e = new Exam();
              e.name = "考试";
              e.termId = repo.store.terms().get(0).id;
              e.date = LocalDate.now().plusDays(2).toString();
              e.id = repo.store.save(e);
              ReminderScheduler.update(c, repo.store, repo.prefs);
              long first =
                  Shadows.shadowOf(c.getSystemService(AlarmManager.class))
                      .getNextScheduledAlarm()
                      .triggerAtTime;
              e.date = LocalDate.now().plusDays(3).toString();
              repo.store.save(e);
              ReminderScheduler.update(c, repo.store, repo.prefs);
              assertEquals(
                  86400000L,
                  Shadows.shadowOf(c.getSystemService(AlarmManager.class))
                          .getNextScheduledAlarm()
                          .triggerAtTime
                      - first);
              e.remindMinutes = -1;
              repo.store.save(e);
              ReminderScheduler.update(c, repo.store, repo.prefs);
              assertTrue(
                  Shadows.shadowOf(c.getSystemService(AlarmManager.class))
                      .getScheduledAlarms()
                      .isEmpty());
            })
        .get(30, TimeUnit.SECONDS);
  }

  @Test
  public void staleAlarmCannotNotifyAndCurrentBatchOnlyFiresOnce() throws Exception {
    ReminderScheduler.channels(c);
    long now = System.currentTimeMillis();
    JSONArray batch =
        new JSONArray()
            .put(
                new JSONObject()
                    .put("key", "exam:1")
                    .put("title", "考试提醒")
                    .put("text", "09:00 A101")
                    .put("at", now - 1000)
                    .put("start", now + 60000)
                    .put("exam", true)
                    .put("termId", 1));
    ReminderScheduler.state(c)
        .edit()
        .putString("token", "new")
        .putString("batch", batch.toString())
        .commit();
    NotificationManager nm = c.getSystemService(NotificationManager.class);
    ReminderScheduler.receive(c, new Intent(ReminderScheduler.FIRE).putExtra("token", "old"));
    assertEquals(0, Shadows.shadowOf(nm).getAllNotifications().size());
    Intent fire = new Intent(ReminderScheduler.FIRE).putExtra("token", "new");
    ReminderScheduler.receive(c, fire);
    assertEquals(1, Shadows.shadowOf(nm).getAllNotifications().size());
    assertFalse(ReminderScheduler.state(c).contains("batch"));
    nm.cancelAll();
    ReminderScheduler.receive(c, fire);
    assertEquals(0, Shadows.shadowOf(nm).getAllNotifications().size());
  }
}
