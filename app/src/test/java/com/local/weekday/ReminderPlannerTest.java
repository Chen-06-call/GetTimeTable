package com.local.weekday;

import static org.junit.Assert.*;

import com.local.weekday.Models.*;
import java.time.*;
import java.util.*;
import org.junit.Test;

public class ReminderPlannerTest {
  final ZoneId zone = ZoneId.of("Asia/Shanghai");
  final Instant before = Instant.parse("2026-08-30T00:00:00Z");

  Term term() {
    Term t = new Term();
    t.id = 1;
    t.routineId = 1;
    t.startDate = "2026-09-02";
    t.weeks = 2;
    return t;
  }

  Routine routine() {
    Routine r = new Routine();
    r.id = 1;
    return r;
  }

  Course course() {
    Course c = new Course();
    c.id = 2;
    c.termId = 1;
    c.name = "数学";
    c.room = "A101";
    c.weeks = 3;
    return c;
  }

  List<ReminderPlanner.Event> plan(Term t, Routine r, List<Course> cs, List<Adjustment> as) {
    return ReminderPlanner.upcoming(
        List.of(t), List.of(r), cs, as, List.of(), true, 10, before, zone);
  }

  @Test
  public void cancellationAndMoveUseEffectiveOccurrenceAndNewRoom() {
    Course c = course();
    Adjustment a = new Adjustment();
    a.courseId = c.id;
    a.week = 1;
    a.canceled = true;
    List<ReminderPlanner.Event> events = plan(term(), routine(), List.of(c), List.of(a));
    assertEquals(1, events.size());
    assertEquals(
        LocalDateTime.parse("2026-09-07T07:50").atZone(zone).toInstant().toEpochMilli(),
        events.get(0).at);
    a.canceled = false;
    a.day = 3;
    a.start = 3;
    a.end = 4;
    a.room = "B202";
    events = plan(term(), routine(), List.of(c), List.of(a));
    assertEquals(2, events.size());
    assertEquals(
        LocalDateTime.parse("2026-09-02T09:40").atZone(zone).toInstant().toEpochMilli(),
        events.get(0).at);
    assertTrue(events.get(0).text.contains("B202"));
  }

  @Test
  public void adjacentPeriodsHaveOneReminderAndRoutineChangesTime() {
    Course c = course(), d = c.copy();
    c.weeks = d.weeks = 1;
    d.id = 3;
    d.start = 3;
    d.end = 4;
    Routine r = routine();
    List<ReminderPlanner.Event> events = plan(term(), r, List.of(c, d), List.of());
    assertEquals(1, events.size());
    assertTrue(events.get(0).text.contains("1–4"));
    r.slots = r.slots.replace("08:00,08:45", "08:10,08:45");
    assertEquals(
        10 * 60000L, plan(term(), r, List.of(c, d), List.of()).get(0).at - events.get(0).at);
  }

  @Test
  public void calibrationAndTimezoneDetermineActualDate() {
    Term t = term();
    t.calibratedMonday = "2026-09-14";
    t.calibratedWeek = 2;
    Course c = course();
    c.weeks = 2;
    ReminderPlanner.Event e = plan(t, routine(), List.of(c), List.of()).get(0);
    assertEquals(
        LocalDateTime.parse("2026-09-14T07:50").atZone(zone).toInstant().toEpochMilli(), e.at);
    assertEquals(1, e.termId);
    assertTrue(
        ReminderPlanner.upcoming(
                List.of(t),
                List.of(routine()),
                List.of(c),
                List.of(),
                List.of(),
                true,
                10,
                Instant.ofEpochMilli(e.at),
                zone)
            .isEmpty());
  }

  @Test
  public void examReminderIsIndependentOfCourseSwitchAndCanBeOff() {
    Exam e = new Exam();
    e.id = 1;
    e.termId = 6;
    e.name = "期末考试";
    e.date = "2026-09-01";
    e.remindMinutes = 1440;
    List<ReminderPlanner.Event> result =
        ReminderPlanner.upcoming(
            List.of(), List.of(), List.of(), List.of(), List.of(e), false, 10, before, zone);
    assertEquals(1, result.size());
    assertTrue(result.get(0).exam);
    assertEquals(6, result.get(0).termId);
    assertEquals(
        LocalDateTime.parse("2026-08-31T09:00").atZone(zone).toInstant().toEpochMilli(),
        result.get(0).at);
    e.remindMinutes = -1;
    assertTrue(
        ReminderPlanner.upcoming(
                List.of(), List.of(), List.of(), List.of(), List.of(e), true, 10, before, zone)
            .isEmpty());
  }

  @Test
  public void invalidExamTimesAndLeadAreRejected() {
    Exam e = new Exam();
    e.name = "考试";
    e.endTime = "08:00";
    assertThrows(IllegalArgumentException.class, () -> ReminderPlanner.validateExam(e));
    e.endTime = "11:00";
    e.remindMinutes = 13;
    assertThrows(IllegalArgumentException.class, () -> ReminderPlanner.validateExam(e));
  }
}
