package com.local.weekday;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** A single rolling alarm, rebuilt after edits, restore, reboot and clock changes. */
public final class ReminderScheduler {
  static final String FIRE = "com.local.weekday.REMIND", CHANNEL = "schedule";
  static final ExecutorService WORK = Executors.newSingleThreadExecutor();
  private static final Object LOCK = new Object();

  static SharedPreferences state(Context c) {
    return c.getSharedPreferences("reminder-runtime", Context.MODE_PRIVATE);
  }

  static PendingIntent alarm(Context c, String token) {
    return PendingIntent.getBroadcast(
        c,
        41,
        new Intent(c, ReminderReceiver.class).setAction(FIRE).putExtra("token", token),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  public static boolean canNotify(Context c) {
    return (Build.VERSION.SDK_INT < 33
            || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED)
        && c.getSystemService(NotificationManager.class).areNotificationsEnabled();
  }

  public static void channels(Context c) {
    c.getSystemService(NotificationManager.class)
        .createNotificationChannel(
            new NotificationChannel(CHANNEL, "课程与考试提醒", NotificationManager.IMPORTANCE_HIGH));
  }

  public static void update(Context c, AppDatabase.Store store, SharedPreferences prefs) {
    synchronized (LOCK) {
      channels(c);
      AlarmManager manager = c.getSystemService(AlarmManager.class);
      manager.cancel(alarm(c, ""));
      String token = UUID.randomUUID().toString();
      state(c).edit().putString("token", token).remove("batch").remove("error").apply();
      if (!canNotify(c)) return;
      List<ReminderPlanner.Event> events =
          ReminderPlanner.upcoming(
              store.terms(),
              store.routines(),
              store.allCourses(),
              store.adjustments(),
              store.allExams(),
              prefs.getBoolean("courseReminders", false),
              prefs.getInt("courseLead", 10),
              Instant.now(),
              ZoneId.systemDefault());
      if (events.isEmpty()) return;
      try {
        long trigger = events.get(0).at;
        JSONArray batch = new JSONArray();
        for (ReminderPlanner.Event e : events) {
          if (e.at != trigger) break;
          batch.put(
              new JSONObject()
                  .put("key", e.key)
                  .put("title", e.title)
                  .put("text", e.text)
                  .put("at", e.at)
                  .put("start", e.start)
                  .put("exam", e.exam)
                  .put("termId", e.termId));
        }
        state(c).edit().putString("batch", batch.toString()).apply();
        PendingIntent intent = alarm(c, token);
        if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms())
          manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent);
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent);
      } catch (Exception e) {
        state(c).edit().putString("error", "提醒安排失败，请重新打开提醒设置").apply();
      }
    }
  }

  public static void reschedule(Context context) {
    Context c = context.getApplicationContext();
    WORK.execute(
        () -> {
          try (AppDatabase db = AppDatabase.open(c)) {
            update(c, db.store(), c.getSharedPreferences("appearance", Context.MODE_PRIVATE));
          } catch (Exception e) {
            state(c).edit().putString("error", "提醒安排失败，请打开 App 检查").apply();
          }
        });
  }

  static void receive(Context c, Intent intent) {
    synchronized (LOCK) {
      if (FIRE.equals(intent.getAction())
          && Objects.equals(intent.getStringExtra("token"), state(c).getString("token", null))) {
        try {
          JSONArray batch = new JSONArray(state(c).getString("batch", "[]"));
          long now = System.currentTimeMillis();
          if (canNotify(c))
            for (int i = 0; i < batch.length(); i++) {
              JSONObject e = batch.getJSONObject(i);
              if (now < e.getLong("at") - 5000 || now > e.getLong("start") + 15 * 60000L) continue;
              Intent open =
                  new Intent(c, MainActivity.class)
                      .setData(Uri.parse("weekday://" + e.getString("key")))
                      .putExtra("showExams", e.getBoolean("exam"))
                      .putExtra("termId", e.getLong("termId"))
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
              PendingIntent pi =
                  PendingIntent.getActivity(
                      c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
              Notification n =
                  new Notification.Builder(c, CHANNEL)
                      .setSmallIcon(R.drawable.ic_notification)
                      .setContentTitle(e.getString("title"))
                      .setContentText(e.getString("text"))
                      .setStyle(new Notification.BigTextStyle().bigText(e.getString("text")))
                      .setContentIntent(pi)
                      .setAutoCancel(true)
                      .setCategory(Notification.CATEGORY_REMINDER)
                      .build();
              c.getSystemService(NotificationManager.class).notify(e.getString("key"), 1, n);
            }
          state(c).edit().remove("batch").putString("token", UUID.randomUUID().toString()).apply();
        } catch (Exception ignored) {
          state(c).edit().putString("error", "提醒内容读取失败，请重新打开 App").apply();
        }
      }
    }
  }

  private ReminderScheduler() {}
}
