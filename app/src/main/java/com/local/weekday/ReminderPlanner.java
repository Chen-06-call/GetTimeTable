package com.local.weekday;

import com.local.weekday.Models.*;
import java.time.*;
import java.util.*;

/** Pure schedule calculation: the same effective occurrences as the weekly timetable. */
public final class ReminderPlanner {
  public static final int[] LEADS = {0, 5, 10, 15, 30, 60, 120, 1440};

  public static class Event {
    public String key, title, text;
    public long at, start, termId;
    public boolean exam;

    Event(String key, String title, String text, long at, long start, boolean exam, long termId) {
      this.key = key;
      this.title = title;
      this.text = text;
      this.at = at;
      this.start = start;
      this.exam = exam;
      this.termId = termId;
    }
  }

  public static boolean validLead(int n, boolean off) {
    if (off && n == -1) return true;
    for (int v : LEADS) if (v == n) return true;
    return false;
  }

  public static void validateExam(Exam e) {
    if (e.name == null
        || e.name.trim().isEmpty()
        || e.name.length() > 100
        || e.room == null
        || e.note == null
        || e.note.length() > 4000) throw new IllegalArgumentException("请填写考试名称，检查教室和备注");
    LocalDate.parse(e.date);
    LocalTime start = LocalTime.parse(e.startTime), end = LocalTime.parse(e.endTime);
    if (!start.isBefore(end)) throw new IllegalArgumentException("考试结束时间须晚于开始时间（同一天）");
    if (!validLead(e.remindMinutes, true)) throw new IllegalArgumentException("无效的考试提醒时间");
  }

  public static List<Event> upcoming(
      List<Term> terms,
      List<Routine> routines,
      List<Course> courses,
      List<Adjustment> changes,
      List<Exam> exams,
      boolean enabled,
      int lead,
      Instant now,
      ZoneId zone) {
    List<Event> result = new ArrayList<>();
    Map<Long, Routine> rs = new HashMap<>();
    for (Routine r : routines) rs.put(r.id, r);
    if (enabled && validLead(lead, false))
      for (Term t : terms) {
        Routine r = rs.get(t.routineId);
        if (r == null) continue;
        List<String[]> slots = CalendarEngine.slots(r.slots);
        List<Course> termCourses = new ArrayList<>();
        for (Course c : courses) if (c.termId == t.id) termCourses.add(c);
        for (int week = 1; week <= t.weeks; week++) {
          List<Course> actual = new ArrayList<>();
          for (Course c : termCourses) {
            Course o = CalendarEngine.occurrence(c, week, changes);
            if (o != null) {
              o.weeks = 1 << (week - 1);
              actual.add(o);
            }
          }
          for (Course c : CalendarEngine.merge(actual)) {
            if (c.start < 1 || c.end > slots.size()) continue;
            LocalDate date = CalendarEngine.dateOf(t, week, c.day);
            LocalTime time = LocalTime.parse(slots.get(c.start - 1)[1]);
            long start = date.atTime(time).atZone(zone).toInstant().toEpochMilli(),
                at = start - lead * 60000L;
            if (at > now.toEpochMilli())
              result.add(
                  new Event(
                      "course:" + t.id + ":" + c.id + ":" + date,
                      "即将上课 · " + c.name,
                      time
                          + " · "
                          + (c.room.isEmpty() ? "教室未填写" : c.room)
                          + " · 第 "
                          + c.start
                          + "–"
                          + c.end
                          + " 节",
                      at,
                      start,
                      false,
                      t.id));
          }
        }
      }
    for (Exam e : exams) {
      if (e.remindMinutes < 0) continue;
      validateExam(e);
      long
          start =
              LocalDate.parse(e.date)
                  .atTime(LocalTime.parse(e.startTime))
                  .atZone(zone)
                  .toInstant()
                  .toEpochMilli(),
          at = start - e.remindMinutes * 60000L;
      if (at > now.toEpochMilli())
        result.add(
            new Event(
                "exam:" + e.id,
                "考试提醒 · " + e.name,
                e.date
                    + " "
                    + e.startTime
                    + "–"
                    + e.endTime
                    + " · "
                    + (e.room.isEmpty() ? "考场未填写" : e.room),
                at,
                start,
                true,
                e.termId));
    }
    result.sort(Comparator.comparingLong(e -> e.at));
    return result;
  }

  private ReminderPlanner() {}
}
