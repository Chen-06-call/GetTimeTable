package com.local.weekday;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.local.weekday.Models.*;
import java.io.*;
import java.time.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w411dp-h891dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
public class MainActivityTest {
  @Test
  public void nativeHomeRendersAndOpensCourseDetails() throws Exception {
    RuntimeEnvironment.getApplication().deleteDatabase("weekday.db");
    ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup();
    MainActivity a = controller.get();
    await(() -> a.repo.term != null);
    CountDownLatch loaded = new CountDownLatch(1);
    a.repo.run(
        () -> {
          a.repo.term.name = "2026 秋季学期 · 示例";
          a.repo.term.startDate = CalendarEngine.monday(LocalDate.now()).minusWeeks(5).toString();
          a.repo.store.save(a.repo.term);
          int[] color = {0xffb9d9c1, 0xffc7d9ed, 0xfff3d5a8, 0xffe6c6d9, 0xffcbd2a9, 0xffd5c8e8};
          String[] names = {"高等数学", "大学英语", "Java 程序设计", "电路基础", "体育", "大学物理"};
          int[] days = {1, 2, 3, 4, 5, 2}, start = {1, 3, 5, 1, 5, 7};
          for (int i = 0; i < names.length; i++) {
            Course c = new Course();
            c.termId = a.repo.term.id;
            c.name = names[i];
            c.room = new String[] {"A101", "B203", "机房 02", "A305", "体育馆", "C102"}[i];
            c.teacher = "示例教师";
            c.day = days[i];
            c.start = start[i];
            c.end = start[i] + 1;
            c.weeks = CalendarEngine.allWeeks(20);
            c.color = color[i];
            a.repo.saveCourse(c);
          }
        },
        e -> {
          assertNull(e);
          loaded.countDown();
        });
    await(() -> loaded.getCount() == 0);
    a.selectedWeek = 6;
    a.refresh();
    View root = a.getWindow().getDecorView();
    root.measure(
        View.MeasureSpec.makeMeasureSpec(411, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(891, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, 411, 891);
    assertNotNull(find(root, "高等数学"));
    assertNotNull(find(root, "全部课程"));
    Bitmap bitmap = Bitmap.createBitmap(411, 891, Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(bitmap));
    File out = new File("../docs/previews/home-native.png");
    out.getParentFile().mkdirs();
    try (FileOutputStream stream = new FileOutputStream(out)) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
    }
    find(root, "高等数学").performClick();
    assertTrue(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().isShowing());
    org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().dismiss();
    controller.pause().stop().destroy();
  }

  @Test
  public void weekTransitionDetachesFinalPageBeforeReattaching() throws Exception {
    RuntimeEnvironment.getApplication().deleteDatabase("weekday.db");
    ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup();
    MainActivity activity = controller.get();
    try {
      await(() -> activity.repo.term != null && activity.selectedWeek >= 1);
      activity.selectedWeek = 1;
      activity.showHome();
      View current = activity.activeHomeRoot;
      ZoomableScheduleLayout currentZoom = activity.activeHomeBodyZoom;
      activity.selectedWeek = 2;
      activity.showHome();
      View next = activity.activeHomeRoot;
      activity.mountWeekTransition(current, next, currentZoom, 1, 1);
      activity.completeWeekTransition(true);
      assertSame(next, activity.activeHomeRoot);
      assertSame(next, activity.content.getChildAt(0));
      assertSame(activity.content, next.getParent());
    } finally {
      controller.pause().stop().destroy();
    }
  }

  @Test
  public void schoolImportOnlyAsksForDestinationAndHomeDropdownSwitchesCoursebooks()
      throws Exception {
    android.app.Application context = RuntimeEnvironment.getApplication();
    context.deleteDatabase("weekday.db");
    context.getSharedPreferences("appearance", 0).edit().clear().commit();
    ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup();
    MainActivity activity = controller.get();
    try {
      await(() -> activity.repo.term != null);
      long originalId = activity.repo.term.id;
      CountDownLatch seeded = new CountDownLatch(1);
      activity.repo.run(
          () -> {
            Course old = new Course();
            old.termId = originalId;
            old.name = "原有课程";
            old.weeks = 1;
            old.day = 3;
            activity.repo.saveCourse(old);
            Exam exam = new Exam();
            exam.termId = originalId;
            exam.name = "原有考试";
            activity.repo.store.save(exam);
          },
          error -> {
            assertNull(error);
            seeded.countDown();
          });
      await(() -> seeded.getCount() == 0);
      JSONObject payload =
          new JSONObject()
              .put("url", SchoolImporter.LOGIN_URL)
              .put("term", "2026-2027 第一学期")
              .put(
                  "entries",
                  new JSONArray()
                      .put(
                          new JSONObject()
                              .put("name", "导入课程")
                              .put("day", "1")
                              .put("sections", "1-2")
                              .put("weeks", "1-16周")
                              .put("room", "A101")));
      java.nio.file.Files.writeString(
          new File(activity.getCacheDir(), "school-import.json").toPath(), payload.toString());
      Intent result = new Intent().putExtra("termId", originalId);
      activity.onActivityResult(207, Activity.RESULT_OK, result);
      await(
          () ->
              ShadowAlertDialog.getLatestAlertDialog() != null
                  && ShadowAlertDialog.getLatestAlertDialog().isShowing());
      AlertDialog choice = ShadowAlertDialog.getLatestAlertDialog();
      assertEquals("导入到哪个课表？", Shadows.shadowOf(choice).getTitle().toString());
      assertEquals("新建课表", choice.getButton(AlertDialog.BUTTON_NEUTRAL).getText().toString());
      assertEquals("当前课表", choice.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
      choice.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
      assertEquals(1, activity.repo.terms.size());
      assertEquals("原有课程", activity.repo.courses.get(0).name);
      activity.onActivityResult(207, Activity.RESULT_OK, result);
      await(() -> ShadowAlertDialog.getLatestAlertDialog().isShowing());
      ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
      await(() -> "新建课表".equals(Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle().toString()));
      ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      await(() -> activity.repo.term.id != originalId && activity.repo.courses.size() == 1
          && findDescription(activity.getWindow().getDecorView(), "切换课表") != null
          && ((Spinner) findDescription(activity.getWindow().getDecorView(), "切换课表")).getCount() == 2);
      assertEquals("导入课程", activity.repo.courses.get(0).name);
      assertTrue(activity.repo.exams.isEmpty());
      long secondId = activity.repo.term.id;
      assertEquals(secondId, activity.repo.prefs.getLong("term", 0));
      assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing());
      Spinner selector = (Spinner) findDescription(activity.getWindow().getDecorView(), "切换课表");
      assertNotNull(selector);
      assertEquals(2, selector.getCount());
      selector.setSelection(0);
      await(() -> activity.repo.term.id == originalId && activity.repo.courses.size() == 1);
      assertEquals("原有课程", activity.repo.courses.get(0).name);
      assertEquals(1, activity.repo.exams.size());
      activity.onActivityResult(207, Activity.RESULT_OK, result);
      await(() -> ShadowAlertDialog.getLatestAlertDialog().isShowing());
      ShadowAlertDialog.getLatestAlertDialog()
          .getButton(AlertDialog.BUTTON_POSITIVE)
          .performClick();
      await(() -> activity.repo.courses.size() == 2);
      assertEquals(originalId, activity.repo.term.id);
      assertEquals(2, activity.repo.terms.size());
      assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing());
    } finally {
      controller.pause().stop().destroy();
    }
  }

  @Test
  public void invalidSchoolResultShowsReasonAndPreservesCurrentCoursebook() throws Exception {
    RuntimeEnvironment.getApplication().deleteDatabase("weekday.db");
    ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup();
    MainActivity activity = controller.get();
    try {
      await(() -> activity.repo.term != null);
      long originalId = activity.repo.term.id;
      JSONObject payload = new JSONObject()
          .put("url", SchoolImporter.LOGIN_URL)
          .put("entries", new JSONArray().put(new JSONObject().put("name", "测试课程")));
      java.nio.file.Files.writeString(
          new File(activity.getCacheDir(), "school-import.json").toPath(), payload.toString());
      activity.onActivityResult(207, Activity.RESULT_OK, new Intent().putExtra("termId", originalId));
      await(() -> ShadowAlertDialog.getLatestAlertDialog() != null
          && ShadowAlertDialog.getLatestAlertDialog().isShowing());
      AlertDialog error = ShadowAlertDialog.getLatestAlertDialog();
      assertEquals("教务课表读取失败", Shadows.shadowOf(error).getTitle().toString());
      assertTrue(Shadows.shadowOf(error).getMessage().toString().contains("没有识别到"));
      assertEquals(originalId, activity.repo.term.id);
      assertEquals(1, activity.repo.terms.size());
      assertTrue(activity.repo.courses.isEmpty());
      error.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    } finally {
      controller.pause().stop().destroy();
    }
  }

  @Test
  public void translationNetworkSettingsOpenAppDetailsAndOfflineErrorsCannotBeCopiedOrSwapped()
      throws Exception {
    RuntimeEnvironment.getApplication().deleteDatabase("weekday.db");
    ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
    MainActivity activity = controller.get();
    try {
      // repo.term is written on the background thread before onCreate's callback is posted, so it
      // is not enough on its own: the queued callback would later reset page and re-render home,
      // replacing the translator. selectedWeek is only set by that callback, so wait for it too.
      await(() -> activity.repo.term != null && activity.selectedWeek >= 1);
      activity.page = 5;
      activity.refresh();
      View root = activity.getWindow().getDecorView();
      find(root, "检查网络权限与连接").performClick();
      AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
      dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      Intent settings = Shadows.shadowOf(activity).getNextStartedActivity();
      assertNotNull(settings);
      assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settings.getAction());
      assertEquals("package:" + activity.getPackageName(), settings.getDataString());
      android.net.ConnectivityManager connectivity = activity.getSystemService(android.net.ConnectivityManager.class);
      Shadows.shadowOf(connectivity).setActiveNetworkInfo(null);
      assertFalse(activity.hasNetworkConnection());
      EditText input = (EditText) findDescription(root, "翻译输入");
      input.setText("hello");
      TextView output = (TextView) findDescription(root, "翻译结果");
      Button translate = findButton(root, "翻译");
      translate.performClick();
      assertTrue(output.getText().toString().contains("没有可用网络"));
      android.content.ClipboardManager clipboard = activity.getSystemService(android.content.ClipboardManager.class);
      clipboard.setPrimaryClip(ClipData.newPlainText("existing", "keep"));
      findButton(root, "复制结果").performClick();
      assertEquals("keep", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
      findButton(root, "⇄ 交换方向").performClick();
      assertEquals("hello", input.getText().toString());
    } finally {
      controller.pause().stop().destroy();
    }
  }

  Button findButton(View view, String label) {
    if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        Button result = findButton(group.getChildAt(i), label);
        if (result != null) return result;
      }
    }
    return null;
  }

  View findDescription(View root, String description) {
    if (description.contentEquals(
        root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = findDescription(group.getChildAt(i), description);
        if (found != null) return found;
      }
    }
    return null;
  }

  interface Condition {
    boolean ready();
  }

  void await(Condition condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (System.nanoTime() < deadline) {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      if (condition.ready()) return;
      Thread.sleep(20);
    }
    fail("background load timed out");
  }

  View find(View root, String prefix) {
    if (root instanceof TextView && ((TextView) root).getText().toString().startsWith(prefix))
      return root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = find(group.getChildAt(i), prefix);
        if (found != null) return found;
      }
    }
    return null;
  }
}
