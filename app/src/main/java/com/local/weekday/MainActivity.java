package com.local.weekday;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.local.weekday.Models.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.LocalTime;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
  static final int COURSE_SCALE_MIN = 100;
  static final int COURSE_SCALE_MAX = 200;
  static final int COMPACT_MODE_SCALE = 100;
  static final int FULL_MODE_SCALE = 135;
  static final int WEEK_ANIMATION_SLIDE = 0;
  static final int WEEK_ANIMATION_CYLINDER = 1;
  static final int WEEK_ANIMATION_FADE = 2;

  Repository repo;
  FrameLayout content;
  LinearLayout navigation;
  BackdropView backdrop;
  int selectedWeek;
  int page = 0;
  boolean allMode = false;
  LocalDate lastViewedDate = LocalDate.now();
  int[] colors = {
    0xffb9d9c1, 0xffc7d9ed, 0xfff3d5a8, 0xffe6c6d9,
    0xffcbd2a9, 0xffd5c8e8, 0xfff1c3a7, 0xffb7d9d7,
    0xffe8d6b3, 0xffd9c4d9, 0xffc6d6b8, 0xffbfcbe7
  };
  int green = 0xff386c56, ink = 0xff233d32, muted = 0xff6c7e72;
  // Resolved in applyPalette() so every page shares one light/dark palette.
  int surface = 0xeefefffb, softFill = 0x40ffffff, divider = 0x1a3c5b49;
  int accentSoft = 0xffe1eddf, onAccent = 0xffffffff;
  View activeHomeRoot;
  ZoomableScheduleLayout activeHomeBodyZoom;
  ZoomableScheduleLayout lastBuiltHomeBodyZoom;
  float weekSwipeDownX, weekSwipeDownY;
  float weekSwipeFilteredDx;
  boolean weekSwipeHorizontal, weekSwipeCommitted;
  android.view.VelocityTracker weekSwipeVelocity;
  FrameLayout weekTransitionHost;
  View weekTransitionCurrent, weekTransitionNext;
  ZoomableScheduleLayout weekTransitionCurrentZoom;
  int weekTransitionBaseWeek, weekTransitionDirection;
  boolean updateCheckInFlight, updateCheckedThisSession;
  boolean updateDownloadCancelled;
  AlertDialog updateProgressDialog;

  @Override
  public void onCreate(Bundle b) {
    SharedPreferences appearance = getSharedPreferences("appearance", MODE_PRIVATE);
    setTheme(
        resolveDarkTheme(appearance)
            ? R.style.AppThemeDark
            : R.style.AppTheme);
    super.onCreate(b);
    setContentView(R.layout.activity_main);
    content = findViewById(R.id.content);
    navigation = findViewById(R.id.navigation);
    backdrop = findViewById(R.id.backdrop);
    View decor = findViewById(android.R.id.content);
    decor.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets;
        });
    repo = new Repository(this);
    applyPalette();
    findViewById(R.id.add)
        .setOnClickListener(
            v -> {
              if (repo.term != null) {
                if (page == 3) editExam(null);
                else if (page == 4) schoolsDialog();
                else if (page == 5) return;
                else editCourse(null, false);
              }
            });
    renderNav();
    repo.run(
        () -> {
          repo.initialize();
          long target = getIntent().getLongExtra("termId", 0);
          if (target > 0) repo.prefs.edit().putLong("term", target).apply();
        },
        e -> {
          if (isDestroyed() || isFinishing()) return;
          if (e != null) toast(e);
          else {
            selectedWeek =
                b == null
                    ? Math.max(
                        1,
                        Math.min(
                            repo.term.weeks,
                            CalendarEngine.currentWeek(repo.term, LocalDate.now())))
                    : b.getInt("week", 1);
            page =
                b == null
                    ? (getIntent().getBooleanExtra("showExams", false) ? 3 : 0)
                    : b.getInt("page", 0);
            allMode = repo.prefs.getBoolean("allMode", false);
            refresh();
            checkForUpdates(false);
          }
        });
  }

  boolean resolveDarkTheme(SharedPreferences prefs) {
    if (prefs.contains("themeMode")) {
      int mode = prefs.getInt("themeMode", 0);
      if (mode == 2) return true;
      if (mode == 1) return false;
      return systemDark();
    }
    return prefs.getBoolean("dark", false);
  }

  TextView tv(String text, float size, int color) {
    TextView v = new TextView(this);
    v.setText(text);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  Button button(String text) {
    Button b = new Button(this);
    b.setText(text);
    b.setTextSize(12);
    b.setTextColor(ink);
    b.setAllCaps(false);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setPadding(dp(4), 0, dp(4), 0);
    b.setMinHeight(dp(36));
    return b;
  }

  void renderNav() {
    navigation.removeAllViews();
    String[] names = {"课表", "课程", "考试", "教务", "翻译", "设置"};
    int[] pages = {0, 1, 3, 4, 5, 2};
    int[] glyphs = {
      R.drawable.ic_tab_schedule,
      R.drawable.ic_tab_courses,
      R.drawable.ic_tab_exam,
      R.drawable.ic_tab_school,
      R.drawable.ic_tab_translate,
      R.drawable.ic_tab_settings
    };
    for (int i = 0; i < names.length; i++) {
      final int p = pages[i];
      boolean active = p == page;
      int tint = active ? green : muted;
      LinearLayout item = new LinearLayout(this);
      item.setOrientation(LinearLayout.VERTICAL);
      item.setGravity(Gravity.CENTER);
      FrameLayout pill = new FrameLayout(this);
      if (active) pill.setBackground(rounded(accentSoft, dp(13)));
      FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(dp(21), dp(21));
      gp.gravity = Gravity.CENTER;
      pill.addView(icon(glyphs[i], tint, 21), gp);
      TextView label = tv(names[i], 10.5f, tint);
      label.setGravity(Gravity.CENTER);
      label.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
      item.addView(pill, new LinearLayout.LayoutParams(dp(38), dp(26)));
      item.addView(label, lp(-1, 20));
      item.setBackground(ripple(rounded(Color.TRANSPARENT, dp(14)), green, dp(14)));
      item.setContentDescription(names[i]);
      item.setOnClickListener(
          v -> {
            if (repo.term == null) return;
            page = p;
            if (p == 0) showHome();
            else if (p == 1) showCourses();
            else if (p == 3) showExams();
            else if (p == 4) showSchoolPage();
            else if (p == 5) showTranslator();
            else showSettings();
            renderNav();
          });
      navigation.addView(item, lp(0, -1, 1));
    }
  }

  void showHome() {
    page = 0;
    cancelWeekTransition();
    content.removeAllViews();
    if (repo.term == null) return;
    LinearLayout root = buildHomeRoot();
    activeHomeRoot = root;
    activeHomeBodyZoom = lastBuiltHomeBodyZoom;
    content.addView(root);
  }

  void cancelWeekTransition() {
    if (weekTransitionCurrent != null) weekTransitionCurrent.animate().cancel();
    if (weekTransitionNext != null) weekTransitionNext.animate().cancel();
    if (weekTransitionHost != null) weekTransitionHost.animate().cancel();
    weekTransitionHost = null;
    weekTransitionCurrent = null;
    weekTransitionNext = null;
    weekTransitionCurrentZoom = null;
  }

  LinearLayout buildHomeRoot() {
    LinearLayout root = vertical();
    root.setPadding(dp(16), 0, dp(16), dp(4));
    LinearLayout options = vertical();
    options.setVisibility(View.GONE);
    String[] coursebooks = new String[repo.terms.size()];
    int selectedCoursebook = 0;
    for (int i = 0; i < repo.terms.size(); i++) {
      coursebooks[i] = repo.terms.get(i).name;
      if (repo.terms.get(i).id == repo.term.id) selectedCoursebook = i;
    }
    Spinner coursebook = dropdown(options, "切换课表", coursebooks, selectedCoursebook);
    coursebook.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (position < repo.terms.size() && repo.terms.get(position).id != repo.term.id) {
              long target = repo.terms.get(position).id;
              work(
                  () -> repo.prefs.edit().putLong("term", target).commit(),
                  () -> {
                    selectedWeek =
                        Math.max(
                            1,
                            Math.min(
                                repo.term.weeks,
                                CalendarEngine.currentWeek(repo.term, LocalDate.now())));
                  });
            }
          }

          public void onNothingSelected(AdapterView<?> parent) {}
        });
    LinearLayout weekbar = new LinearLayout(this);
    weekbar.setGravity(Gravity.CENTER_VERTICAL);
    ImageView prev = icon(R.drawable.ic_chevron, muted, 18);
    ImageView next = icon(R.drawable.ic_chevron, muted, 18);
    prev.setRotation(180);
    prev.setPadding(dp(9), dp(9), dp(9), dp(9));
    next.setPadding(dp(9), dp(9), dp(9), dp(9));
    prev.setBackground(ripple(rounded(softFill, dp(20)), green, dp(20)));
    next.setBackground(ripple(rounded(softFill, dp(20)), green, dp(20)));
    Button today = button("本周");
    today.setTextColor(green);
    today.setTextSize(12.5f);
    today.setBackground(ripple(rounded(accentSoft, dp(11)), green, dp(11)));
    prev.setContentDescription("上一周");
    next.setContentDescription("下一周");
    prev.setOnClickListener(
        v -> {
          if (weekTransitionHost == null && selectedWeek > 1) {
            selectedWeek--;
            showHomeAnimated(-1);
          }
        });
    next.setOnClickListener(
        v -> {
          if (weekTransitionHost == null && selectedWeek < repo.term.weeks) {
            selectedWeek++;
            showHomeAnimated(1);
          }
        });
    today.setOnClickListener(
        v -> {
          int previous = selectedWeek;
          selectedWeek =
              Math.max(
                  1,
                  Math.min(
                      repo.term.weeks, CalendarEngine.currentWeek(repo.term, LocalDate.now())));
          showHomeAnimated(Integer.compare(selectedWeek, previous));
        });
    weekbar.addView(prev, new LinearLayout.LayoutParams(dp(36), dp(36)));
    TextView range = tv(weekTitle(), 15, ink);
    range.setGravity(Gravity.CENTER);
    range.setTypeface(null, Typeface.BOLD);
    range.setBackground(ripple(rounded(Color.TRANSPARENT, dp(11)), green, dp(11)));
    range.setOnClickListener(
        v -> {
          String[] weeks = new String[repo.term.weeks];
          for (int i = 0; i < weeks.length; i++) weeks[i] = "第 " + (i + 1) + " 周";
          new AlertDialog.Builder(this)
              .setTitle("选择周次")
              .setSingleChoiceItems(
                  weeks,
                  selectedWeek - 1,
                  (d, n) -> {
                    int previous = selectedWeek;
                    selectedWeek = n + 1;
                    d.dismiss();
                    showHomeAnimated(Integer.compare(selectedWeek, previous));
                  })
              .setNegativeButton("关闭", null)
              .show();
        });
    weekbar.addView(range, new LinearLayout.LayoutParams(0, dp(36), 1));
    weekbar.addView(next, new LinearLayout.LayoutParams(dp(36), dp(36)));
    weekbar.addView(today, new LinearLayout.LayoutParams(dp(54), dp(32)));
    root.addView(weekbar);
    ImageView more = findViewById(R.id.home_more);
    more.setVisibility(View.VISIBLE);
    more.setOnClickListener(
        v -> {
          if (options.getVisibility() == View.VISIBLE) {
            options.animate().alpha(0f).setDuration(120).withEndAction(() -> options.setVisibility(View.GONE)).start();
          } else {
            options.setAlpha(0f);
            options.setVisibility(View.VISIBLE);
            options.animate().alpha(1f).setDuration(160).start();
          }
        });
    TextView hint = tv(weekHint(), 11, muted);
    hint.setPadding(dp(5), dp(2), dp(5), dp(5));
    options.addView(hint);
    // Keep course scope separate from the visual density mode.
    LinearLayout scopeModes = new LinearLayout(this);
    scopeModes.setBackground(rounded(softFill, dp(12)));
    scopeModes.setPadding(dp(2), dp(2), dp(2), dp(2));
    for (int i = 0; i < 2; i++) {
      final boolean all = i == 1;
      boolean active = allMode == all;
      TextView scope = tv(i == 0 ? "所选周课程" : "全部课程", 12, active ? onAccent : muted);
      scope.setGravity(Gravity.CENTER);
      scope.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
      scope.setBackground(
          active
              ? rounded(green, dp(10))
              : ripple(rounded(Color.TRANSPARENT, dp(10)), green, dp(10)));
      scopeModes.addView(scope, new LinearLayout.LayoutParams(0, dp(28), 1));
      scope.setOnClickListener(
          v -> {
            allMode = all;
            repo.prefs.edit().putBoolean("allMode", all).apply();
            showHome();
          });
    }
    options.addView(scopeModes, lp(-1, -2));
    root.addView(options);
    LinearLayout fullGrid = buildGrid();
    View fixedHeader = fullGrid.getChildAt(0);
    View scheduleBody = fullGrid.getChildAt(1);
    fullGrid.removeAllViews();
    ZoomableScheduleLayout headerZoom = new ZoomableScheduleLayout(this);
    headerZoom.setContent(fixedHeader);
    ZoomableScheduleLayout bodyZoom = new ZoomableScheduleLayout(this);
    lastBuiltHomeBodyZoom = bodyZoom;
    bodyZoom.setContent(scheduleBody);
    bodyZoom.setOnZoomChangedListener(headerZoom::setZoom);
    LinearLayout scheduleContent = vertical();
    scheduleContent.addView(headerZoom, new LinearLayout.LayoutParams(-2, -2));
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(false);
    scroll.setClipToPadding(false);
    scroll.addView(bodyZoom, new ScrollView.LayoutParams(-2, -2));
    scheduleContent.addView(scroll, new LinearLayout.LayoutParams(-2, 0, 1));
    HorizontalScrollView horizontalScroll = new HorizontalScrollView(this);
    horizontalScroll.setFillViewport(false);
    horizontalScroll.setClipToPadding(false);
    horizontalScroll.addView(scheduleContent, new HorizontalScrollView.LayoutParams(-2, -1));
    horizontalScroll.setOnTouchListener(this::handleWeekSwipeTouch);
    scroll.setOnTouchListener(this::handleWeekSwipeTouch);
    bodyZoom.setOnTouchListener(this::handleWeekSwipeTouch);
    root.addView(horizontalScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (repo.courses.isEmpty()) {
      TextView empty = tv("从一门课开始，安排好每一周。  ＋ 添加课程", 12, green);
      empty.setGravity(Gravity.CENTER);
      empty.setPadding(dp(10), dp(11), dp(10), dp(11));
      empty.setBackground(ripple(rounded(accentSoft, dp(12)), green, dp(12)));
      empty.setOnClickListener(v -> editCourse(null, false));
      LinearLayout.LayoutParams emptyParams = lp(-1, -2);
      emptyParams.topMargin = dp(8);
      root.addView(empty, emptyParams);
    }
    return root;
  }

  boolean handleWeekSwipeTouch(View source, MotionEvent event) {
    if (activeHomeBodyZoom != null && activeHomeBodyZoom.getZoom() > 1.001f) return false;
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      if (weekSwipeVelocity != null) weekSwipeVelocity.recycle();
      weekSwipeVelocity = android.view.VelocityTracker.obtain();
      weekSwipeVelocity.addMovement(event);
      weekSwipeDownX = event.getX();
      weekSwipeDownY = event.getY();
      weekSwipeFilteredDx = 0f;
      weekSwipeHorizontal = false;
      weekSwipeCommitted = false;
      return false;
    }
    if (weekSwipeVelocity != null) weekSwipeVelocity.addMovement(event);
    float dx = event.getX() - weekSwipeDownX;
    float dy = event.getY() - weekSwipeDownY;
    if (action == MotionEvent.ACTION_MOVE
        && !weekSwipeHorizontal
        && Math.abs(dx) > dp(10)
        && Math.abs(dx) > Math.abs(dy) * 1.2f) {
      weekSwipeHorizontal = true;
      source.getParent().requestDisallowInterceptTouchEvent(true);
      int direction = dx < 0 ? 1 : -1;
      if (!prepareWeekTransition(direction)) {
        weekSwipeHorizontal = false;
      } else {
        // Do not make the first move wait for the low-pass filter to warm up.
        weekSwipeFilteredDx = dx;
        updateWeekTransition(weekSwipeFilteredDx);
      }
    }
    if (!weekSwipeHorizontal) return false;
    if (action == MotionEvent.ACTION_MOVE) {
      updateWeekTransition(smoothWeekSwipe(dx));
      return true;
    }
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      float stableDx = smoothWeekSwipe(dx);
      int threshold = weekSwipeThreshold();
      float signedDistance = weekTransitionDirection == 1 ? -stableDx : stableDx;
      boolean switchWeek = weekTransitionHost != null && signedDistance >= threshold / 2f;
      if (switchWeek && !weekSwipeCommitted && weekTransitionHost != null) {
        weekSwipeCommitted = true;
        selectedWeek = weekTransitionBaseWeek + weekTransitionDirection;
        finishWeekTransition(true, 180);
      } else if (weekTransitionHost != null) finishWeekTransition(false, 160);
      weekSwipeHorizontal = false;
      source.getParent().requestDisallowInterceptTouchEvent(false);
      if (weekSwipeVelocity != null) {
        weekSwipeVelocity.recycle();
        weekSwipeVelocity = null;
      }
      return true;
    }
    return true;
  }

  boolean prepareWeekTransition(int direction) {
    if (repo.term == null || direction == 0) return false;
    int nextWeek = selectedWeek + direction;
    if (nextWeek < 1 || nextWeek > repo.term.weeks) return false;
    if (weekTransitionHost != null) return true;
    View current = activeHomeRoot;
    if (current == null) return false;
    ZoomableScheduleLayout currentZoom = activeHomeBodyZoom;
    int baseWeek = selectedWeek;
    selectedWeek = nextWeek;
    View next = buildHomeRoot();
    selectedWeek = baseWeek;
    activeHomeBodyZoom = currentZoom;
    if (next == null) return false;
    mountWeekTransition(current, next, currentZoom, baseWeek, direction);
    return true;
  }

  void mountWeekTransition(
      View current, View next, ZoomableScheduleLayout currentZoom, int baseWeek, int direction) {
    FrameLayout host = new FrameLayout(this);
    host.setClipChildren(false);
    host.setClipToPadding(false);
    content.removeAllViews();
    content.addView(host, new FrameLayout.LayoutParams(-1, -1));
    host.addView(current, new FrameLayout.LayoutParams(-1, -1));
    host.addView(next, new FrameLayout.LayoutParams(-1, -1));
    weekTransitionHost = host;
    weekTransitionCurrent = current;
    weekTransitionNext = next;
    weekTransitionCurrentZoom = currentZoom;
    weekTransitionBaseWeek = baseWeek;
    weekTransitionDirection = direction;
    activeHomeRoot = host;
    applyWeekTransition(0f);
  }

  int weekTransitionWidth() {
    int width = weekTransitionHost == null ? 0 : weekTransitionHost.getWidth();
    return Math.max(dp(1), width > 0 ? width : getResources().getDisplayMetrics().widthPixels);
  }

  void updateWeekTransition(float dx) {
    if (weekTransitionHost == null) return;
    float progress = weekSwipeProgress(dx);
    applyWeekTransition(progress, dx);
  }

  /** Removes touch-sample noise without making the page feel detached from the finger. */
  float smoothWeekSwipe(float rawDx) {
    float delta = rawDx - weekSwipeFilteredDx;
    float deadband = dp(2);
    if (Math.abs(delta) <= deadband) return weekSwipeFilteredDx;
    float response = Math.min(.65f, Math.max(.35f, Math.abs(delta) / Math.max(1f, dp(48))));
    weekSwipeFilteredDx += delta * response;
    return weekSwipeFilteredDx;
  }

  int weekSwipeThreshold() {
    return Math.max(dp(40), Math.min(dp(120), weekTransitionWidth() / 3));
  }

  float weekSwipeProgress(float dx) {
    if (weekTransitionHost == null) return 0f;
    float signedDistance = weekTransitionDirection == 1 ? -dx : dx;
    float progress = signedDistance / (float) weekSwipeThreshold();
    if (weekSwipeVelocity != null) {
      weekSwipeVelocity.computeCurrentVelocity(1000);
      float towardVelocity =
          weekTransitionDirection == 1
              ? -weekSwipeVelocity.getXVelocity()
              : weekSwipeVelocity.getXVelocity();
      // A fast deliberate swipe advances the visual transition slightly farther,
      // while reverse motion still reduces the distance-based progress.
      if (towardVelocity > 0f)
        progress += Math.min(.18f, towardVelocity / (float) dp(5000));
    }
    return Math.max(0f, Math.min(1f, progress));
  }

  void applyWeekTransition(float progress) {
    applyWeekTransition(progress, 0f);
  }

  void applyWeekTransition(float progress, float dx) {
    if (weekTransitionHost == null) return;
    int width = weekTransitionWidth();
    float offset = dx == 0f ? 0f : dx;
    int animation = weekAnimation();
    if (animation == WEEK_ANIMATION_FADE) {
      weekTransitionCurrent.setTranslationX(offset * .16f);
      weekTransitionNext.setTranslationX(0f);
      weekTransitionCurrent.setAlpha(1f - progress);
      weekTransitionNext.setAlpha(progress);
      float scale = .975f + .025f * progress;
      weekTransitionCurrent.setScaleX(1f - .025f * progress);
      weekTransitionCurrent.setScaleY(1f - .025f * progress);
      weekTransitionNext.setScaleX(scale);
      weekTransitionNext.setScaleY(scale);
      weekTransitionCurrent.setRotationY(0f);
      weekTransitionNext.setRotationY(0f);
    } else {
      weekTransitionCurrent.setTranslationX(offset);
      weekTransitionNext.setTranslationX(weekTransitionDirection * width + offset);
      weekTransitionCurrent.setAlpha(1f);
      weekTransitionNext.setAlpha(1f);
      if (animation == WEEK_ANIMATION_CYLINDER) {
        float angle = 18f * progress;
        weekTransitionCurrent.setCameraDistance(getResources().getDisplayMetrics().density * 12000f);
        weekTransitionNext.setCameraDistance(getResources().getDisplayMetrics().density * 12000f);
        weekTransitionCurrent.setRotationY(-weekTransitionDirection * angle);
        weekTransitionNext.setRotationY(weekTransitionDirection * (18f - angle));
        weekTransitionCurrent.setScaleY(1f - .04f * progress);
        weekTransitionNext.setScaleY(.96f + .04f * progress);
      } else {
        weekTransitionCurrent.setRotationY(0f);
        weekTransitionNext.setRotationY(0f);
        weekTransitionCurrent.setScaleX(1f);
        weekTransitionCurrent.setScaleY(1f);
        weekTransitionNext.setScaleX(1f);
        weekTransitionNext.setScaleY(1f);
      }
    }
  }

  void finishWeekTransition(boolean commit, long duration) {
    if (weekTransitionHost == null) return;
    View current = weekTransitionCurrent;
    View next = weekTransitionNext;
    int width = weekTransitionWidth();
    if (weekAnimation() == WEEK_ANIMATION_FADE) {
      current
          .animate()
          .translationX(commit ? -weekTransitionDirection * dp(18) : 0f)
          .alpha(commit ? 0f : 1f)
          .scaleX(commit ? .975f : 1f)
          .scaleY(commit ? .975f : 1f)
          .setDuration(duration);
      next
          .animate()
          .translationX(0f)
          .alpha(commit ? 1f : 0f)
          .scaleX(commit ? 1f : .975f)
          .scaleY(commit ? 1f : .975f)
          .setDuration(duration)
          .withEndAction(() -> completeWeekTransition(commit));
    } else {
      current
          .animate()
          .translationX(commit ? -weekTransitionDirection * width : 0f)
          .rotationY(weekAnimation() == WEEK_ANIMATION_CYLINDER ? -weekTransitionDirection * 18f : 0f)
          .scaleY(1f)
          .setDuration(duration);
      next
          .animate()
          .translationX(commit ? 0f : weekTransitionDirection * width)
          .rotationY(0f)
          .scaleY(1f)
          .setDuration(duration)
          .withEndAction(() -> completeWeekTransition(commit));
    }
  }

  void completeWeekTransition(boolean commit) {
    if (weekTransitionHost == null) return;
    View finalRoot = commit ? weekTransitionNext : weekTransitionCurrent;
    if (commit) selectedWeek = weekTransitionBaseWeek + weekTransitionDirection;
    if (!commit) {
      selectedWeek = weekTransitionBaseWeek;
      activeHomeBodyZoom = weekTransitionCurrentZoom;
    }
    finalRoot.setTranslationX(0f);
    finalRoot.setTranslationY(0f);
    finalRoot.setAlpha(1f);
    finalRoot.setScaleX(1f);
    finalRoot.setScaleY(1f);
    finalRoot.setRotationY(0f);
    ViewParent parent = finalRoot.getParent();
    if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(finalRoot);
    content.removeAllViews();
    content.addView(finalRoot, new FrameLayout.LayoutParams(-1, -1));
    activeHomeRoot = finalRoot;
    weekTransitionHost = null;
    weekTransitionCurrent = null;
    weekTransitionNext = null;
    weekTransitionCurrentZoom = null;
  }

  void showHomeAnimated(int direction) {
    if (direction == 0 || activeHomeRoot == null) {
      showHome();
      return;
    }
    View current = activeHomeRoot;
    ZoomableScheduleLayout currentZoom = activeHomeBodyZoom;
    int baseWeek = selectedWeek - direction;
    int targetWeek = selectedWeek;
    if (baseWeek < 1 || baseWeek > repo.term.weeks || targetWeek < 1 || targetWeek > repo.term.weeks)
      return;
    View next = buildHomeRoot();
    selectedWeek = baseWeek;
    activeHomeBodyZoom = currentZoom;
    if (next == null) return;
    mountWeekTransition(current, next, currentZoom, baseWeek, direction);
    finishWeekTransition(true, 220);
  }

  String weekTitle() {
    LocalDate start = CalendarEngine.dateOf(repo.term, selectedWeek, 1),
        end = start.plusDays(repo.prefs.getInt("days", 7) - 1);
    return "第 "
        + selectedWeek
        + " 周  ·  "
        + start.format(DateTimeFormatter.ofPattern("MM.dd"))
        + " — "
        + end.format(DateTimeFormatter.ofPattern("MM.dd"));
  }

  String weekHint() {
    int current = CalendarEngine.currentWeek(repo.term, LocalDate.now());
    if (current < 1) return "学期尚未开始 · 第 1 周从 " + CalendarEngine.weekOne(repo.term) + " 开始";
    if (current > repo.term.weeks) return "本学期已结束 · 可继续浏览和编辑课表";
    return (selectedWeek == current ? "本周" : "正在查看第 " + selectedWeek + " 周") + " · 左右滑动切周 · 点击周次选择";
  }

  String displayMode() {
    String value = repo.prefs.getString("scheduleMode", "compact");
    return "full".equals(value) ? value : "compact";
  }

  String displayModeLabel() {
    if ("full".equals(displayMode())) return "完整模式 · 可放大并纵向滚动";
    return "紧凑模式 · 当前周课程完整显示";
  }

  int modePresetScale(String value) {
    if ("full".equals(value)) return FULL_MODE_SCALE;
    return COMPACT_MODE_SCALE;
  }

  int weekAnimation() {
    return Math.max(
        WEEK_ANIMATION_SLIDE,
        Math.min(WEEK_ANIMATION_FADE, repo.prefs.getInt("weekAnimation", WEEK_ANIMATION_SLIDE)));
  }

  String weekAnimationLabel() {
    String[] labels = {"跟手并排", "滚筒", "淡入淡出"};
    return labels[weekAnimation()];
  }

  void weekAnimationDialog() {
    String[] labels = {"跟手并排（两周同时显示）", "滚筒（立体翻页）", "淡入淡出（轻量切换）"};
    new AlertDialog.Builder(this)
        .setTitle("周次切换动画")
        .setSingleChoiceItems(
            labels,
            weekAnimation(),
            (dialog, which) -> {
              repo.prefs.edit().putInt("weekAnimation", which).apply();
              dialog.dismiss();
              showSettings();
            })
        .setNegativeButton("取消", null)
        .show();
  }

  String displayModeScaleLabel() {
    int scale = "full".equals(displayMode())
        ? Math.max(COURSE_SCALE_MIN, Math.min(COURSE_SCALE_MAX, repo.prefs.getInt("courseScale", FULL_MODE_SCALE)))
        : modePresetScale(displayMode());
    return scale + "%";
  }

  void displayModeAndScaleDialog() {
    LinearLayout root = form();
    String[] values = {"compact", "full"};
    int fullScale = Math.max(COURSE_SCALE_MIN, Math.min(COURSE_SCALE_MAX, repo.prefs.getInt("courseScale", FULL_MODE_SCALE)));
    String[] labels = {
      "紧凑模式（" + COMPACT_MODE_SCALE + "%）",
      "完整模式（" + FULL_MODE_SCALE + "%）"
    };
    int selected = Math.max(0, Arrays.asList(values).indexOf(displayMode()));
    Spinner mode = dropdown(root, "课程显示模式", labels, selected);
    SeekBar scale = rangeSlider(
        root,
        "课程块大小（" + COURSE_SCALE_MIN + "–" + COURSE_SCALE_MAX + "%）",
        fullScale,
        COURSE_SCALE_MIN,
        COURSE_SCALE_MAX);
    mode.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            scale.setProgress(modePresetScale(values[position]) - COURSE_SCALE_MIN);
          }

          public void onNothingSelected(AdapterView<?> parent) {}
        });
    AlertDialog dialog =
        editor(
            "课程显示模式与块大小",
            root,
            () -> {
              int position = mode.getSelectedItemPosition();
              repo.prefs
                  .edit()
                  .putString("scheduleMode", values[position])
                  .putInt("courseScale", scale.getProgress() + COURSE_SCALE_MIN)
                  .apply();
              showSettings();
            });
  }

  android.graphics.drawable.Drawable rounded(int color, float radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(radius);
    return d;
  }

  /** Transparent fill with a 1dp border, for secondary buttons. */
  Drawable outlined(int stroke, float radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(Color.TRANSPARENT);
    d.setStroke(Math.max(1, dp(1)), Color.argb(110, Color.red(stroke), Color.green(stroke), Color.blue(stroke)));
    d.setCornerRadius(radius);
    return d;
  }

  LinearLayout buildGrid() {
    int days = repo.prefs.getInt("days", 7);
    List<String[]> slots = CalendarEngine.slots(repo.routine.slots);
    int periods = slots.size();
    String mode = displayMode();
    int label = dp(34),
        width = getResources().getDisplayMetrics().widthPixels - dp(32),
        col = Math.max(dp(34), (width - label) / days),
        compactRow = compactRowHeight(periods),
        modeScale = "full".equals(mode)
            ? Math.max(COURSE_SCALE_MIN, Math.min(COURSE_SCALE_MAX, repo.prefs.getInt("courseScale", FULL_MODE_SCALE)))
            : modePresetScale(mode),
        row = Math.max(dp(24), Math.round(compactRow * modeScale / 100f)),
        head = dp("full".equals(mode) ? 52 : 46);
    LinearLayout grid = vertical();
    LinearLayout heads = new LinearLayout(this);
    heads.addView(tv("", 10, muted), new LinearLayout.LayoutParams(label, head));
    for (int d = 1; d <= days; d++) {
      LocalDate date = CalendarEngine.dateOf(repo.term, selectedWeek, d);
      boolean today = date.equals(LocalDate.now());
      TextView h =
          tv(
              dayName(d) + "\n" + date.format(DateTimeFormatter.ofPattern("MM.dd")),
              11,
              today ? green : muted);
      h.setGravity(Gravity.CENTER);
      h.setTypeface(null, today ? Typeface.BOLD : Typeface.NORMAL);
      if (today) h.setBackground(rounded(accentSoft, dp(11)));
      LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(col - dp(2), head);
      hp.leftMargin = dp(1);
      hp.rightMargin = dp(1);
      heads.addView(h, hp);
    }
    grid.addView(heads);
    FrameLayout board = new FrameLayout(this);
    board.setLayoutParams(new LinearLayout.LayoutParams(width, periods * row));
    for (int p = 1; p <= periods; p++) {
      String[] slot = slots.get(p - 1);
      TextView time = tv(p + "\n" + slot[1] + "\n" + slot[2], 9, muted);
      time.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      time.setPadding(0, dp(6), 0, 0);
      time.setLineSpacing(0, 1f);
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(label, row);
      lp.topMargin = (p - 1) * row;
      board.addView(time, lp);
      for (int d = 1; d <= days; d++) {
        View cell = new View(this);
        cell.setBackground(rounded(softFill, dp(7)));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(col - dp(4), row - dp(4));
        cp.leftMargin = label + (d - 1) * col + dp(2);
        cp.topMargin = (p - 1) * row + dp(2);
        board.addView(cell, cp);
      }
    }
    List<Course> shown = new ArrayList<>();
    if (allMode) {
      for (Course c :
          CalendarEngine.allOccurrences(repo.courses, repo.adjustments, repo.term.weeks))
        if (c.day <= days) shown.add(c);
    } else
      for (Course c : repo.courses) {
        Course actual = CalendarEngine.occurrence(c, selectedWeek, repo.adjustments);
        if (actual != null && actual.day <= days) shown.add(actual);
      }
    shown = CalendarEngine.merge(shown);
    shown.sort(Comparator.comparingInt((Course c) -> c.day).thenComparingInt(c -> c.start));
    Set<Course> placed = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Course c : shown) {
      if (placed.contains(c)) continue;
      List<Course> group = new ArrayList<>();
      group.add(c);
      placed.add(c);
      int min = c.start, max = c.end;
      boolean grew;
      do {
        grew = false;
        for (Course other : shown)
          if (!placed.contains(other)
              && other.day == c.day
              && other.start <= max
              && other.end >= min) {
            group.add(other);
            placed.add(other);
            min = Math.min(min, other.start);
            max = Math.max(max, other.end);
            grew = true;
          }
      } while (grew);
      Course display = c;
      if (allMode && !CalendarEngine.hasWeek(c.weeks, selectedWeek)) {
        display = c.copy();
        display.color = lightenCourseColor(c.color);
      }
      TextView block = courseBlock(display);
      if (group.size() > 1) {
        block.setText(group.size() + " 门课程\n点击展开");
        block.setOnClickListener(
            v -> {
              String[] names = new String[group.size()];
              for (int i = 0; i < names.length; i++) {
                Course o = group.get(i);
                names[i] =
                    o.name
                        + " · "
                        + o.start
                        + "–"
                        + o.end
                        + " 节 · "
                        + CalendarEngine.weekLabel(o.weeks, repo.term.weeks);
              }
              new AlertDialog.Builder(this)
                  .setTitle(dayName(c.day) + " · 重叠课程")
                  .setItems(names, (d, n) -> courseDialog(group.get(n), false))
                  .setNegativeButton("关闭", null)
                  .show();
            });
      } else if (allMode) block.append("\n" + CalendarEngine.weekLabel(c.weeks, repo.term.weeks));
      FrameLayout.LayoutParams bp =
          new FrameLayout.LayoutParams(col - dp(4), (max - min + 1) * row - dp(5));
      bp.leftMargin = label + (c.day - 1) * col + dp(2);
      bp.topMargin = (min - 1) * row;
      board.addView(block, bp);
    }
    grid.addView(board);
    addExamBlocks(board, slots, label, col, row, days);
    return grid;
  }

  int compactRowHeight(int periods) {
    android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
    int reserved = dp(198);
    int available = Math.max(dp(24), metrics.heightPixels - reserved - dp(42));
    int fitted = available / Math.max(1, periods);
    return Math.max(dp(24), Math.min(dp(64), fitted));
  }

  void addExamBlocks(
      FrameLayout board, List<String[]> slots, int label, int col, int row, int days) {
    LocalDate monday = CalendarEngine.dateOf(repo.term, selectedWeek, 1);
    for (Exam exam : repo.exams) {
      try {
        LocalDate date = LocalDate.parse(exam.date);
        int day = (int) (date.toEpochDay() - monday.toEpochDay()) + 1;
        if (day < 1 || day > days) continue;
        int start = examStartPeriod(slots, exam.startTime);
        int end = examEndPeriod(slots, exam.endTime, start);
        TextView block = examBlock(exam);
        FrameLayout.LayoutParams p =
            new FrameLayout.LayoutParams(col - dp(4), (end - start + 1) * row - dp(5));
        p.leftMargin = label + (day - 1) * col + dp(2);
        p.topMargin = (start - 1) * row;
        board.addView(block, p);
      } catch (Exception ignored) {
        // Invalid manually entered exam data is kept in the exam list but omitted from the grid.
      }
    }
  }

  int examStartPeriod(List<String[]> slots, String value) {
    LocalTime time = LocalTime.parse(value);
    for (int i = 0; i < slots.size(); i++) {
      LocalTime start = LocalTime.parse(slots.get(i)[1]), end = LocalTime.parse(slots.get(i)[2]);
      if (!time.isBefore(start) && !time.isAfter(end)) return i + 1;
      if (time.isBefore(start)) return Math.max(1, i);
    }
    return time.isBefore(LocalTime.parse(slots.get(0)[1])) ? 1 : slots.size();
  }

  int examEndPeriod(List<String[]> slots, String value, int fallback) {
    LocalTime time = LocalTime.parse(value);
    for (int i = 0; i < slots.size(); i++) {
      LocalTime start = LocalTime.parse(slots.get(i)[1]), end = LocalTime.parse(slots.get(i)[2]);
      if (time.isAfter(start) && !time.isAfter(end)) return i + 1;
      if (time.equals(end)) return i + 1;
    }
    return fallback;
  }

  TextView examBlock(Exam exam) {
    TextView block =
        tv(
            "考试\n" + exam.name + (exam.room.isEmpty() ? "" : "\n" + exam.room),
            11,
            getColor(R.color.exam_ink));
    block.setPadding(dp(6), dp(5), dp(4), dp(4));
    block.setGravity(Gravity.TOP);
    block.setMaxLines(6);
    block.setLineSpacing(dp(1), 1f);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(getColor(R.color.exam_fill));
    bg.setStroke(Math.max(1, dp(1)), getColor(R.color.exam_stroke));
    bg.setCornerRadius(dp(9));
    block.setBackground(bg);
    block.setOnClickListener(v -> editExam(exam));
    block.setOnTouchListener(this::handleWeekSwipeTouch);
    return block;
  }

  TextView courseBlock(Course c) {
    String room = c.room == null ? "" : c.room.trim();
    String courseInfo = c.name + (room.isEmpty() ? "" : "\n" + room);
    String text =
        courseInfo
            + (repo.prefs.getBoolean("showTeacher", false) && !c.teacher.isEmpty()
                ? "\n" + c.teacher
                : "");
    float textSize = Math.max(8, repo.prefs.getInt("textSize", 12) - 1);
    TextView block = tv(text, textSize, getColor(R.color.ink_light));
    int inset = "full".equals(displayMode()) ? 6 : 3;
    block.setPadding(dp(inset), dp(inset), dp(Math.max(2, inset - 1)), dp(Math.max(2, inset - 1)));
    block.setGravity(Gravity.TOP);
    // Keep the room text as a normal paragraph so long locations wrap instead of being replaced
    // by an ellipsis. The first room characters therefore remain visible in compact cells.
    block.setHorizontallyScrolling(false);
    block.setIncludeFontPadding(false);
    block.setMaxLines(Integer.MAX_VALUE);
    block.setEllipsize(null);
    block.setLineSpacing("full".equals(displayMode()) ? dp(1) : 0, 1f);
    GradientDrawable bg = new GradientDrawable();
    int alpha = repo.prefs.getInt("opacity", 94) * 255 / 100;
    bg.setColor(Color.argb(alpha, Color.red(c.color), Color.green(c.color), Color.blue(c.color)));
    bg.setCornerRadius(dp(9));
    block.setBackground(bg);
    block.setOnClickListener(v -> courseDialog(c, false));
    block.setOnLongClickListener(
        v -> {
          courseDialog(c, true);
          return true;
        });
    block.setOnTouchListener(this::handleWeekSwipeTouch);
    return block;
  }

  int lightenCourseColor(int color) {
    int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
    return Color.rgb(r + (255 - r) * 55 / 100, g + (255 - g) * 55 / 100, b + (255 - b) * 55 / 100);
  }

  String dayName(int d) {
    return new String[] {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"}[d];
  }

  LinearLayout vertical() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  void showCourses() {
    content.removeAllViews();
    LinearLayout root = vertical();
    root.setPadding(dp(18), dp(8), dp(18), dp(10));
    TextView title = tv("课程管理", 22, ink);
    title.setTypeface(null, Typeface.BOLD);
    root.addView(title, lp(-1, 50));
    TextView sub = tv(repo.term.name + "  ·  " + repo.courses.size() + " 门课程", 12, muted);
    sub.setPadding(0, 0, 0, dp(12));
    root.addView(sub, lp(-1, -2));
    for (Course c : repo.courses) {
      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(0, 0, dp(10), 0);
      row.setBackground(ripple(rounded(surface, dp(14)), green, dp(14)));
      row.setOnClickListener(v -> courseDialog(c, false));
      // Colour rail matching the course block on the schedule.
      View swatch = new View(this);
      GradientDrawable rail = new GradientDrawable();
      rail.setColor(c.color);
      rail.setCornerRadii(
          new float[] {dp(14), dp(14), 0, 0, 0, 0, dp(14), dp(14)});
      swatch.setBackground(rail);
      row.addView(swatch, lp(5, -1));
      LinearLayout text = vertical();
      text.setPadding(dp(13), dp(12), dp(8), dp(12));
      TextView name = tv(c.name, 15.5f, ink);
      name.setTypeface(null, Typeface.BOLD);
      text.addView(name);
      TextView meta =
          tv(
              dayName(c.day)
                  + "  第 "
                  + c.start
                  + "–"
                  + c.end
                  + " 节"
                  + (c.room.isEmpty() ? "" : "  ·  " + c.room)
                  + (c.teacher.isEmpty() ? "" : "  ·  " + c.teacher),
              12.5f,
              muted);
      meta.setPadding(0, dp(3), 0, 0);
      text.addView(meta);
      TextView weeks = tv(CalendarEngine.weekLabel(c.weeks, repo.term.weeks), 11.5f, green);
      weeks.setPadding(0, dp(3), 0, 0);
      text.addView(weeks);
      row.addView(text, lp(0, -2, 1));
      Button edit = button("编辑");
      edit.setTextColor(green);
      edit.setBackground(ripple(rounded(accentSoft, dp(10)), green, dp(10)));
      edit.setOnClickListener(v -> courseDialog(c, true));
      row.addView(edit, lp(54, 36));
      LinearLayout.LayoutParams rp = lp(-1, -2);
      rp.bottomMargin = dp(9);
      root.addView(row, rp);
    }
    if (repo.courses.isEmpty()) {
      LinearLayout empty = card(root);
      TextView none = tv("还没有课程。点击右上角「添加」录入第一门课。", 13.5f, muted);
      none.setPadding(dp(14), dp(18), dp(14), dp(18));
      empty.addView(none);
    }
    ScrollView sv = new ScrollView(this);
    sv.addView(root);
    content.addView(sv);
  }

  void showSettings() {
    showSettings(0);
  }

  void showSettings(int restoreScrollY) {
    content.removeAllViews();
    LinearLayout root = vertical();
    root.setPadding(dp(18), dp(8), dp(18), dp(16));
    TextView title = tv("设置", 22, ink);
    title.setTypeface(null, Typeface.BOLD);
    root.addView(title, lp(-1, 50));
    LinearLayout semester = section(root, "学期与周次");
    row(semester, "当前课表", repo.term.name, v -> termDialog());
    row(
        semester,
        "课表日期",
        repo.term.startDate + " · " + repo.term.weeks + " 周",
        v -> termDialog());
    row(
        semester,
        "当前周校准",
        "第 " + CalendarEngine.currentWeek(repo.term, LocalDate.now()) + " 周",
        v -> calibrateDialog());
    LinearLayout reminders = section(root, "提醒与考试");
    row(
        reminders,
        "课程提醒",
        repo.prefs.getBoolean("courseReminders", false)
            ? "已开启 · 提前 " + repo.prefs.getInt("courseLead", 10) + " 分钟"
            : "点击开启并选择提前时间",
        v -> reminderDialog());
    row(
        reminders,
        "考试安排",
        repo.exams.size() + " 场 · 日期、时间、考场与提醒",
        v -> {
          page = 3;
          refresh();
        });
    row(
        reminders,
        "中英翻译",
        "中文 ↔ English · 需要网络",
        v -> {
          page = 5;
          refresh();
        });
    LinearLayout display = section(root, "课程显示");
    row(
        display,
        "显示范围",
        allMode ? "整个学期 · 标注适用周次" : "仅显示所选周课程",
        v -> {
          allMode = !allMode;
          repo.prefs.edit().putBoolean("allMode", allMode).apply();
          showSettings(settingsScrollY());
        });
    row(
        display,
        "课程显示模式与块大小",
        displayModeLabel() + " · " + displayModeScaleLabel(),
        v -> displayModeAndScaleDialog());
    row(display, "周次切换动画", weekAnimationLabel(), v -> weekAnimationDialog());
    row(
        display,
        "显示天数",
        repo.prefs.getInt("days", 7) == 7 ? "周一至周日" : "周一至周五",
        v -> {
          repo.prefs.edit().putInt("days", repo.prefs.getInt("days", 7) == 7 ? 5 : 7).apply();
          showSettings(settingsScrollY());
        });
    row(
        display,
        "显示教师",
        repo.prefs.getBoolean("showTeacher", false) ? "已开启" : "已关闭",
        repo.prefs.getBoolean("showTeacher", false),
        enabled -> {
          repo.prefs.edit().putBoolean("showTeacher", enabled).apply();
          showSettings(settingsScrollY());
        });
    LinearLayout data = section(root, "作息与数据");
    row(
        data,
        "作息方案",
        repo.routine.name + " · " + CalendarEngine.slots(repo.routine.slots).size() + " 节",
        v -> routineDialog());
    row(data, "导入课程", "CSV / XLSX · 选择新建或当前课表", v -> pickImport());
    row(data, "学校与教务网站", "湖北经济学院 · 登录后读取课表", v -> schoolsDialog());
    row(data, "备份与恢复", "导出本机数据 / 导入快照", v -> backupDialog());
    row(
        data,
        "检查更新",
        "当前版本 " + currentVersionName() + " · GitHub Releases",
        v -> checkForUpdates(true));
    LinearLayout looks = section(root, "外观");
    row(looks, "背景与主题", "10 款内置背景 · 跟随系统/浅色/深色 · 可选相册", v -> appearanceDialog());
    ScrollView sv = new ScrollView(this);
    sv.addView(root);
    content.addView(sv);
    if (restoreScrollY > 0) {
      sv.post(() -> sv.scrollTo(0, restoreScrollY));
    }
  }

  int settingsScrollY() {
    View current = content.getChildCount() == 0 ? null : content.getChildAt(0);
    return current instanceof ScrollView ? current.getScrollY() : 0;
  }

  /** Section heading plus the card that its rows are added to. */
  LinearLayout section(LinearLayout p, String s) {
    TextView x = tv(s, 11, green);
    x.setTypeface(null, Typeface.BOLD);
    x.setLetterSpacing(0.06f);
    x.setPadding(dp(4), dp(18), 0, dp(6));
    p.addView(x, lp(-1, -2));
    return card(p);
  }

  /** Rounded surface container. Rows inside it are separated by hairlines. */
  LinearLayout card(LinearLayout p) {
    LinearLayout c = vertical();
    c.setBackground(rounded(surface, dp(14)));
    LinearLayout.LayoutParams cp = lp(-1, -2);
    cp.bottomMargin = dp(4);
    p.addView(c, cp);
    return c;
  }

  void row(LinearLayout p, String a, String b, View.OnClickListener l) {
    if (p.getChildCount() > 0) {
      View line = new View(this);
      line.setBackgroundColor(divider);
      LinearLayout.LayoutParams hairline =
          new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.5f)));
      hairline.leftMargin = dp(14);
      p.addView(line, hairline);
    }
    LinearLayout r = new LinearLayout(this);
    r.setPadding(dp(14), dp(11), dp(10), dp(11));
    r.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout text = vertical();
    text.addView(tv(a, 14.5f, ink));
    TextView value = tv(b, 12, muted);
    value.setPadding(0, dp(2), 0, 0);
    text.addView(value);
    r.addView(text, lp(0, -2, 1));
    r.addView(icon(R.drawable.ic_chevron, muted, 17));
    r.setBackground(ripple(rounded(Color.TRANSPARENT, dp(14)), green, dp(14)));
    r.setOnClickListener(l);
    p.addView(r, lp(-1, -2));
  }

  void row(
      LinearLayout p,
      String a,
      String b,
      boolean checked,
      java.util.function.Consumer<Boolean> changed) {
    if (p.getChildCount() > 0) {
      View line = new View(this);
      line.setBackgroundColor(divider);
      LinearLayout.LayoutParams hairline =
          new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.5f)));
      hairline.leftMargin = dp(14);
      p.addView(line, hairline);
    }
    LinearLayout r = new LinearLayout(this);
    r.setPadding(dp(14), dp(11), dp(10), dp(11));
    r.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout text = vertical();
    text.addView(tv(a, 14.5f, ink));
    TextView value = tv(b, 12, muted);
    value.setPadding(0, dp(2), 0, 0);
    text.addView(value);
    r.addView(text, lp(0, -2, 1));
    TeacherToggle toggle = new TeacherToggle(this, checked);
    r.addView(toggle, new LinearLayout.LayoutParams(dp(54), dp(34)));
    r.setBackground(ripple(rounded(Color.TRANSPARENT, dp(14)), green, dp(14)));
    r.setOnClickListener(
        v -> {
          boolean next = !toggle.checked;
          toggle.setChecked(next);
          changed.accept(next);
        });
    p.addView(r, lp(-1, -2));
  }

  final class TeacherToggle extends View {
    boolean checked;
    Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);

    TeacherToggle(Context context, boolean checked) {
      super(context);
      this.checked = checked;
      setLayerType(View.LAYER_TYPE_SOFTWARE, null);
      setContentDescription("显示教师开关");
    }

    void setChecked(boolean value) {
      checked = value;
      invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float h = getHeight();
      float left = dp(2), right = getWidth() - dp(2);
      float top = (h - dp(22)) / 2f, bottom = top + dp(22);
      track.setStyle(Paint.Style.FILL);
      track.setColor(checked ? green : 0x55386c56);
      canvas.drawRoundRect(new RectF(left, top, right, bottom), dp(12), dp(12), track);
      float radius = dp(9);
      float cx = checked ? right - radius - dp(2) : left + radius + dp(2);
      thumb.setStyle(Paint.Style.FILL);
      thumb.setColor(Color.WHITE);
      thumb.setShadowLayer(dp(2.5f), 0, dp(1.2f), 0x66000000);
      canvas.drawCircle(cx, (top + bottom) / 2f, radius, thumb);
      thumb.clearShadowLayer();
    }
  }

  EditText field(LinearLayout root, String label, String value, boolean number) {
    TextView l = tv(label, 12, muted);
    l.setPadding(0, dp(12), 0, dp(2));
    root.addView(l);
    EditText e = new EditText(this);
    e.setText(value);
    e.setTextColor(ink);
    e.setTextSize(15);
    e.setSingleLine(true);
    if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER);
    root.addView(e, lp(-1, 52));
    return e;
  }

  LinearLayout form() {
    LinearLayout root = vertical();
    root.setPadding(dp(22), dp(6), dp(22), dp(16));
    return root;
  }

  ScrollView scroll(LinearLayout root) {
    ScrollView s = new ScrollView(this);
    s.addView(root);
    return s;
  }

  int dp(float n) {
    return (int) (getResources().getDisplayMetrics().density * n + .5f);
  }

  void toast(Object e) {
    Toast.makeText(
            this,
            e instanceof Exception ? ((Exception) e).getMessage() : String.valueOf(e),
            Toast.LENGTH_LONG)
        .show();
  }

  void work(Repository.Work w, Runnable after) {
    repo.run(
        w,
        e -> {
          if (isFinishing() || isDestroyed()) return;
          if (e != null) toast(e);
          else {
            if (after != null) after.run();
            refresh();
          }
        });
  }

  void refresh() {
    applyPalette();
    backdrop.configure(repo.prefs);
    if (page == 0) showHome();
    else if (page == 1) showCourses();
    else if (page == 3) showExams();
    else if (page == 4) showSchoolPage();
    else if (page == 5) showTranslator();
    else showSettings();
    findViewById(R.id.add).setVisibility(page == 5 ? View.GONE : View.VISIBLE);
    findViewById(R.id.home_more).setVisibility(page == 0 ? View.VISIBLE : View.GONE);
    renderNav();
  }

  interface Save {
    void run() throws Exception;
  }

  AlertDialog editor(String title, LinearLayout root, Save save) {
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll(root))
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    b -> {
                      try {
                        save.run();
                        d.dismiss();
                      } catch (Exception e) {
                        toast(e);
                      }
                    }));
    d.show();
    return d;
  }

  void termDialog() {
    String[] options = new String[repo.terms.size() + 2];
    for (int i = 0; i < repo.terms.size(); i++)
      options[i] = repo.terms.get(i).name + (repo.terms.get(i).id == repo.term.id ? "  ✓" : "");
    options[options.length - 2] = "编辑当前课表";
    options[options.length - 1] = "＋ 新建课表";
    new AlertDialog.Builder(this)
        .setTitle("课表管理")
        .setItems(
            options,
            (d, p) -> {
              if (p < repo.terms.size()) {
                long id = repo.terms.get(p).id;
                work(
                    () -> {
                      repo.prefs.edit().putLong("term", id).commit();
                    },
                    () ->
                        selectedWeek =
                            Math.max(
                                1,
                                Math.min(
                                    repo.term.weeks,
                                    CalendarEngine.currentWeek(repo.term, LocalDate.now()))));
              } else editTerm(p == options.length - 1);
            })
        .setNegativeButton("关闭", null)
        .show();
  }

  void editTerm(boolean fresh) {
    Term t = fresh ? new Term() : repo.term;
    LinearLayout root = form();
    EditText name = field(root, "课表名称", t.name, false), date = dateField(root, "开学日期", t.startDate);
    Spinner weeks = numberDropdown(root, "学期总周数", 20, t.weeks, "周");
    TextView monday = tv("第 1 周周一：" + CalendarEngine.weekOne(t), 13, green);
    root.addView(monday);
    date.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            try {
              monday.setText("第 1 周周一：" + CalendarEngine.monday(LocalDate.parse(s.toString())));
            } catch (Exception e) {
              monday.setText("请输入有效日期");
            }
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
    editor(
        fresh ? "新建课表" : "编辑课表",
        root,
        () -> {
          String n = name.getText().toString().trim();
          LocalDate day = LocalDate.parse(date.getText().toString().trim());
          int w = weeks.getSelectedItemPosition() + 1;
          if (n.isEmpty() || w < 1 || w > 20) throw new Exception("请填写名称，总周数为 1–20");
          if (!fresh) {
            for (Course c : repo.courses)
              if ((c.weeks & ~CalendarEngine.allWeeks(w)) != 0)
                throw new Exception("已有课程超过新的学期周数，请先修改课程");
            if (t.calibratedMonday != null && t.calibratedWeek > w)
              throw new Exception("当前校准周超过新的总周数，请先恢复日期计算");
          }
          String old = t.startDate;
          work(
              () -> {
                t.name = n;
                t.startDate = day.toString();
                t.weeks = w;
                if (fresh) {
                  Routine initial = new Routine();
                  initial.name = repo.routine.name;
                  initial.slots = repo.routine.slots;
                  t.routineId = repo.store.save(initial);
                }
                if (!old.equals(t.startDate)) {
                  t.calibratedMonday = null;
                  t.calibratedWeek = 0;
                }
                t.id = repo.store.save(t);
                repo.prefs.edit().putLong("term", t.id).commit();
              },
              () ->
                  selectedWeek =
                      Math.max(1, Math.min(w, CalendarEngine.currentWeek(t, LocalDate.now()))));
        });
  }

  void calibrateDialog() {
    LinearLayout root = form();
    Spinner week =
        numberDropdown(
            root,
            "本周应为第几教学周",
            repo.term.weeks,
            Math.max(
                1,
                Math.min(repo.term.weeks, CalendarEngine.currentWeek(repo.term, LocalDate.now()))),
            "周");
    TextView note =
        tv("校准从本周周一 " + CalendarEngine.monday(LocalDate.now()) + " 起生效，之后每周自动递增。", 13, muted);
    root.addView(note);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("校准当前周")
            .setView(root)
            .setNegativeButton("取消", null)
            .setNeutralButton(
                "恢复开学日期计算",
                (x, y) ->
                    work(
                        () -> {
                          repo.term.calibratedMonday = null;
                          repo.term.calibratedWeek = 0;
                          repo.store.save(repo.term);
                        },
                        () ->
                            selectedWeek =
                                Math.max(
                                    1,
                                    Math.min(
                                        repo.term.weeks,
                                        CalendarEngine.currentWeek(repo.term, LocalDate.now())))))
            .setPositiveButton("校准", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    b -> {
                      try {
                        int n = week.getSelectedItemPosition() + 1;
                        if (n < 1 || n > repo.term.weeks) throw new Exception("校准周必须在当前课表内");
                        work(
                            () -> {
                              repo.term.calibratedMonday =
                                  CalendarEngine.monday(LocalDate.now()).toString();
                              repo.term.calibratedWeek = n;
                              repo.store.save(repo.term);
                            },
                            () -> selectedWeek = n);
                        d.dismiss();
                      } catch (Exception e) {
                        toast(e);
                      }
                    }));
    d.show();
  }

  void courseDialog(Course displayed, boolean editing) {
    Course original = displayed;
    for (Course c : repo.courses)
      if (c.id == displayed.id) {
        original = c;
        break;
      }
    final Course source = original;
    if (editing) {
      editCourse(source, false);
      return;
    }
    String detail =
        dayName(displayed.day)
            + " · 第 "
            + displayed.start
            + "–"
            + displayed.end
            + " 节\n"
            + timeLabel(displayed)
            + "\n"
            + CalendarEngine.weekLabel(source.weeks, repo.term.weeks)
            + "\n\n教室："
            + (displayed.room.isEmpty() ? "未填写" : displayed.room)
            + "\n教师："
            + (source.teacher.isEmpty() ? "未填写" : source.teacher)
            + "\n备注："
            + (source.note.isEmpty() ? "无" : source.note);
    new AlertDialog.Builder(this)
        .setTitle(source.name)
        .setMessage(detail)
        .setPositiveButton("编辑", (d, w) -> editCourse(source, false))
        .setNeutralButton("更多操作", (d, w) -> courseActions(source))
        .setNegativeButton("关闭", null)
        .show();
  }

  String timeLabel(Course c) {
    List<String[]> slots = CalendarEngine.slots(repo.routine.slots);
    return slots.get(c.start - 1)[1] + " – " + slots.get(c.end - 1)[2];
  }

  void courseActions(Course c) {
    String[] actions = {"复制课程", "所选周临时取消 / 调课 / 换教室", "恢复所选周原始安排", "删除课程"};
    new AlertDialog.Builder(this)
        .setTitle(c.name)
        .setItems(
            actions,
            (d, p) -> {
              if (p == 0) editCourse(c, true);
              else if (p == 1) adjustCourse(c);
              else if (p == 2) {
                work(
                    () -> {
                      for (Adjustment a : repo.store.adjustments())
                        if (a.courseId == c.id && a.week == selectedWeek) repo.store.delete(a);
                    },
                    () -> toast("已恢复原始安排"));
              } else
                new AlertDialog.Builder(this)
                    .setTitle("删除课程？")
                    .setMessage("删除 " + c.name + " 及其所有临时调整。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (x, y) -> work(() -> repo.deleteCourse(c), null))
                    .show();
            })
        .show();
  }

  void editCourse(Course source, boolean copy) {
    Course c = source == null ? new Course() : source.copy();
    if (source == null) {
      c.termId = repo.term.id;
      c.weeks = CalendarEngine.allWeeks(repo.term.weeks);
      c.color = courseColor(c.name, c.teacher, repo.courses, Collections.emptyList());
    }
    if (copy) c.id = 0;
    LinearLayout root = form();
    EditText name = field(root, "课程名称", c.name, false),
        teacher = field(root, "教师", c.teacher, false),
        room = field(root, "教室", c.room, false);
    Spinner day =
        dropdown(root, "星期", new String[] {"周一", "周二", "周三", "周四", "周五", "周六", "周日"}, c.day - 1);
    int periods = CalendarEngine.slots(repo.routine.slots).size();
    Spinner start = numberDropdown(root, "开始节次", periods, c.start, "节"),
        end = numberDropdown(root, "结束节次", periods, c.end, "节");
    WeekInput weeks = new WeekInput(root, c.weeks, repo.term.weeks);
    EditText note = field(root, "备注", c.note, false);
    note.setSingleLine(false);
    EditText color = field(root, "颜色（#RRGGBB）", String.format("#%06X", 0xffffff & c.color), false);
    Button palette = button("选择课程颜色");
    root.addView(palette);
    palette.setOnClickListener(
        v ->
            new AlertDialog.Builder(this)
                .setItems(
                    new String[] {
                      "薄荷", "晴蓝", "杏黄", "樱粉", "橄榄", "淡紫",
                      "珊瑚", "青灰", "麦芽", "藕紫", "豆绿", "雾蓝"
                    },
                    (d, p) -> color.setText(String.format("#%06X", colors[p] & 0xffffff)))
                .show());
    name.setOnFocusChangeListener(
        (v, focused) -> {
          if (!focused && source == null)
            for (Course other : repo.courses)
              if (other.name.equals(name.getText().toString().trim())) {
                color.setText(String.format("#%06X", other.color & 0xffffff));
                break;
              }
        });
    editor(
        source == null ? "添加课程" : copy ? "复制课程" : "编辑课程",
        root,
        () -> {
          c.name = name.getText().toString().trim();
          c.teacher = teacher.getText().toString().trim();
          c.room = room.getText().toString().trim();
          c.note = note.getText().toString();
          c.day = day.getSelectedItemPosition() + 1;
          c.start = start.getSelectedItemPosition() + 1;
          c.end = end.getSelectedItemPosition() + 1;
          c.weeks = weeks.mask();
          c.color = Color.parseColor(color.getText().toString()) | 0xff000000;
          if (source == null || !c.name.equals(source.name) || !c.teacher.equals(source.teacher))
            c.color = courseColor(c.name, c.teacher, repo.courses, Collections.emptyList());
          CalendarEngine.validate(c, repo.term, CalendarEngine.slots(repo.routine.slots).size());
          Runnable save =
              () ->
                  work(
                      () -> {
                        repo.db.runInTransaction(
                            () -> {
                              repo.saveCourse(c);
                              for (Adjustment a : repo.store.adjustments())
                                if (a.courseId == c.id && !CalendarEngine.hasWeek(c.weeks, a.week))
                                  repo.store.delete(a);
                            });
                      },
                      null);
          String conflicts =
              CalendarEngine.conflicts(c, repo.courses, repo.adjustments, repo.term.weeks);
          if (conflicts.isEmpty()) save.run();
          else confirmConflict(conflicts, save);
        });
  }

  void confirmConflict(String text, Runnable save) {
    new AlertDialog.Builder(this)
        .setTitle("发现课程冲突")
        .setMessage(text.length() > 1600 ? text.substring(0, 1600) + "\n…" : text)
        .setNegativeButton("返回修改", null)
        .setPositiveButton("仍然保存", (d, p) -> save.run())
        .show();
  }

  void adjustCourse(Course source) {
    if (selectedWeek < 1
        || selectedWeek > repo.term.weeks
        || !CalendarEngine.hasWeek(source.weeks, selectedWeek)) {
      toast("请先在课表中选择该课程有课的周次");
      return;
    }
    Course current = CalendarEngine.occurrence(source, selectedWeek, repo.adjustments);
    if (current == null) current = source;
    LinearLayout root = form();
    CheckBox cancel = new CheckBox(this);
    cancel.setText("取消第 " + selectedWeek + " 周这次课程");
    for (Adjustment old : repo.adjustments)
      if (old.courseId == source.id && old.week == selectedWeek) cancel.setChecked(old.canceled);
    root.addView(cancel);
    Spinner day =
        dropdown(
            root, "临时星期", new String[] {"周一", "周二", "周三", "周四", "周五", "周六", "周日"}, current.day - 1);
    int periods = CalendarEngine.slots(repo.routine.slots).size();
    Spinner start = numberDropdown(root, "临时开始节次", periods, current.start, "节"),
        end = numberDropdown(root, "临时结束节次", periods, current.end, "节");
    EditText room = field(root, "临时教室", current.room, false);
    int week = selectedWeek;
    editor(
        "仅调整第 " + week + " 周",
        root,
        () -> {
          Adjustment a = new Adjustment();
          a.courseId = source.id;
          a.week = week;
          a.canceled = cancel.isChecked();
          a.day = day.getSelectedItemPosition() + 1;
          a.start = start.getSelectedItemPosition() + 1;
          a.end = end.getSelectedItemPosition() + 1;
          a.room = room.getText().toString();
          for (Adjustment old : repo.adjustments)
            if (old.courseId == a.courseId && old.week == week) a.id = old.id;
          if (!a.canceled) {
            Course c = source.copy();
            c.day = a.day;
            c.start = a.start;
            c.end = a.end;
            c.room = a.room;
            CalendarEngine.validate(c, repo.term, CalendarEngine.slots(repo.routine.slots).size());
          }
          List<Adjustment> adjusted = new ArrayList<>();
          for (Adjustment old : repo.adjustments)
            if (!(old.courseId == a.courseId && old.week == week)) adjusted.add(old);
          adjusted.add(a);
          Course only = source.copy();
          only.weeks = 1 << (week - 1);
          String conflicts =
              a.canceled
                  ? ""
                  : CalendarEngine.conflicts(only, repo.courses, adjusted, repo.term.weeks);
          Runnable save = () -> work(() -> repo.store.save(a), null);
          if (conflicts.isEmpty()) save.run();
          else confirmConflict(conflicts, save);
        });
  }

  void routineDialog() {
    String[] names = new String[repo.routines.size() + 2];
    for (int i = 0; i < repo.routines.size(); i++)
      names[i] =
          repo.routines.get(i).name + (repo.routines.get(i).id == repo.routine.id ? "  ✓" : "");
    names[names.length - 2] = "编辑当前作息";
    names[names.length - 1] = "＋ 新增作息";
    new AlertDialog.Builder(this)
        .setTitle("作息方案")
        .setItems(
            names,
            (d, p) -> {
              if (p < repo.routines.size()) {
                Routine r = repo.routines.get(p);
                int n = CalendarEngine.slots(r.slots).size();
                for (Course c : repo.courses)
                  if (c.end > n) {
                    toast("该方案节数少于现有课程，请先修改课程");
                    return;
                  }
                for (Adjustment a : repo.adjustments)
                  for (Course c : repo.courses)
                    if (a.courseId == c.id && !a.canceled && a.end > n) {
                      toast("临时调课超出该作息的节次");
                      return;
                    }
                work(
                    () -> {
                      repo.term.routineId = r.id;
                      repo.store.save(repo.term);
                    },
                    null);
              } else editRoutine(p == names.length - 1);
            })
        .setNegativeButton("关闭", null)
        .show();
  }

  void editRoutine(boolean fresh) {
    Routine r = fresh ? new Routine() : repo.routine;
    LinearLayout root = form();
    EditText name = field(root, "方案名称（如：夏季 / 冬季）", fresh ? "新作息" : r.name, false);
    RoutineInput times = new RoutineInput(root, r.slots);
    editor(
        "编辑作息",
        root,
        () -> {
          String n = name.getText().toString().trim(), data = times.value();
          int total = CalendarEngine.slots(data).size();
          if (n.isEmpty()) throw new Exception("请填写方案名称");
          work(
              () -> {
                if (!fresh) {
                  Set<Long> ids = new HashSet<>();
                  for (Term t : repo.store.terms()) if (t.routineId == r.id) ids.add(t.id);
                  Set<Long> courses = new HashSet<>();
                  for (Course c : repo.store.allCourses())
                    if (ids.contains(c.termId)) {
                      if (c.end > total) throw new Exception("现有课程超出新的作息节数");
                      courses.add(c.id);
                    }
                  for (Adjustment a : repo.store.adjustments())
                    if (courses.contains(a.courseId) && !a.canceled && a.end > total)
                      throw new Exception("临时调课超出新的作息节数");
                }
                r.name = n;
                r.slots = data;
                r.id = repo.store.save(r);
                repo.term.routineId = r.id;
                repo.store.save(repo.term);
              },
              null);
        });
  }

  void schoolsDialog() {
    LinearLayout root = form();
    EditText search = field(root, "搜索名称 / 简称 / 关键词", "", false);
    LinearLayout list = vertical();
    root.addView(list);
    Runnable render =
        () -> {
          list.removeAllViews();
          String q = search.getText().toString().toLowerCase(Locale.ROOT);
          for (School s : repo.schools)
            if ((s.name + " " + s.alias + " " + s.keywords).toLowerCase(Locale.ROOT).contains(q)) {
              row(
                  list,
                  s.name,
                  s.url,
                  v ->
                      new AlertDialog.Builder(this)
                          .setTitle(s.name)
                          .setItems(
                              new String[] {"进入教务网站", "修改学校", "删除学校"},
                              (d, p) -> {
                                if (p == 0) openSchool(s);
                                else if (p == 1) editSchool(s);
                                else
                                  new AlertDialog.Builder(this)
                                      .setTitle("删除学校？")
                                      .setMessage("将移除这个教务入口。")
                                      .setNegativeButton("取消", null)
                                      .setPositiveButton(
                                          "删除", (a, b) -> work(() -> repo.store.delete(s), null))
                                      .show();
                              })
                          .show());
            }
          if (list.getChildCount() == 0) list.addView(tv("暂无匹配的学校，点击下方添加。", 13, muted));
        };
    render.run();
    search.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            render.run();
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
    new AlertDialog.Builder(this)
        .setTitle("学校与教务网站")
        .setView(scroll(root))
        .setPositiveButton("添加学校", (d, p) -> editSchool(null))
        .setNeutralButton(
            "清除网站登录状态",
            (d, p) -> {
              android.webkit.CookieManager.getInstance().removeAllCookies(ok -> toast("已清除网站登录状态"));
              android.webkit.WebStorage.getInstance().deleteAllData();
              new android.webkit.WebView(this).clearCache(true);
            })
        .setNegativeButton("关闭", null)
        .show();
  }

  void editSchool(School existing) {
    School s = existing == null ? new School() : existing;
    LinearLayout root = form();
    EditText name = field(root, "学校名称", s.name, false),
        alias = field(root, "简称", s.alias, false),
        keys = field(root, "搜索关键词", s.keywords, false),
        url = field(root, "教务网址（http:// 或 https://）", s.url, false);
    root.addView(tv("湖北经济学院支持登录后读取个人课表；其他学校可下载表格后导入。", 13, muted));
    editor(
        "学校配置",
        root,
        () -> {
          School next = new School();
          next.id = s.id;
          next.name = name.getText().toString().trim();
          next.alias = alias.getText().toString();
          next.keywords = keys.getText().toString();
          next.url = url.getText().toString().trim();
          Repository.validateSchool(next);
          work(() -> repo.store.save(next), null);
        });
  }

  void backupDialog() {
    new AlertDialog.Builder(this)
        .setTitle("备份与恢复")
        .setItems(
            new String[] {"导出完整备份（包含背景）", "从文件恢复备份", "恢复导入前的自动快照", "导出 CSV 导入模板", "导出 XLSX 导入模板"},
            (d, p) -> {
              if (p == 0) createDocument("application/json", "一周课程表备份.json", 201);
              else if (p == 1)
                openDocument(
                    new String[] {"application/json", "text/plain", "application/octet-stream"},
                    202);
              else if (p == 2) {
                List<File> snapshots = repo.snapshots();
                if (snapshots.isEmpty()) {
                  toast("还没有自动快照");
                  return;
                }
                String[] names = new String[snapshots.size()];
                for (int i = 0; i < names.length; i++) {
                  File f = snapshots.get(i);
                  names[i] =
                      java.time.Instant.ofEpochMilli(f.lastModified())
                              .atZone(java.time.ZoneId.systemDefault())
                              .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
                          + "  "
                          + (f.getName().contains("restore") ? "恢复前" : "导入前");
                }
                new AlertDialog.Builder(this)
                    .setTitle("选择恢复点")
                    .setItems(
                        names,
                        (x, y) ->
                            confirmRestore(
                                () ->
                                    new JSONObject(
                                        new String(
                                            java.nio.file.Files.readAllBytes(
                                                snapshots.get(y).toPath()),
                                            java.nio.charset.StandardCharsets.UTF_8))))
                    .show();
              } else if (p == 3) createDocument("text/csv", "课程导入模板.csv", 203);
              else
                createDocument(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "课程导入模板.xlsx",
                    204);
            })
        .setNegativeButton("关闭", null)
        .show();
  }

  interface BackupSource {
    JSONObject read() throws Exception;
  }

  void confirmRestore(BackupSource source) {
    new AlertDialog.Builder(this)
        .setTitle("恢复备份？")
        .setMessage("这会替换全部学期、课程、作息、学校和外观。当前数据会先保存为自动快照。")
        .setNegativeButton("取消", null)
        .setPositiveButton(
            "恢复",
            (d, p) ->
                work(
                    () -> repo.restore(source.read()),
                    () -> {
                      selectedWeek = 1;
                      allMode = repo.prefs.getBoolean("allMode", false);
                      toast("已恢复备份");
                      recreate();
                    }))
        .show();
  }

  void openDocument(String[] types, int request) {
    Intent i =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_MIME_TYPES, types);
    startActivityForResult(i, request);
  }

  void createDocument(String type, String name, int request) {
    startActivityForResult(
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType(type)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, name),
        request);
  }

  void pickImport() {
    new AlertDialog.Builder(this)
        .setTitle("导入课程")
        .setItems(
            new String[] {"选择 CSV / XLSX 文件", "获取标准模板"},
            (d, p) -> {
              if (p == 0)
                openDocument(
                    new String[] {
                      "text/*",
                      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                      "application/octet-stream"
                    },
                    200);
              else backupDialog();
            })
        .setNegativeButton("取消", null)
        .show();
  }

  void appearanceDialog() {
    LinearLayout root = form();
    String[] names = {
      "晨光", "晴空", "森林", "星夜", "山岚", "纸页", "春日", "夏日", "秋色", "冬霜",
      "自定义背景（先从相册选择）"
    };
    Spinner background = new Spinner(this);
    background.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
    background.setSelection(
        repo.prefs.getBoolean("customBackground", false)
            ? 10
            : Math.min(9, repo.prefs.getInt("background", 5)));
    root.addView(tv("内置背景", 13, muted));
    root.addView(background);
    String[] themeNames = {"跟随系统", "浅色主题", "深色主题"};
    Spinner theme = new Spinner(this);
    theme.setContentDescription("主题模式");
    theme.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, themeNames));
    int themeMode =
        repo.prefs.contains("themeMode")
            ? repo.prefs.getInt("themeMode", 0)
            : (repo.prefs.contains("dark") && repo.prefs.getBoolean("dark", false) ? 2 : 0);
    theme.setSelection(Math.max(0, Math.min(2, themeMode)));
    root.addView(tv("主题", 13, muted));
    root.addView(theme);
    SeekBar bright = slider(root, "背景亮度", repo.prefs.getInt("brightness", 100), 100);
    SeekBar blur = slider(root, "模糊程度", repo.prefs.getInt("blur", 0), 100);
    SeekBar veil = slider(root, "背景遮罩", repo.prefs.getInt("veil", 35), 100);
    SeekBar opacity = slider(root, "课程块透明度", repo.prefs.getInt("opacity", 94), 100);
    SeekBar size = slider(root, "文字大小（9–20 sp）", repo.prefs.getInt("textSize", 12) - 9, 11);
    Button photo = button("从相册选择并裁剪背景");
    root.addView(photo);
    AlertDialog appearance =
        editor(
            "背景与外观",
            root,
            () -> {
              if (background.getSelectedItemPosition() == 10
                  && !new File(getFilesDir(), "background.jpg").isFile())
                throw new Exception("请先从相册选择背景");
              int selectedTheme = theme.getSelectedItemPosition();
              repo.prefs
                  .edit()
                  .putBoolean("customBackground", background.getSelectedItemPosition() == 10)
                  .putInt("background", Math.min(9, background.getSelectedItemPosition()))
                  .putInt("brightness", bright.getProgress())
                  .putInt("blur", blur.getProgress())
                  .putInt("veil", veil.getProgress())
                  .putInt("opacity", opacity.getProgress())
                  .putInt("textSize", size.getProgress() + 9)
                  .putInt("themeMode", selectedTheme)
                  .putBoolean(
                      "dark", selectedTheme == 2 || (selectedTheme == 0 && systemDark()))
                  .apply();
              recreate();
            });
    photo.setOnClickListener(
        v -> {
          appearance.dismiss();
          openDocument(new String[] {"image/*"}, 205);
        });
  }

  SeekBar slider(LinearLayout root, String name, int value, int max) {
    TextView l = tv(name + " · " + value, 13, muted);
    root.addView(l);
    SeekBar s = new SeekBar(this);
    s.setMax(max);
    s.setProgress(value);
    root.addView(s, lp(-1, 50));
    s.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int p, boolean from) {
            l.setText(name + " · " + p);
          }

          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}
        });
    return s;
  }

  SeekBar rangeSlider(LinearLayout root, String name, int value, int min, int max) {
    TextView l = tv(name + " · " + Math.max(min, Math.min(max, value)) + "%", 13, muted);
    root.addView(l);
    SeekBar s = new SeekBar(this);
    s.setMax(max - min);
    s.setProgress(Math.max(0, Math.min(max - min, value - min)));
    root.addView(s, lp(-1, 50));
    s.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int p, boolean from) {
            l.setText(name + " · " + (p + min) + "%");
          }

          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}
        });
    return s;
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 207 && result == RESULT_OK && data != null) {
      long targetId = data.getLongExtra("termId", 0);
      int importMode = data.getIntExtra("importMode", -1);
      String importName = data.getStringExtra("importName");
      if (repo.term == null || repo.term.id != targetId) {
        toast("当前课表已改变，请重新读取课表");
        return;
      }
      File file = new File(getCacheDir(), "school-import.json");
      repo.run(
          () -> {
            JSONObject payload =
                new JSONObject(
                    new String(
                        Repository.readLimited(new FileInputStream(file), 2 * 1024 * 1024),
                        java.nio.charset.StandardCharsets.UTF_8));
            SchoolImporter.Result parsed = SchoolImporter.parse(payload, repo.term.weeks);
            runOnUiThread(
                () -> {
                  if (isFinishing() || isDestroyed()) return;
                  prepareImport(
                      parsed.preview(),
                      new int[] {0, 1, 2, 3, 4, 5, 6, 7, -1},
                      parsed.schoolTerm.isEmpty() ? "" : "湖北经济学院 · " + parsed.schoolTerm,
                      parsed.errors,
                      importMode,
                      importName == null ? "" : importName);
                });
          },
          e -> {
            if (e != null && !isFinishing() && !isDestroyed())
              new AlertDialog.Builder(this)
                  .setTitle("教务课表读取失败")
                  .setMessage(e.getMessage() == null ? "页面没有返回可识别课程，请返回个人课表重新查询后再读取。" : e.getMessage())
                  .setPositiveButton("知道了", null)
                  .show();
          });
      return;
    }
    if (result != RESULT_OK || data == null || data.getData() == null) {
      if (request == 206 && result == RESULT_OK) refresh();
      return;
    }
    Uri uri = data.getData();
    if (request == 200) {
      work(
          () -> {
            byte[] bytes =
                Repository.readLimited(getContentResolver().openInputStream(uri), 12 * 1024 * 1024);
            Importer.Preview preview =
                bytes.length > 2 && bytes[0] == 'P' && bytes[1] == 'K'
                    ? Importer.xlsx(bytes)
                    : Importer.csv(bytes);
            runOnUiThread(
                () -> {
                  if (!isDestroyed() && !isFinishing()) mapImport(preview);
                });
          },
          null);
    } else if (request == 201) {
      work(
          () -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
              if (out == null) throw new IOException("无法写入文件");
              out.write(
                  repo.backup().toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
          },
          () -> toast("备份已导出"));
    } else if (request == 202)
      confirmRestore(
          () ->
              new JSONObject(
                  new String(
                      Repository.readLimited(
                          getContentResolver().openInputStream(uri), 24 * 1024 * 1024),
                      java.nio.charset.StandardCharsets.UTF_8)));
    else if (request == 203 || request == 204) {
      work(
          () -> {
            try (InputStream in =
                    getAssets().open(request == 203 ? "template.csv" : "template.xlsx");
                OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
              if (out == null) throw new IOException("无法写入模板");
              byte[] b = new byte[8192];
              int n;
              while ((n = in.read(b)) != -1) out.write(b, 0, n);
            }
          },
          () -> toast("模板已导出"));
    } else if (request == 205) {
      work(
          () -> {
            byte[] bytes =
                Repository.readLimited(getContentResolver().openInputStream(uri), 20 * 1024 * 1024);
            File f = new File(getCacheDir(), "picked-image");
            java.nio.file.Files.write(f.toPath(), bytes);
            runOnUiThread(
                () ->
                    startActivityForResult(
                        new Intent(this, CropActivity.class)
                            .putExtra(CropActivity.EXTRA_PATH, f.getAbsolutePath()),
                        206));
          },
          null);
    }
  }

  void mapImport(Importer.Preview preview) {
    LinearLayout root = form();
    String[] fields = {"课程名称", "星期", "开始节次", "结束节次", "周次", "教师", "教室", "备注", "颜色"};
    Spinner[] mappings = new Spinner[fields.length];
    String[] headers = new String[preview.headers().length + 1];
    headers[0] = "（不导入 / 使用默认值）";
    System.arraycopy(preview.headers(), 0, headers, 1, preview.headers().length);
    for (int i = 0; i < fields.length; i++) {
      root.addView(tv(fields[i] + (i < 4 ? " *" : ""), 13, muted));
      Spinner spinner = new Spinner(this);
      spinner.setAdapter(
          new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, headers));
      for (int j = 1; j < headers.length; j++)
        if (headers[j].replace("\uFEFF", "").trim().equals(fields[i])) spinner.setSelection(j);
      mappings[i] = spinner;
      root.addView(spinner);
    }
    new AlertDialog.Builder(this)
        .setTitle("对应字段 · " + preview.rows().size() + " 行")
        .setView(scroll(root))
        .setNegativeButton("取消", null)
        .setPositiveButton(
            "继续导入",
            (d, p) -> {
              int[] map = new int[fields.length];
              for (int i = 0; i < map.length; i++)
                map[i] = mappings[i].getSelectedItemPosition() - 1;
              try {
                for (int i = 0; i < 4; i++)
                  if (map[i] < 0) throw new Exception("请对应课程名、星期、开始节次和结束节次");
                prepareImport(preview, map, "", Collections.emptyList());
              } catch (Exception e) {
                toast(e);
              }
            })
        .show();
  }

  String cell(String[] row, int[] map, int index, String fallback) {
    return map[index] < 0 || map[index] >= row.length || row[map[index]].trim().isEmpty()
        ? fallback
        : row[map[index]].trim();
  }

  void prepareImport(
      Importer.Preview table, int[] map, String sourceName, List<String> sourceErrors) {
    prepareImport(table, map, sourceName, sourceErrors, -1, "");
  }

  void prepareImport(
      Importer.Preview table,
      int[] map,
      String sourceName,
      List<String> sourceErrors,
      int forcedMode) {
    prepareImport(table, map, sourceName, sourceErrors, forcedMode, "");
  }

  void prepareImport(
      Importer.Preview table,
      int[] map,
      String sourceName,
      List<String> sourceErrors,
      int forcedMode,
      String forcedName) {
    List<Course> valid = new ArrayList<>();
    List<String> errors = new ArrayList<>(sourceErrors);
    int row = 1;
    for (String[] data : table.rows()) {
      row++;
      boolean empty = true;
      for (String item : data) if (!item.trim().isEmpty()) empty = false;
      if (empty) continue;
      try {
        Course course = new Course();
        course.name = cell(data, map, 0, "");
        String day = cell(data, map, 1, "1");
        course.day =
            day.matches("[1-7]")
                ? Integer.parseInt(day)
                : Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日").indexOf(day) + 1;
        course.start = Integer.parseInt(cell(data, map, 2, "1"));
        course.end = Integer.parseInt(cell(data, map, 3, "2"));
        course.weeks = CalendarEngine.parseWeeks(cell(data, map, 4, "每周"), repo.term.weeks);
        course.teacher = cell(data, map, 5, "");
        course.room = cell(data, map, 6, "");
        course.note = cell(data, map, 7, "");
        course.color = courseColor(course.name, course.teacher, repo.courses, valid);
        String color = cell(data, map, 8, "");
        if (!color.isEmpty()) course.color = Color.parseColor(color) | 0xff000000;
        CalendarEngine.validate(course, repo.term, CalendarEngine.slots(repo.routine.slots).size());
        valid.add(course);
      } catch (Exception e) {
        errors.add("第 " + row + " 行：" + e.getMessage());
      }
    }
    if (valid.isEmpty()) {
      new AlertDialog.Builder(this)
          .setTitle("没有可导入的课程")
          .setMessage(errors.isEmpty() ? "未读取到课程" : String.join("\n", errors))
          .setPositiveButton("关闭", null)
          .show();
      return;
    }
    String info = "当前课表：" + repo.term.name;
    if (!sourceName.isEmpty()) info += "\n来源：" + sourceName;
    if (!errors.isEmpty())
      info +=
          "\n\n有 "
              + errors.size()
              + " 条错误将跳过：\n"
              + String.join("\n", errors.subList(0, Math.min(8, errors.size())));
    long currentId = repo.term.id;
    if (forcedMode == 0 || forcedMode == 1) {
      importToCoursebook(
          valid,
          currentId,
          forcedMode == 1,
          forcedMode == 1 && !forcedName.trim().isEmpty() ? forcedName.trim() : sourceName,
           errors.size());
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle("导入到哪个课表？")
        .setMessage(info)
        .setNegativeButton("取消", null)
        .setNeutralButton(
            "新建课表", (d, n) -> askNewCoursebookName(valid, currentId, sourceName, errors.size()))
        .setPositiveButton(
            "当前课表",
             (d, n) -> importToCoursebook(valid, currentId, false, sourceName, errors.size()))
        .show();
  }

  void askNewCoursebookName(List<Course> courses, long sourceId, String sourceName, int skipped) {
    EditText name = new EditText(this);
    name.setSingleLine(true);
    name.setHint("例如：2026 春季课表");
    String defaultName = sourceName.trim().isEmpty() ? repo.term.name + " · 新课表" : sourceName.trim();
    name.setText(defaultName);
    name.selectAll();
    new AlertDialog.Builder(this)
        .setTitle("新建课表")
        .setMessage("请输入课表名称")
        .setView(name)
        .setNegativeButton("取消", null)
        .setPositiveButton(
            "导入",
            (d, w) -> {
              String value = name.getText().toString().trim();
              importToCoursebook(
                  courses,
                  sourceId,
                  true,
                  value.isEmpty() ? defaultName : value,
                   skipped);
            })
        .show();
  }

  String courseKey(String name, String teacher) {
    return name.trim() + "\u001f" + teacher.trim();
  }

  int courseColor(String name, String teacher, List<Course> existing, List<Course> pending) {
    String key = courseKey(name, teacher);
    for (Course c : existing)
      if (courseKey(c.name, c.teacher).equals(key)) return c.color;
    for (Course c : pending)
      if (courseKey(c.name, c.teacher).equals(key)) return c.color;
    Set<Integer> used = new HashSet<>();
    for (Course c : existing)
      if (!courseKey(c.name, c.teacher).equals(key)) used.add(c.color & 0x00ffffff);
    for (Course c : pending)
      if (!courseKey(c.name, c.teacher).equals(key)) used.add(c.color & 0x00ffffff);
    for (int color : colors) if (!used.contains(color & 0x00ffffff)) return color;
    return colors[Math.floorMod(key.hashCode(), colors.length)];
  }

  void importToCoursebook(
      List<Course> courses, long sourceId, boolean createNew, String sourceName, int skipped) {
    if (repo.term.id != sourceId) {
      toast("当前课表已改变，请重新导入");
      return;
    }
    ImportPlanner.Plan plan =
        ImportPlanner.plan(
            courses,
            createNew ? Collections.emptyList() : repo.courses,
            createNew ? Collections.emptyList() : repo.adjustments,
            repo.term.weeks,
            false);
    if (plan.accepted.isEmpty()) {
      toast("课程已存在，已跳过重复课程");
      return;
    }
    Repository.ImportResult[] result = new Repository.ImportResult[1];
    Runnable save =
        () ->
            repo.run(
                () ->
                    result[0] =
                        repo.importCourses(sourceId, courses, createNew, sourceName),
                e -> {
                  if (isFinishing() || isDestroyed()) return;
                  if (e != null) {
                    new AlertDialog.Builder(this)
                        .setTitle("课程导入失败")
                        .setMessage(e.getMessage() == null ? "保存课表失败，请重试。" : e.getMessage())
                        .setPositiveButton("知道了", null)
                        .show();
                    return;
                  }
                  if (createNew) {
                    selectedWeek =
                        Math.max(
                            1,
                            Math.min(
                                repo.term.weeks,
                                CalendarEngine.currentWeek(repo.term, LocalDate.now())));
                    page = 0;
                  }
                  toast(
                      "已导入 "
                          + result[0].added
                          + " 条课程"
                          + (createNew ? "，已切换到新课表" : "到当前课表")
                          + (result[0].duplicates == 0
                              ? ""
                              : "；重复 " + result[0].duplicates + " 条已跳过")
                          + (skipped == 0 ? "" : "；错误 " + skipped + " 条已跳过"));
                  refresh();
                });
    if (plan.conflicts.isEmpty()) save.run();
    else confirmConflict(String.join("\n", plan.conflicts), save);
  }

  boolean isDark() {
    return resolveDarkTheme(repo.prefs);
  }

  boolean systemDark() {
    return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
        == Configuration.UI_MODE_NIGHT_YES;
  }

  void applyPalette() {
    boolean dark = isDark();
    ink = getColor(dark ? R.color.ink_dark : R.color.ink_light);
    muted = getColor(dark ? R.color.muted_dark : R.color.muted_light);
    green = getColor(dark ? R.color.green_dark : R.color.green_light);
    surface = getColor(dark ? R.color.surface_dark : R.color.surface_light);
    softFill = getColor(dark ? R.color.surface_soft_dark : R.color.surface_soft_light);
    divider = getColor(dark ? R.color.divider_dark : R.color.divider_light);
    accentSoft = getColor(dark ? R.color.accent_soft_dark : R.color.accent_soft_light);
    onAccent = getColor(dark ? R.color.on_accent_dark : R.color.on_accent_light);
    ((TextView) findViewById(R.id.title)).setTextColor(ink);
    ((TextView) findViewById(R.id.eyebrow)).setTextColor(muted);
    Button add = findViewById(R.id.add);
    add.setTextColor(onAccent);
    add.setBackground(ripple(rounded(green, dp(21)), onAccent, dp(21)));
    Drawable plus = add.getCompoundDrawablesRelative()[0];
    if (plus != null) plus.setColorFilter(onAccent, PorterDuff.Mode.SRC_IN);
    ImageView more = findViewById(R.id.home_more);
    more.setColorFilter(muted, PorterDuff.Mode.SRC_IN);
    more.setBackground(ripple(rounded(softFill, dp(18)), green, dp(18)));
    navigation.setBackgroundColor(getColor(dark ? R.color.nav_dark : R.color.nav_light));
    findViewById(R.id.nav_divider).setBackgroundColor(divider);
    int bars = getColor(dark ? R.color.bg_dark : R.color.bg_light);
    getWindow().setStatusBarColor(bars);
    getWindow().setNavigationBarColor(bars);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  }

  /** Touch feedback over a shaped background, bounded to the same corner radius. */
  Drawable ripple(Drawable content, int tint, float radius) {
    GradientDrawable mask = new GradientDrawable();
    mask.setColor(Color.WHITE);
    mask.setCornerRadius(radius);
    return new RippleDrawable(
        ColorStateList.valueOf(Color.argb(55, Color.red(tint), Color.green(tint), Color.blue(tint))),
        content,
        mask);
  }

  ImageView icon(int resource, int tint, int size) {
    ImageView view = new ImageView(this);
    view.setImageResource(resource);
    view.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
    view.setScaleType(ImageView.ScaleType.FIT_CENTER);
    view.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
    return view;
  }

  LinearLayout.LayoutParams lp(int w, int h) {
    return new LinearLayout.LayoutParams(w > 0 ? dp(w) : w, h > 0 ? dp(h) : h);
  }

  LinearLayout.LayoutParams lp(int w, int h, float weight) {
    return new LinearLayout.LayoutParams(w > 0 ? dp(w) : w, h > 0 ? dp(h) : h, weight);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (repo != null && repo.term != null) {
      ReminderScheduler.reschedule(this);
      LocalDate now = LocalDate.now();
      if (!now.equals(lastViewedDate)) {
        int previous = CalendarEngine.currentWeek(repo.term, lastViewedDate);
        if (selectedWeek == previous)
          selectedWeek =
              Math.max(1, Math.min(repo.term.weeks, CalendarEngine.currentWeek(repo.term, now)));
        lastViewedDate = now;
        refresh();
      }
      if (!updateCheckInFlight && !updateCheckedThisSession) checkForUpdates(false);
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putInt("week", selectedWeek);
    b.putInt("page", page);
  }

  Spinner dropdown(LinearLayout root, String label, String[] options, int selected) {
    TextView text = tv(label, 12, muted);
    text.setPadding(0, dp(12), 0, dp(3));
    root.addView(text);
    Spinner spinner = new Spinner(this);
    spinner.setContentDescription(label);
    spinner.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
    spinner.setSelection(Math.max(0, Math.min(options.length - 1, selected)));
    root.addView(spinner, lp(-1, 48));
    return spinner;
  }

  Spinner numberDropdown(LinearLayout root, String label, int count, int selected, String unit) {
    String[] options = new String[count];
    for (int i = 0; i < count; i++) options[i] = "第 " + (i + 1) + " " + unit;
    return dropdown(root, label, options, selected - 1);
  }

  EditText dateField(LinearLayout root, String label, String value) {
    EditText field = field(root, label, value, false);
    field.setFocusable(false);
    field.setContentDescription(label);
    field.setOnClickListener(
        v -> {
          LocalDate date = LocalDate.parse(field.getText().toString());
          new DatePickerDialog(
                  this,
                  (p, y, m, d) -> field.setText(LocalDate.of(y, m + 1, d).toString()),
                  date.getYear(),
                  date.getMonthValue() - 1,
                  date.getDayOfMonth())
              .show();
        });
    return field;
  }

  EditText timeField(LinearLayout root, String label, String value) {
    EditText field = field(root, label, value, false);
    field.setFocusable(false);
    field.setContentDescription(label);
    field.setOnClickListener(
        v -> {
          LocalTime time = LocalTime.parse(field.getText().toString());
          new TimePickerDialog(
                  this,
                  (p, h, m) -> field.setText(String.format(Locale.ROOT, "%02d:%02d", h, m)),
                  time.getHour(),
                  time.getMinute(),
                  true)
              .show();
        });
    return field;
  }

  class WeekInput {
    final Spinner mode;
    final Button custom;
    int chosen;
    final int total;

    WeekInput(LinearLayout root, int mask, int total) {
      this.chosen = mask;
      this.total = total;
      int initial = 3;
      if (mask == CalendarEngine.allWeeks(total)) initial = 0;
      else if (mask == CalendarEngine.parseWeeks("单周", total)) initial = 1;
      else if (mask == CalendarEngine.parseWeeks("双周", total)) initial = 2;
      mode = dropdown(root, "上课周次", new String[] {"每周", "单周", "双周", "指定周次"}, initial);
      custom = button(CalendarEngine.weekLabel(mask, total) + " · 点击选择");
      root.addView(custom, lp(-1, 48));
      custom.setVisibility(initial == 3 ? View.VISIBLE : View.GONE);
      mode.setOnItemSelectedListener(
          new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int n, long id) {
              custom.setVisibility(n == 3 ? View.VISIBLE : View.GONE);
            }

            public void onNothingSelected(AdapterView<?> p) {}
          });
      custom.setOnClickListener(
          v -> {
            String[] labels = new String[total];
            boolean[] checked = new boolean[total];
            for (int i = 0; i < total; i++) {
              labels[i] = "第 " + (i + 1) + " 周";
              checked[i] = CalendarEngine.hasWeek(chosen, i + 1);
            }
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("选择上课周次")
                .setMultiChoiceItems(labels, checked, (d, n, yes) -> checked[n] = yes)
                .setNegativeButton("取消", null)
                .setPositiveButton(
                    "确认",
                    (d, n) -> {
                      chosen = 0;
                      for (int i = 0; i < total; i++) if (checked[i]) chosen |= 1 << i;
                      custom.setText(
                          chosen == 0
                              ? "尚未选择周次"
                              : CalendarEngine.weekLabel(chosen, total) + " · 点击选择");
                    })
                .show();
          });
    }

    int mask() {
      if (mode.getSelectedItemPosition() < 3)
        return CalendarEngine.parseWeeks(
            new String[] {"每周", "单周", "双周"}[mode.getSelectedItemPosition()], total);
      if (chosen == 0) throw new IllegalArgumentException("请至少选择一周");
      return chosen;
    }
  }

  class RoutineInput {
    final LinearLayout list;
    final List<Spinner> segments = new ArrayList<>();
    final List<EditText> starts = new ArrayList<>(), ends = new ArrayList<>();

    RoutineInput(LinearLayout root, String data) {
      root.addView(tv("逐节选择时段与时间，节次之间的间隔为休息时间。", 13, muted));
      list = vertical();
      root.addView(list);
      for (String[] row : CalendarEngine.slots(data)) add(row);
      Button add = button("＋ 增加一节"), remove = button("移除最后一节");
      root.addView(add, lp(-1, 44));
      root.addView(remove, lp(-1, 44));
      add.setOnClickListener(
          v -> {
            if (starts.size() >= 24) {
              toast("每天最多 24 节");
              return;
            }
            LocalTime previous = LocalTime.parse(ends.get(ends.size() - 1).getText().toString());
            LocalTime next = previous.plusMinutes(10);
            if (!next.isAfter(previous) || !next.plusMinutes(45).isAfter(next)) {
              toast("已到当天末尾");
              return;
            }
            add(
                new String[] {
                  next.getHour() < 12 ? "上午" : next.getHour() < 18 ? "下午" : "晚上",
                  next.toString(),
                  next.plusMinutes(45).toString()
                });
          });
      remove.setOnClickListener(
          v -> {
            int last = starts.size() - 1;
            if (last == 0) {
              toast("至少保留一节");
              return;
            }
            list.removeViewAt(last);
            segments.remove(last);
            starts.remove(last);
            ends.remove(last);
          });
    }

    void add(String[] row) {
      LinearLayout item = vertical();
      item.setPadding(0, dp(5), 0, dp(8));
      Spinner segment =
          dropdown(
              item,
              "第 " + (starts.size() + 1) + " 节 · 时段",
              new String[] {"上午", "下午", "晚上"},
              Arrays.asList("上午", "下午", "晚上").indexOf(row[0]));
      EditText start = timeField(item, "开始时间", row[1]), end = timeField(item, "结束时间", row[2]);
      segments.add(segment);
      starts.add(start);
      ends.add(end);
      list.addView(item);
    }

    String value() {
      StringBuilder out = new StringBuilder();
      for (int i = 0; i < starts.size(); i++) {
        if (i > 0) out.append('\n');
        out.append(segments.get(i).getSelectedItem())
            .append(',')
            .append(starts.get(i).getText())
            .append(',')
            .append(ends.get(i).getText());
      }
      return out.toString();
    }
  }

  String[] leadLabels(boolean off) {
    String[] labels = new String[ReminderPlanner.LEADS.length + (off ? 1 : 0)];
    if (off) labels[0] = "不提醒";
    for (int i = 0; i < ReminderPlanner.LEADS.length; i++) {
      int n = ReminderPlanner.LEADS[i];
      labels[i + (off ? 1 : 0)] = n == 0 ? "开始时" : n == 1440 ? "提前 1 天" : "提前 " + n + " 分钟";
    }
    return labels;
  }

  int leadIndex(int lead, boolean off) {
    if (off && lead < 0) return 0;
    for (int i = 0; i < ReminderPlanner.LEADS.length; i++)
      if (ReminderPlanner.LEADS[i] == lead) return i + (off ? 1 : 0);
    return off ? 5 : 2;
  }

  void reminderDialog() {
    LinearLayout root = form();
    CheckBox on = new CheckBox(this);
    on.setText("开启课程提醒（全部学期）");
    on.setChecked(repo.prefs.getBoolean("courseReminders", false));
    root.addView(on);
    Spinner lead =
        dropdown(
            root, "提前时间", leadLabels(false), leadIndex(repo.prefs.getInt("courseLead", 10), false));
    String status = ReminderScheduler.canNotify(this) ? "通知权限已开启" : "通知权限未开启，允许通知后才能收到提醒";
    AlarmManager alarms = getSystemService(AlarmManager.class);
    if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms())
      status += "\n尚未允许精确闹钟，提醒可能延后。";
    String error = ReminderScheduler.state(this).getString("error", "");
    root.addView(tv(status + (error.isEmpty() ? "" : "\n" + error), 13, muted));
    Button exact = button("设置精确提醒权限"), notifications = button("设置通知权限");
    root.addView(exact, lp(-1, 48));
    root.addView(notifications, lp(-1, 48));
    exact.setOnClickListener(
        v -> {
          try {
            if (Build.VERSION.SDK_INT >= 31)
              startActivity(
                  new Intent(
                      android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                      Uri.parse("package:" + getPackageName())));
            else toast("此系统无需额外精确闹钟权限");
          } catch (Exception e) {
            toast("请在系统设置中允许闹钟与提醒");
          }
        });
    notifications.setOnClickListener(v -> requestNotifications());
    root.addView(tv("提醒跟随临时取消、调课和作息变化更新。系统省电限制、强行停止 App 或未授权可能影响送达；考试提醒在每场考试中单独设置。", 12, muted));
    editor(
        "课程提醒",
        root,
        () -> {
          repo.prefs
              .edit()
              .putBoolean("courseReminders", on.isChecked())
              .putInt("courseLead", ReminderPlanner.LEADS[lead.getSelectedItemPosition()])
              .apply();
          ReminderScheduler.reschedule(this);
          if (on.isChecked()) requestNotifications();
          refresh();
        });
  }

  void requestNotifications() {
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 91);
    } else if (!ReminderScheduler.canNotify(this)) {
      try {
        startActivity(
            new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
      } catch (Exception e) {
        toast("请在系统设置中允许通知");
      }
    } else toast("通知权限已开启");
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == 91) {
      ReminderScheduler.reschedule(this);
      toast(ReminderScheduler.canNotify(this) ? "通知已开启" : "通知未开启，可稍后在提醒设置中授权");
    }
  }

  void showExams() {
    content.removeAllViews();
    LinearLayout root = form();
    TextView heading = tv(repo.term.name + " · 考试安排", 20, ink);
    heading.setTypeface(null, Typeface.BOLD);
    root.addView(heading, lp(-1, 50));
    Spinner filter = dropdown(root, "显示范围", new String[] {"即将开始", "全部考试", "已结束"}, 0);
    LinearLayout list = vertical();
    LinearLayout.LayoutParams listParams = lp(-1, -2);
    listParams.topMargin = dp(14);
    root.addView(list, listParams);
    Runnable render =
        () -> {
          list.removeAllViews();
          LocalDateTime now = LocalDateTime.now();
          int visible = 0;
          for (Exam e : repo.exams) {
            LocalDateTime end = LocalDate.parse(e.date).atTime(LocalTime.parse(e.endTime));
            if (filter.getSelectedItemPosition() == 0 && end.isBefore(now)
                || filter.getSelectedItemPosition() == 2 && !end.isBefore(now)) continue;
            visible++;
            LinearLayout card = vertical();
            card.setPadding(dp(14), dp(12), dp(14), dp(13));
            card.setBackground(ripple(rounded(surface, dp(14)), green, dp(14)));
            TextView examName = tv(e.name, 17, ink);
            examName.setTypeface(null, Typeface.BOLD);
            card.addView(examName);
            TextView when = tv(e.date + "  " + e.startTime + "–" + e.endTime, 13.5f, green);
            when.setPadding(0, dp(4), 0, dp(4));
            card.addView(when);
            card.addView(
                tv(
                    "考场："
                        + (e.room.isEmpty() ? "未填写" : e.room)
                        + "\n"
                        + (e.remindMinutes < 0
                            ? "不提醒"
                            : "提醒：" + leadLabels(false)[leadIndex(e.remindMinutes, false)])
                        + (e.note.isEmpty() ? "" : "\n" + e.note),
                    13,
                    muted));
            card.setOnClickListener(
                v ->
                    new AlertDialog.Builder(this)
                        .setTitle(e.name)
                        .setItems(
                            new String[] {"编辑考试", "删除考试"},
                            (d, p) -> {
                              if (p == 0) editExam(e);
                              else
                                new AlertDialog.Builder(this)
                                    .setTitle("删除这场考试？")
                                    .setNegativeButton("取消", null)
                                    .setPositiveButton(
                                        "删除", (x, y) -> work(() -> repo.store.delete(e), null))
                                    .show();
                            })
                        .show());
            LinearLayout.LayoutParams cp = lp(-1, -2);
            cp.bottomMargin = dp(12);
            list.addView(card, cp);
          }
          if (visible == 0) list.addView(tv("暂无考试，点击右上角添加。", 14, muted), lp(-1, 64));
        };
    filter.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int n, long id) {
            render.run();
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    render.run();
    content.addView(scroll(root));
  }

  void showTranslator() {
    content.removeAllViews();
    LinearLayout root = form();
    TextView heading = tv("中英翻译", 22, ink);
    heading.setTypeface(null, Typeface.BOLD);
    root.addView(heading, lp(-1, 50));
    TextView lead = tv("输入中文或英文。点击翻译时发送给在线翻译服务。", 13, muted);
    lead.setPadding(0, 0, 0, dp(12));
    root.addView(lead, lp(-1, -2));
    Button network = button("检查网络权限与连接");
    network.setTextSize(12.5f);
    network.setTextColor(green);
    network.setBackground(ripple(outlined(green, dp(11)), green, dp(11)));
    root.addView(network, lp(-1, 44));
    network.setOnClickListener(v -> checkTranslationNetwork());
    Spinner direction =
        dropdown(root, "翻译方向", new String[] {"中文 → English", "English → 中文"}, 0);
    EditText input = field(root, "输入内容", "", false);
    input.setSingleLine(false);
    input.setMinLines(5);
    input.setLayoutParams(lp(-1, 130));
    input.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(4000)});
    input.setGravity(Gravity.TOP | Gravity.START);
    input.setPadding(dp(10), dp(8), dp(10), dp(8));
    input.setContentDescription("翻译输入");
    LinearLayout actions = new LinearLayout(this);
    actions.setGravity(Gravity.CENTER_VERTICAL);
    Button translate = button("翻译"), swap = button("⇄ 交换方向"), clear = button("清空");
    translate.setTextColor(onAccent);
    translate.setBackground(ripple(rounded(green, dp(11)), onAccent, dp(11)));
    for (Button secondary : new Button[] {swap, clear}) {
      secondary.setTextColor(green);
      secondary.setBackground(ripple(outlined(green, dp(11)), green, dp(11)));
    }
    LinearLayout.LayoutParams primary = lp(0, 46, 1);
    primary.rightMargin = dp(7);
    actions.addView(translate, primary);
    LinearLayout.LayoutParams middle = lp(0, 46, 1.3f);
    middle.rightMargin = dp(7);
    actions.addView(swap, middle);
    actions.addView(clear, lp(0, 46, 1));
    LinearLayout.LayoutParams actionParams = lp(-1, -2);
    actionParams.topMargin = dp(12);
    root.addView(actions, actionParams);
    TextView output = tv("翻译结果会显示在这里", 16, muted);
    output.setGravity(Gravity.TOP | Gravity.START);
    output.setPadding(dp(12), dp(12), dp(12), dp(12));
    output.setMinHeight(dp(150));
    output.setTextIsSelectable(true);
    output.setBackground(rounded(surface, dp(12)));
    output.setContentDescription("翻译结果");
    TextView resultLabel = tv("结果", 12, muted);
    resultLabel.setPadding(0, dp(14), 0, dp(4));
    root.addView(resultLabel);
    root.addView(output, lp(-1, -2));
    Button copy = button("复制结果");
    copy.setTextColor(green);
    copy.setBackground(ripple(outlined(green, dp(11)), green, dp(11)));
    LinearLayout.LayoutParams copyParams = lp(-1, 46);
    copyParams.topMargin = dp(10);
    root.addView(copy, copyParams);
    final String[] successfulResult = {""};
    final int[] generation = {0};
    Runnable enableInput = () -> {
      translate.setEnabled(true);
      swap.setEnabled(true);
      direction.setEnabled(true);
      input.setEnabled(true);
    };
    copy.setOnClickListener(
        v -> {
          String value = successfulResult[0];
          if (value.isEmpty()) {
            toast("暂无翻译结果");
            return;
          }
          ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
              .setPrimaryClip(android.content.ClipData.newPlainText("翻译结果", value));
          toast("已复制翻译结果");
        });
    clear.setOnClickListener(v -> {
      generation[0]++;
      successfulResult[0] = "";
      enableInput.run();
      input.setText("");
      output.setText("翻译结果会显示在这里");
      output.setTextColor(muted);
    });
    swap.setOnClickListener(
        v -> {
          int selected = direction.getSelectedItemPosition();
          direction.setSelection(selected == 0 ? 1 : 0);
          String result = successfulResult[0];
          successfulResult[0] = "";
          if (!result.isEmpty()) {
            input.setText(result);
          }
          output.setText("翻译结果会显示在这里");
          output.setTextColor(muted);
        });
    translate.setOnClickListener(
        v -> {
          String text = input.getText().toString().trim();
          if (text.isEmpty()) {
            toast("请输入要翻译的内容");
            return;
          }
          if (!hasNetworkConnection()) {
            successfulResult[0] = "";
            output.setText("当前没有可用网络，请连接 Wi-Fi 或移动数据后重试。");
            output.setTextColor(0xffa04435);
            return;
          }
          String source = direction.getSelectedItemPosition() == 0 ? "zh-CN" : "en";
          String target = direction.getSelectedItemPosition() == 0 ? "en" : "zh-CN";
          int requestGeneration = ++generation[0];
          successfulResult[0] = "";
          translate.setEnabled(false);
          swap.setEnabled(false);
          direction.setEnabled(false);
          input.setEnabled(false);
          output.setText("正在翻译…");
          output.setTextColor(muted);
          new Thread(
                  () -> {
                    try {
                      String result = TranslationService.translate(text, source, target);
                      runOnUiThread(
                          () -> {
                            if (isFinishing() || isDestroyed() || page != 5
                                || requestGeneration != generation[0]) return;
                            successfulResult[0] = result;
                            output.setText(result);
                            output.setTextColor(ink);
                            enableInput.run();
                          });
                    } catch (Exception e) {
                      runOnUiThread(
                          () -> {
                            if (isFinishing() || isDestroyed() || page != 5
                                || requestGeneration != generation[0]) return;
                            output.setText("翻译失败：" + (e.getMessage() == null ? "请检查网络后重试" : e.getMessage()));
                            output.setTextColor(0xffa04435);
                            enableInput.run();
                          });
                    }
                  },
                  "translation")
              .start();
        });
    content.addView(scroll(root));
  }

  boolean hasNetworkConnection() {
    android.net.ConnectivityManager manager =
        (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    if (manager == null) return false;
    android.net.Network network = manager.getActiveNetwork();
    if (network == null) return false;
    android.net.NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
    return capabilities != null
        && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
  }

  void checkTranslationNetwork() {
    String message =
        hasNetworkConnection()
            ? "已连接网络，可尝试翻译。若仍无法连接，请检查本应用的 Wi-Fi 和移动数据访问是否被系统限制。"
            : "当前没有检测到网络连接，请连接 Wi-Fi 或开启移动数据。";
    message += "\n\n应用已声明联网权限，安装时自动授予。若手机另外限制了应用联网，请打开应用设置允许访问网络。";
    new AlertDialog.Builder(this)
        .setTitle("网络连接")
        .setMessage(message)
        .setNegativeButton("关闭", null)
        .setNeutralButton("应用联网设置", (d, which) -> openTranslationNetworkSettings(true))
        .setPositiveButton(
            "打开网络设置",
            (d, which) -> openTranslationNetworkSettings(false))
        .show();
  }

  void checkForUpdates(boolean manual) {
    if (updateCheckInFlight || (!manual && updateCheckedThisSession)) return;
    updateCheckInFlight = true;
    updateCheckedThisSession = true;
    UpdateService.check(
        (manifest, error) ->
            runOnUiThread(
                () -> {
                  updateCheckInFlight = false;
                  if (isFinishing() || isDestroyed()) return;
                  if (error != null) {
                    // Allow the next foreground resume to retry after a transient network failure.
                    updateCheckedThisSession = false;
                    if (manual) showUpdateCheckError(error);
                    return;
                  }
                  if (!UpdateService.isNewer(manifest, currentVersionCode())) {
                    if (manual) toast("当前已是最新版本");
                    return;
                  }
                  showUpdateDialog(manifest);
                }));
  }

  void showUpdateCheckError(Exception error) {
    String detail = error == null ? "未知错误" : error.getMessage();
    if (detail == null || detail.trim().isEmpty()) detail = "未知错误";
    boolean missingManifest = detail.contains("HTTP 404");
    String message =
        missingManifest
            ? detail
                + "\n\n未找到远程版本清单。请在 GitHub Release 上传 version.json，或确认仓库 main 分支包含 version.json。"
            : detail
                + "\n\n请检查网络连接；如果手机厂商限制了应用联网，请在应用设置中允许 Wi-Fi 和移动数据。";
    new AlertDialog.Builder(this)
        .setTitle("检查更新失败")
        .setMessage(message)
        .setNegativeButton("关闭", null)
        .setNeutralButton("应用联网设置", (d, which) -> openTranslationNetworkSettings(true))
        .setPositiveButton("网络设置", (d, which) -> openTranslationNetworkSettings(false))
        .show();
  }

  void showUpdateDialog(UpdateService.Manifest manifest) {
    StringBuilder message =
        new StringBuilder("发现新版本 ")
            .append(manifest.versionName)
            .append("（versionCode ")
            .append(manifest.versionCode)
            .append("）");
    if (!manifest.releaseDate.isEmpty()) message.append("\n发布日期：").append(manifest.releaseDate);
    if (!manifest.notes().isEmpty()) message.append("\n\n更新内容：\n").append(manifest.notes());
    new AlertDialog.Builder(this)
        .setTitle("发现新版本")
        .setMessage(message.toString())
        .setNegativeButton("稍后", null)
        .setPositiveButton("下载更新", (d, which) -> downloadUpdate(manifest))
        .show();
  }

  void downloadUpdate(UpdateService.Manifest manifest) {
    updateDownloadCancelled = false;
    LinearLayout box = vertical();
    box.setPadding(dp(22), dp(4), dp(22), 0);
    TextView status = tv("正在连接 GitHub…", 13, muted);
    ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(1000);
    box.addView(status, lp(-1, 36));
    box.addView(progress, lp(-1, 40));
    updateProgressDialog =
        new AlertDialog.Builder(this)
            .setTitle("下载更新")
            .setView(box)
            .setNegativeButton("取消", null)
            .create();
    updateProgressDialog.setCanceledOnTouchOutside(false);
    updateProgressDialog.setOnCancelListener(d -> updateDownloadCancelled = true);
    updateProgressDialog.show();
    UpdateService.download(
        this,
        manifest,
        new UpdateService.DownloadCallback() {
          @Override
          public void progress(long downloaded, long total) {
            runOnUiThread(
                () -> {
                  if (updateProgressDialog == null || !updateProgressDialog.isShowing()) return;
                  if (total > 0) {
                    progress.setProgress((int) Math.min(1000, downloaded * 1000 / total));
                    status.setText(
                        "已下载 " + formatBytes(downloaded) + " / " + formatBytes(total));
                  } else {
                    status.setText("已下载 " + formatBytes(downloaded));
                  }
                });
          }

          @Override
          public void complete(File apk, Exception error) {
            runOnUiThread(
                () -> {
                  if (updateProgressDialog != null && updateProgressDialog.isShowing())
                    updateProgressDialog.dismiss();
                  updateProgressDialog = null;
                  if (updateDownloadCancelled) return;
                  if (isFinishing() || isDestroyed()) return;
                  if (error != null) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("更新失败")
                        .setMessage(error.getMessage() == null ? "请稍后重试" : error.getMessage())
                        .setPositiveButton("关闭", null)
                        .show();
                    return;
                  }
                  installUpdate(apk);
                });
          }
        });
  }

  String formatBytes(long bytes) {
    if (bytes < 1024 * 1024) return Math.max(1, bytes / 1024) + " KB";
    return String.format(java.util.Locale.US, "%.1f MB", bytes / 1024d / 1024d);
  }

  void installUpdate(File apk) {
    if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
      new AlertDialog.Builder(this)
          .setTitle("需要安装权限")
          .setMessage("请允许本应用安装未知来源应用，然后回到这里再次点击检查更新。")
          .setNegativeButton("取消", null)
          .setPositiveButton(
              "打开设置",
              (d, which) ->
                  startActivity(
                      new Intent(
                          android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                          Uri.parse("package:" + getPackageName()))))
          .show();
      return;
    }
    Uri uri = Uri.parse("content://" + getPackageName() + ".update-provider/update.apk");
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setDataAndType(uri, "application/vnd.android.package-archive");
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      startActivity(intent);
    } catch (Exception e) {
      toast("无法打开系统安装器：" + e.getMessage());
    }
  }

  int currentVersionCode() {
    try {
      return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
    } catch (Exception e) {
      return 0;
    }
  }

  String currentVersionName() {
    try {
      String value = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
      return value == null ? "未知" : value;
    } catch (Exception e) {
      return "未知";
    }
  }

  void openTranslationNetworkSettings(boolean appSettings) {
    try {
      Intent intent = appSettings
          ? new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
              Uri.parse("package:" + getPackageName()))
          : new Intent(Build.VERSION.SDK_INT >= 29
              ? android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY
              : android.provider.Settings.ACTION_WIRELESS_SETTINGS);
      startActivity(intent);
    } catch (Exception e) {
      toast("请在手机设置中检查本应用的 Wi-Fi 和移动数据访问权限");
    }
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    // The swipe replaces the original child with a transition host. Keep routing
    // the active gesture here so the detached child cannot strand the animation.
    if (weekTransitionHost != null
        && weekSwipeHorizontal
        && event.getActionMasked() != MotionEvent.ACTION_DOWN) {
      handleWeekSwipeTouch(weekTransitionHost, event);
      return true;
    }
    return super.dispatchTouchEvent(event);
  }

  void editExam(Exam source) {
    Exam e = new Exam();
    if (source != null) {
      e.id = source.id;
      e.termId = source.termId;
      e.name = source.name;
      e.date = source.date;
      e.startTime = source.startTime;
      e.endTime = source.endTime;
      e.room = source.room;
      e.note = source.note;
      e.remindMinutes = source.remindMinutes;
    } else e.termId = repo.term.id;
    LinearLayout root = form();
    Set<String> names = new LinkedHashSet<>();
    for (Course c : repo.courses) names.add(c.name);
    String[] courseNames = new String[names.size() + 1];
    courseNames[0] = "手动填写课程名称";
    int at = 1;
    for (String n : names) courseNames[at++] = n;
    Spinner course = dropdown(root, "从当前课程选择（可选）", courseNames, 0);
    EditText name = field(root, "考试名称", e.name, false),
        date = dateField(root, "考试日期", e.date),
        start = timeField(root, "开始时间", e.startTime),
        end = timeField(root, "结束时间", e.endTime),
        room = field(root, "考试教室 / 考场", e.room, false),
        note = field(root, "备注", e.note, false);
    note.setSingleLine(false);
    Spinner lead = dropdown(root, "考试提醒", leadLabels(true), leadIndex(e.remindMinutes, true));
    course.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int n, long id) {
            if (n > 0) name.setText(courseNames[n]);
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    editor(
        source == null ? "添加考试" : "编辑考试",
        root,
        () -> {
          e.name = name.getText().toString().trim();
          e.date = date.getText().toString();
          e.startTime = start.getText().toString();
          e.endTime = end.getText().toString();
          e.room = room.getText().toString().trim();
          e.note = note.getText().toString();
          int selected = lead.getSelectedItemPosition();
          e.remindMinutes = selected == 0 ? -1 : ReminderPlanner.LEADS[selected - 1];
          ReminderPlanner.validateExam(e);
          StringBuilder conflicts = new StringBuilder();
          for (Exam old : repo.exams)
            if (old.id != e.id
                && old.date.equals(e.date)
                && LocalTime.parse(e.startTime).isBefore(LocalTime.parse(old.endTime))
                && LocalTime.parse(old.startTime).isBefore(LocalTime.parse(e.endTime)))
              conflicts
                  .append(old.name)
                  .append(" · ")
                  .append(old.startTime)
                  .append("–")
                  .append(old.endTime)
                  .append('\n');
          Runnable save =
              () ->
                  work(
                      () -> repo.store.save(e),
                      () -> {
                        if (e.remindMinutes >= 0 && !ReminderScheduler.canNotify(this))
                          requestNotifications();
                      });
          if (conflicts.length() > 0)
            new AlertDialog.Builder(this)
                .setTitle("考试时间重叠")
                .setMessage(conflicts.toString())
                .setNegativeButton("返回修改", null)
                .setPositiveButton("仍然保存", (d, p) -> save.run())
                .show();
          else save.run();
        });
  }

  void showSchoolPage() {
    content.removeAllViews();
    LinearLayout root = form();
    TextView heading = tv("教务系统", 23, ink);
    heading.setTypeface(null, Typeface.BOLD);
    root.addView(heading, lp(-1, 50));
    TextView lead = tv("自行登录并完成验证码，打开个人课表后点击「一键读取课表」。", 13, muted);
    lead.setPadding(0, 0, 0, dp(14));
    root.addView(lead, lp(-1, -2));
    if (repo.schools.isEmpty()) {
      LinearLayout empty = card(root);
      TextView none = tv("尚未添加学校，请点击下方管理学校。", 13.5f, muted);
      none.setPadding(dp(14), dp(16), dp(14), dp(16));
      empty.addView(none);
    } else {
      LinearLayout list = card(root);
      for (School s : repo.schools)
        row(
            list,
            s.name,
            SchoolImporter.trusted(s.url) ? "正方教务 · 支持读取课表" : s.url,
            v -> openSchool(s));
    }
    Button manage = button("管理学校 / 搜索学校");
    manage.setTextSize(13);
    manage.setTextColor(green);
    manage.setBackground(ripple(outlined(green, dp(12)), green, dp(12)));
    LinearLayout.LayoutParams mp = lp(-1, 48);
    mp.topMargin = dp(12);
    root.addView(manage, mp);
    manage.setOnClickListener(v -> schoolsDialog());
    content.addView(scroll(root));
  }

  void openSchool(School school) {
    startActivityForResult(
        new Intent(this, WebActivity.class)
            .putExtra("url", school.url)
            .putExtra("termId", repo.term.id)
            .putExtra("termWeeks", repo.term.weeks)
            .putExtra("termName", repo.term.name),
        207);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (repo != null && repo.term != null) {
      page = intent.getBooleanExtra("showExams", false) ? 3 : 0;
      long target = intent.getLongExtra("termId", repo.term.id);
      work(
          () -> repo.prefs.edit().putLong("term", target).apply(),
          () -> {
            selectedWeek =
                Math.max(
                    1,
                    Math.min(
                        repo.term.weeks, CalendarEngine.currentWeek(repo.term, LocalDate.now())));
            refresh();
          });
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (repo != null) repo.close();
  }
}
