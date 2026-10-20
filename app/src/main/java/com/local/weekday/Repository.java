package com.local.weekday;

import android.content.*;
import android.os.*;
import android.util.Base64;
import com.local.weekday.Models.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** All database and file I/O is serialized off the UI thread. */
public final class Repository implements AutoCloseable {
  public final AppDatabase db;
  public final AppDatabase.Store store;
  public final SharedPreferences prefs;
  public final Context context;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  public List<Term> terms = new ArrayList<>();
  public List<Course> courses = new ArrayList<>();
  public List<Adjustment> adjustments = new ArrayList<>();
  public List<Routine> routines = new ArrayList<>();
  public List<School> schools = new ArrayList<>();
  public List<Exam> exams = new ArrayList<>();
  public Term term;
  public Routine routine;

  public interface Work {
    void run() throws Exception;
  }

  public interface Result {
    void done(Exception error);
  }

  public Repository(Context context) {
    this.context = context.getApplicationContext();
    db = AppDatabase.open(context);
    store = db.store();
    prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE);
  }

  public void run(Work work, Result result) {
    executor.execute(
        () -> {
          Exception error = null;
          try {
            work.run();
            reload();
            ReminderScheduler.update(context, store, prefs);
          } catch (Exception e) {
            error = e;
          }
          Exception finalError = error;
          if (result != null) main.post(() -> result.done(finalError));
        });
  }

  public void initialize() {
    for (Routine r : store.routines())
      if ("默认作息".equals(r.name) && Routine.LEGACY_DEFAULT_SLOTS.equals(r.slots)) {
        r.slots = Routine.DEFAULT_SLOTS;
        store.save(r);
      }
    if (!prefs.getBoolean("hbueSeeded", false)) {
      boolean exists = false;
      for (School s : store.schools())
        if (s.name.equals("湖北经济学院") || s.url.contains("218.197.82.133")) exists = true;
      if (!exists) {
        School s = new School();
        s.name = "湖北经济学院";
        s.alias = "湖经 HBUE";
        s.keywords = "湖北 武汉 正方 教务 课表";
        s.url = SchoolImporter.LOGIN_URL;
        store.save(s);
      }
      prefs.edit().putBoolean("hbueSeeded", true).commit();
    }
    if (store.terms().isEmpty()) {
      Routine r = new Routine();
      r.id = store.save(r);
      Term t = new Term();
      t.name = LocalDate.now().getYear() + " 学年 · 第一学期";
      t.startDate = LocalDate.now().withMonth(9).withDayOfMonth(1).toString();
      t.routineId = r.id;
      store.save(t);
    }
  }

  private void reload() {
    terms = store.terms();
    routines = store.routines();
    schools = store.schools();
    adjustments = store.adjustments();
    if (terms.isEmpty()) {
      initialize();
      terms = store.terms();
      routines = store.routines();
    }
    long id = prefs.getLong("term", 0);
    term = terms.get(0);
    for (Term t : terms) if (t.id == id) term = t;
    routine = routines.get(0);
    for (Routine r : routines) if (r.id == term.routineId) routine = r;
    courses = store.courses(term.id);
    exams = store.exams(term.id);
  }

  public void saveCourse(Course c) {
    c.name = c.name.trim();
    c.id = store.save(c);
  }

  public static class ImportResult {
    public long termId;
    public int added, duplicates;
  }

  /** Validates first and commits the coursebook, its routine and courses together. */
  public ImportResult importCourses(
      long sourceId, List<Course> imported, boolean createNew, String name) throws Exception {
    Term source = null;
    for (Term t : store.terms()) if (t.id == sourceId) source = t;
    if (source == null) throw new IllegalArgumentException("目标课表已不存在，请重新导入");
    Routine routine = null;
    for (Routine r : store.routines()) if (r.id == source.routineId) routine = r;
    if (routine == null) throw new IllegalArgumentException("课表作息不存在");
    List<Course> copies = new ArrayList<>();
    int periods = CalendarEngine.slots(routine.slots).size();
    for (Course c : imported) {
      Course copy = c.copy();
      copy.id = 0;
      copy.termId = sourceId;
      CalendarEngine.validate(copy, source, periods);
      copies.add(copy);
    }
    ImportPlanner.Plan plan =
        ImportPlanner.plan(
            copies,
            createNew ? Collections.emptyList() : store.courses(sourceId),
            createNew ? Collections.emptyList() : store.adjustments(),
            source.weeks,
            false);
    ImportResult result = new ImportResult();
    result.termId = sourceId;
    result.duplicates = plan.duplicates;
    if (plan.accepted.isEmpty()) return result;
    Term target = source;
    Routine newRoutine = new Routine();
    if (createNew) {
      newRoutine.name = routine.name;
      newRoutine.slots = routine.slots;
      target = new Term();
      String base = name == null || name.trim().isEmpty() ? source.name + " · 新课表" : name.trim();
      Set<String> names = new HashSet<>();
      for (Term t : store.terms()) names.add(t.name);
      target.name = base;
      for (int n = 2; names.contains(target.name); n++) target.name = base + "（" + n + "）";
      target.startDate = source.startDate;
      target.weeks = source.weeks;
      target.calibratedMonday = source.calibratedMonday;
      target.calibratedWeek = source.calibratedWeek;
    }
    snapshot("before-import");
    Term destination = target;
    db.runInTransaction(
        () -> {
          if (createNew) {
            destination.routineId = store.save(newRoutine);
            destination.id = store.save(destination);
          }
          for (Course course : plan.accepted) {
            course.termId = destination.id;
            store.save(course);
          }
        });
    result.termId = destination.id;
    result.added = plan.accepted.size();
    if (createNew) prefs.edit().putLong("term", destination.id).commit();
    return result;
  }

  public void deleteCourse(Course c) {
    db.runInTransaction(
        () -> {
          store.deleteAdjustments(c.id);
          store.delete(c);
        });
  }

  public File snapshot(String reason) throws Exception {
    File dir = new File(context.getFilesDir(), "snapshots");
    if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建备份目录");
    File file = new File(dir, System.currentTimeMillis() + "-" + reason + ".json");
    byte[] data = backup().toString().getBytes(StandardCharsets.UTF_8);
    File temp = new File(dir, "writing.tmp");
    try (FileOutputStream out = new FileOutputStream(temp)) {
      out.write(data);
      out.getFD().sync();
    }
    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    File[] all = dir.listFiles((d, n) -> n.endsWith(".json"));
    if (all != null) {
      Arrays.sort(all, Comparator.comparing(File::getName).reversed());
      for (int i = 8; i < all.length; i++) Files.deleteIfExists(all[i].toPath());
    }
    return file;
  }

  public List<File> snapshots() {
    File[] files =
        new File(context.getFilesDir(), "snapshots").listFiles((d, n) -> n.endsWith(".json"));
    if (files == null) return new ArrayList<>();
    Arrays.sort(files, Comparator.comparing(File::getName).reversed());
    return Arrays.asList(files);
  }

  public JSONObject backup() throws Exception {
    JSONObject root = new JSONObject();
    root.put("format", "weekday-backup");
    root.put("version", 2);
    root.put("created", Instant.now().toString());
    root.put("terms", encode(store.terms()));
    root.put("courses", encode(store.allCourses()));
    root.put("adjustments", encode(store.adjustments()));
    root.put("routines", encode(store.routines()));
    root.put("schools", encode(store.schools()));
    root.put("exams", encode(store.allExams()));
    root.put("preferences", new JSONObject(prefs.getAll()));
    File bg = new File(context.getFilesDir(), "background.jpg");
    if (bg.isFile())
      root.put(
          "background", Base64.encodeToString(Files.readAllBytes(bg.toPath()), Base64.NO_WRAP));
    return root;
  }

  private static JSONArray encode(List<?> objects) throws Exception {
    JSONArray result = new JSONArray();
    for (Object obj : objects) {
      JSONObject row = new JSONObject();
      for (java.lang.reflect.Field f : obj.getClass().getFields()) row.put(f.getName(), f.get(obj));
      result.put(row);
    }
    return result;
  }

  private static <T> List<T> decode(JSONArray rows, Class<T> type) throws Exception {
    if (rows.length() > 10000) throw new IOException("备份记录过多");
    List<T> list = new ArrayList<>();
    Set<Long> ids = new HashSet<>();
    for (int i = 0; i < rows.length(); i++) {
      T obj = type.getDeclaredConstructor().newInstance();
      JSONObject row = rows.getJSONObject(i);
      for (java.lang.reflect.Field f : type.getFields()) {
        if (f.getType() == String.class) {
          f.set(obj, row.isNull(f.getName()) ? null : row.getString(f.getName()));
        } else if (f.getType() == long.class) f.setLong(obj, row.getLong(f.getName()));
        else if (f.getType() == int.class) f.setInt(obj, row.getInt(f.getName()));
        else if (f.getType() == boolean.class) f.setBoolean(obj, row.getBoolean(f.getName()));
      }
      long id = type.getField("id").getLong(obj);
      if (id <= 0 || !ids.add(id)) throw new IOException("备份包含无效或重复编号");
      list.add(obj);
    }
    return list;
  }

  public void restore(JSONObject root) throws Exception {
    if (!root.optString("format").equals("weekday-backup")
        || (root.optInt("version") != 1 && root.optInt("version") != 2))
      throw new IOException("不支持的备份格式或版本");
    List<Term> ts = decode(root.getJSONArray("terms"), Term.class);
    List<Course> cs = decode(root.getJSONArray("courses"), Course.class);
    List<Adjustment> as = decode(root.getJSONArray("adjustments"), Adjustment.class);
    List<Routine> rs = decode(root.getJSONArray("routines"), Routine.class);
    List<School> ss = decode(root.getJSONArray("schools"), School.class);
    List<Exam> es =
        decode(
            root.optInt("version") == 1 ? new JSONArray() : root.getJSONArray("exams"), Exam.class);
    if (ts.isEmpty() || rs.isEmpty()) throw new IOException("备份缺少学期或作息");
    Map<Long, Term> tm = new HashMap<>();
    Map<Long, Routine> rm = new HashMap<>();
    Map<Long, Course> cm = new HashMap<>();
    for (Routine r : rs) {
      CalendarEngine.slots(r.slots);
      if (r.name == null || r.name.trim().isEmpty()) throw new IOException("无效的作息名称");
      rm.put(r.id, r);
    }
    for (Term t : ts) {
      LocalDate.parse(t.startDate);
      if (t.name == null
          || t.name.trim().isEmpty()
          || t.weeks < 1
          || t.weeks > 20
          || !rm.containsKey(t.routineId)) throw new IOException("无效的学期配置");
      if (t.calibratedMonday != null
          && (!CalendarEngine.monday(LocalDate.parse(t.calibratedMonday))
                  .toString()
                  .equals(t.calibratedMonday)
              || t.calibratedWeek < 1
              || t.calibratedWeek > t.weeks)) throw new IOException("无效的校准配置");
      tm.put(t.id, t);
    }
    for (Course c : cs) {
      Term t = tm.get(c.termId);
      if (t == null) throw new IOException("课程引用了不存在的学期");
      Objects.requireNonNull(c.teacher);
      Objects.requireNonNull(c.room);
      Objects.requireNonNull(c.note);
      CalendarEngine.validate(c, t, CalendarEngine.slots(rm.get(t.routineId).slots).size());
      cm.put(c.id, c);
    }
    Set<String> keys = new HashSet<>();
    for (Adjustment a : as) {
      Course c = cm.get(a.courseId);
      if (c == null
          || !CalendarEngine.hasWeek(c.weeks, a.week)
          || !keys.add(a.courseId + ":" + a.week)) throw new IOException("无效的临时调整");
      if (!a.canceled) {
        Course actual = c.copy();
        actual.day = a.day;
        actual.start = a.start;
        actual.end = a.end;
        actual.room = Objects.requireNonNull(a.room);
        Term t = tm.get(c.termId);
        CalendarEngine.validate(actual, t, CalendarEngine.slots(rm.get(t.routineId).slots).size());
      }
    }
    for (School s : ss) {
      validateSchool(s);
    }
    for (Exam e : es) {
      if (!tm.containsKey(e.termId)) throw new IOException("考试引用了不存在的学期");
      ReminderPlanner.validateExam(e);
    }
    JSONObject settings = root.getJSONObject("preferences");
    SharedPreferences.Editor editor = prefs.edit().clear();
    Set<String> intKeys =
        new HashSet<>(
            Arrays.asList(
                "background", "brightness", "blur", "veil", "opacity", "textSize", "days", "themeMode", "courseScale", "weekAnimation"));
    Set<String> boolKeys =
        new HashSet<>(
            Arrays.asList(
                "dark",
                "showTeacher",
                "allMode",
                "customBackground",
                "courseReminders"));
    for (Iterator<String> it = settings.keys(); it.hasNext(); ) {
      String k = it.next();
      if (k.equals("courseLead")) {
        int lead = settings.getInt(k);
        if (!ReminderPlanner.validLead(lead, false)) throw new IOException("无效的课程提醒时间");
        editor.putInt(k, lead);
      } else if (intKeys.contains(k)) {
        int v = settings.getInt(k);
        boolean invalid =
            k.equals("themeMode")
                ? v < 0 || v > 2
                : k.equals("courseScale")
                    ? v < 100 || v > 200
                    : k.equals("weekAnimation") ? v < 0 || v > 2 : v < 0 || v > 100;
        if (invalid)
          throw new IOException("无效的外观参数");
        editor.putInt(k, v);
      } else if (boolKeys.contains(k)) editor.putBoolean(k, settings.getBoolean(k));
      else if (k.equals("scheduleMode")) {
        String mode = settings.getString(k);
        if (!(mode.equals("compact") || mode.equals("full")))
          throw new IOException("无效的课程显示模式");
        editor.putString(k, mode);
      }
      else if (k.equals("term")) {
        long id = settings.getLong(k);
        if (!tm.containsKey(id)) throw new IOException("无效的当前学期");
        editor.putLong(k, id);
      }
    }
    int days = settings.optInt("days", 7), size = settings.optInt("textSize", 12);
    if ((days != 5 && days != 7) || size < 9 || size > 20) throw new IOException("无效的显示设置");
    int courseScale = settings.optInt("courseScale", 135);
    if (courseScale < 100 || courseScale > 200) throw new IOException("无效的课程块大小");
    byte[] image =
        root.has("background") ? Base64.decode(root.getString("background"), Base64.DEFAULT) : null;
    if (image != null) {
      android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      android.graphics.BitmapFactory.decodeByteArray(image, 0, image.length, bounds);
      if (image.length > 8 * 1024 * 1024
          || bounds.outWidth < 1
          || bounds.outHeight < 1
          || (long) bounds.outWidth * bounds.outHeight > 8000000)
        throw new IOException("背景图片无效或过大");
    }
    if (settings.optBoolean("customBackground", false) && image == null)
      throw new IOException("备份缺少自定义背景");
    // Validation finishes before any existing data is touched.
    snapshot("before-restore");
    File bg = new File(context.getFilesDir(), "background.jpg"),
        staged = new File(context.getFilesDir(), "background.restore");
    if (image != null) Files.write(staged.toPath(), image);
    db.runInTransaction(
        () -> {
          store.clearAllAdjustments();
          store.clearAllCourses();
          store.clearTerms();
          store.clearRoutines();
          store.clearSchools();
          store.clearExams();
          for (Routine r : rs) store.save(r);
          for (Term t : ts) store.save(t);
          for (Course c : cs) store.save(c);
          for (Adjustment a : as) store.save(a);
          for (School s : ss) store.save(s);
          for (Exam e : es) store.save(e);
        });
    if (image != null)
      Files.move(staged.toPath(), bg.toPath(), StandardCopyOption.REPLACE_EXISTING);
    else Files.deleteIfExists(bg.toPath());
    editor.putBoolean("hbueSeeded", true);
    if (!editor.commit()) throw new IOException("外观设置保存失败，可从自动快照恢复");
  }

  public static void validateSchool(School s) throws Exception {
    if (s.name == null || s.name.trim().isEmpty() || s.alias == null || s.keywords == null)
      throw new IOException("请填写学校名称");
    java.net.URI uri = new java.net.URI(s.url);
    if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
        || uri.getHost() == null
        || uri.getUserInfo() != null) throw new IOException("教务网址应为完整的 HTTP 或 HTTPS 地址");
  }

  public static byte[] readLimited(InputStream stream, int max) throws IOException {
    if (stream == null) throw new IOException("无法打开文件");
    try (InputStream in = stream;
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) {
        if (out.size() + n > max) throw new IOException("文件超过大小限制");
        out.write(b, 0, n);
      }
      return out.toByteArray();
    }
  }

  @Override
  public void close() {
    executor.execute(db::close);
    executor.shutdown();
  }
}
