package com.local.weekday;

import static org.junit.Assert.*;

import com.local.weekday.Models.*;
import java.time.*;
import java.util.*;
import org.junit.Test;

public class CalendarEngineTest {
  @Test
  public void academicWeekStartsOnMondayOfOpeningWeek() {
    Term t = new Term();
    t.startDate = "2026-09-02";
    assertEquals(LocalDate.parse("2026-08-31"), CalendarEngine.weekOne(t));
    assertEquals(1, CalendarEngine.currentWeek(t, LocalDate.parse("2026-09-06")));
    assertEquals(2, CalendarEngine.currentWeek(t, LocalDate.parse("2026-09-07")));
    assertEquals(LocalDate.parse("2026-09-07"), CalendarEngine.dateOf(t, 2, 1));
  }

  @Test
  public void calibrationAdvancesAndCanBeRestored() {
    Term t = new Term();
    t.startDate = "2026-09-01";
    t.calibratedMonday = "2026-10-12";
    t.calibratedWeek = 6;
    assertEquals(7, CalendarEngine.currentWeek(t, LocalDate.parse("2026-10-19")));
    assertEquals(LocalDate.parse("2026-10-19"), CalendarEngine.dateOf(t, 7, 1));
    t.calibratedMonday = null;
    t.calibratedWeek = 0;
    assertEquals(8, CalendarEngine.currentWeek(t, LocalDate.parse("2026-10-19")));
  }

  @Test
  public void weekMasksParseAndRejectOutOfTermValues() {
    assertEquals(CalendarEngine.allWeeks(20), CalendarEngine.parseWeeks("每周", 20));
    assertEquals(0b10101, CalendarEngine.parseWeeks("1,3,5", 5));
    assertEquals(0b10101, CalendarEngine.parseWeeks("单周", 5));
    assertEquals(0b1010, CalendarEngine.parseWeeks("双周", 5));
    try {
      CalendarEngine.parseWeeks("1-21", 20);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void mergeRequiresMatchingMetadataAndConsecutivePeriods() {
    Course a = course(1, 2, "A", 7), b = course(3, 3, "A", 7), c = course(4, 4, "B", 7);
    List<Course> result = CalendarEngine.merge(Arrays.asList(c, a, b));
    assertEquals(2, result.size());
    assertEquals(3, result.get(0).end);
    assertEquals(4, result.get(1).start);
  }

  @Test
  public void conflictOnlyExistsOnOverlappingApplicableWeeks() {
    Term t = new Term();
    t.weeks = 4;
    Course a = course(1, 2, "A", 0b0001), b = course(2, 3, "B", 0b0010);
    assertTrue(
        CalendarEngine.conflicts(a, Collections.singletonList(b), Collections.emptyList(), 4)
            .isEmpty());
    b.weeks = 0b0001;
    assertFalse(
        CalendarEngine.conflicts(a, Collections.singletonList(b), Collections.emptyList(), 4)
            .isEmpty());
  }

  @Test
  public void temporaryCancellationAndMoveAffectOnlyOneWeek() {
    Course a = course(1, 2, "A", 0b1111), b = course(3, 4, "B", 0b1111);
    Adjustment adjustment = new Adjustment();
    adjustment.courseId = a.id;
    adjustment.week = 2;
    adjustment.canceled = true;
    assertNull(CalendarEngine.occurrence(a, 2, Collections.singletonList(adjustment)));
    assertNotNull(CalendarEngine.occurrence(a, 3, Collections.singletonList(adjustment)));
    adjustment.canceled = false;
    adjustment.day = 1;
    adjustment.start = 3;
    adjustment.end = 4;
    adjustment.room = "新教室";
    Course moved = CalendarEngine.occurrence(a, 2, Collections.singletonList(adjustment));
    assertEquals("新教室", moved.room);
    assertEquals(3, moved.start);
    assertFalse(
        CalendarEngine.conflicts(
                a, Collections.singletonList(b), Collections.singletonList(adjustment), 4)
            .isEmpty());
    assertEquals(1, a.start);
    java.util.List<Course> semester =
        CalendarEngine.allOccurrences(
            Collections.singletonList(a), Collections.singletonList(adjustment), 4);
    assertEquals(2, semester.size());
    assertEquals(0b1101, semester.get(0).weeks);
    assertEquals(0b0010, semester.get(1).weeks);
    assertEquals(3, semester.get(1).start);
  }

  @Test
  public void timetableRejectsOverlapsAndKeepsRestIntervals() {
    assertEquals(2, CalendarEngine.slots("上午,08:00,08:45\n下午,14:00,14:45").size());
    try {
      CalendarEngine.slots("上午,08:00,09:00\n上午,08:45,09:30");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void defaultRoutineHasFiveFourThreePeriodsAndRequestedTimes() {
    java.util.List<String[]> slots = CalendarEngine.slots(Models.Routine.DEFAULT_SLOTS);
    assertEquals(12, slots.size());
    assertEquals("08:00", slots.get(0)[1]);
    assertEquals("12:15", slots.get(4)[2]);
    assertEquals("14:00", slots.get(5)[1]);
    assertEquals("17:25", slots.get(8)[2]);
    assertEquals("18:30", slots.get(9)[1]);
    assertEquals("20:55", slots.get(11)[2]);
  }

  Course course(int start, int end, String name, int weeks) {
    Course c = new Course();
    c.id = name.equals("A") ? 1 : 2;
    c.day = 1;
    c.start = start;
    c.end = end;
    c.name = name;
    c.teacher = "T";
    c.room = "R";
    c.color = 7;
    c.weeks = weeks;
    return c;
  }
}
