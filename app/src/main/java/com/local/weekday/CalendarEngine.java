package com.local.weekday;

import com.local.weekday.Models.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Pure Java scheduling rules, shared by rendering, validation and import. */
public final class CalendarEngine {
  public static LocalDate monday(LocalDate date) {
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  public static LocalDate weekOne(Term term) {
    return monday(LocalDate.parse(term.startDate));
  }

  public static int currentWeek(Term t, LocalDate today) {
    LocalDate anchor =
        t.calibratedMonday == null ? weekOne(t) : LocalDate.parse(t.calibratedMonday);
    int base = t.calibratedMonday == null ? 1 : t.calibratedWeek;
    return base + (int) ChronoUnit.WEEKS.between(anchor, monday(today));
  }

  public static LocalDate dateOf(Term t, int week, int day) {
    LocalDate anchor =
        t.calibratedMonday == null ? weekOne(t) : LocalDate.parse(t.calibratedMonday);
    int base = t.calibratedMonday == null ? 1 : t.calibratedWeek;
    return anchor.plusWeeks(week - base).plusDays(day - 1L);
  }

  public static int allWeeks(int total) {
    return (1 << total) - 1;
  }

  public static boolean hasWeek(int mask, int week) {
    return week >= 1 && week <= 20 && (mask & (1 << (week - 1))) != 0;
  }

  public static int parseWeeks(String value, int total) {
    if (total < 1 || total > 20) throw new IllegalArgumentException("学期周数须为 1–20");
    String v =
        value
            .trim()
            .replace('，', ',')
            .replace('、', ',')
            .replace('—', '-')
            .replace('～', '-')
            .replace("周", "")
            .replace("第", "")
            .replace(" ", "");
    if (v.equals("每") || v.equals("全部") || v.equals("每周")) return allWeeks(total);
    if (v.equals("单") || v.equals("双")) {
      int mask = 0;
      for (int w = 1; w <= total; w++) if (w % 2 == (v.equals("单") ? 1 : 0)) mask |= 1 << (w - 1);
      return mask;
    }
    int mask = 0;
    try {
      for (String range : v.split(",", -1)) {
        String[] pair = range.split("-", -1);
        if (pair.length > 2) throw new Exception();
        int first = Integer.parseInt(pair[0]),
            last = pair.length == 1 ? first : Integer.parseInt(pair[1]);
        if (first < 1 || last > total || first > last) throw new Exception();
        for (int w = first; w <= last; w++) mask |= 1 << (w - 1);
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("周次格式应为 每周、单周、双周 或 1-8,10；不能超过学期周数");
    }
    if (mask == 0) throw new IllegalArgumentException("请至少选择一周");
    return mask;
  }

  public static String weekLabel(int mask, int total) {
    if (mask == allWeeks(total)) return "每周";
    StringBuilder b = new StringBuilder();
    for (int w = 1; w <= total; w++)
      if (hasWeek(mask, w)) {
        int last = w;
        while (last < total && hasWeek(mask, last + 1)) last++;
        if (b.length() > 0) b.append(',');
        b.append(w);
        if (last > w) b.append('-').append(last);
        w = last;
      }
    return b + "周";
  }

  public static Course occurrence(Course source, int week, List<Adjustment> adjustments) {
    if (!hasWeek(source.weeks, week)) return null;
    Course c = source.copy();
    for (Adjustment a : adjustments)
      if (a.courseId == c.id && a.week == week) {
        if (a.canceled) return null;
        c.day = a.day;
        c.start = a.start;
        c.end = a.end;
        c.room = a.room;
        break;
      }
    return c;
  }

  public static boolean overlaps(Course a, Course b) {
    return a.day == b.day && a.start <= b.end && b.start <= a.end;
  }

  /** Whole-semester view groups the effective (possibly moved) occurrences by schedule. */
  public static List<Course> allOccurrences(
      List<Course> courses, List<Adjustment> adjustments, int total) {
    List<Course> result = new ArrayList<>();
    for (Course source : courses) {
      Map<String, Course> grouped = new LinkedHashMap<>();
      for (int week = 1; week <= total; week++) {
        Course c = occurrence(source, week, adjustments);
        if (c == null) continue;
        String key = c.day + ":" + c.start + ":" + c.end + ":" + c.room;
        Course existing = grouped.get(key);
        if (existing == null) {
          c.weeks = 1 << (week - 1);
          grouped.put(key, c);
        } else existing.weeks |= 1 << (week - 1);
      }
      result.addAll(grouped.values());
    }
    return result;
  }

  public static String conflicts(
      Course proposed, List<Course> courses, List<Adjustment> adjustments, int total) {
    Set<String> hits = new LinkedHashSet<>();
    for (int w = 1; w <= total; w++) {
      Course a = occurrence(proposed, w, adjustments);
      if (a == null) continue;
      for (Course source : courses) {
        if (source.id != 0 && source.id == proposed.id) continue;
        Course b = occurrence(source, w, adjustments);
        if (b != null && overlaps(a, b)) hits.add("第 " + w + " 周 · " + b.name);
      }
    }
    return String.join("\n", hits);
  }

  public static List<Course> merge(List<Course> input) {
    List<Course> list = new ArrayList<>();
    for (Course c : input) list.add(c.copy());
    list.sort(Comparator.comparingInt((Course c) -> c.day).thenComparingInt(c -> c.start));
    List<Course> out = new ArrayList<>();
    for (Course c : list) {
      Course match = null;
      for (Course p : out)
        if (p.day == c.day
            && p.end + 1 == c.start
            && p.weeks == c.weeks
            && p.name.equals(c.name)
            && p.teacher.equals(c.teacher)
            && p.room.equals(c.room)
            && p.note.equals(c.note)
            && p.color == c.color) {
          match = p;
          break;
        }
      if (match == null) out.add(c);
      else match.end = c.end;
    }
    return out;
  }

  public static List<String[]> slots(String data) {
    List<String[]> result = new ArrayList<>();
    LocalTime previous = null;
    for (String line : data.trim().split("\\r?\\n")) {
      String[] parts = line.trim().split(",", -1);
      if (parts.length != 3 || !Arrays.asList("上午", "下午", "晚上").contains(parts[0]))
        throw new IllegalArgumentException("每行格式：上午,08:00,08:45（也可用下午、晚上）");
      try {
        LocalTime start = LocalTime.parse(parts[1]), end = LocalTime.parse(parts[2]);
        if (!start.isBefore(end) || (previous != null && start.isBefore(previous)))
          throw new Exception();
        previous = end;
      } catch (Exception e) {
        throw new IllegalArgumentException("作息须按时间顺序填写，结束晚于开始，节次不能重叠");
      }
      result.add(parts);
    }
    if (result.isEmpty() || result.size() > 24) throw new IllegalArgumentException("每天支持 1–24 节课");
    return result;
  }

  public static void validate(Course c, Term t, int periods) {
    if (c.name.trim().isEmpty()) throw new IllegalArgumentException("请填写课程名称");
    if (c.name.length() > 100 || c.note.length() > 4000)
      throw new IllegalArgumentException("名称或备注过长");
    if (c.day < 1 || c.day > 7 || c.start < 1 || c.start > c.end || c.end > periods)
      throw new IllegalArgumentException("请检查星期和节次，结束节次不能超过作息节数");
    if (c.weeks == 0 || (c.weeks & ~allWeeks(t.weeks)) != 0)
      throw new IllegalArgumentException("课程周次超出学期范围");
  }

  private CalendarEngine() {}
}
