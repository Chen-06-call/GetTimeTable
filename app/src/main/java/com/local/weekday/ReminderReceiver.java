package com.local.weekday;

import android.content.*;

public class ReminderReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (ReminderScheduler.FIRE.equals(intent.getAction())
        && !java.util.Objects.equals(
            intent.getStringExtra("token"),
            ReminderScheduler.state(context).getString("token", null))) return;
    PendingResult pending = goAsync();
    Context c = context.getApplicationContext();
    ReminderScheduler.WORK.execute(
        () -> {
          try {
            ReminderScheduler.receive(c, intent);
            try (AppDatabase db = AppDatabase.open(c)) {
              ReminderScheduler.update(
                  c, db.store(), c.getSharedPreferences("appearance", Context.MODE_PRIVATE));
            }
          } catch (Exception e) {
            ReminderScheduler.state(c).edit().putString("error", "提醒更新失败，请重新打开 App").apply();
          } finally {
            pending.finish();
          }
        });
  }
}
