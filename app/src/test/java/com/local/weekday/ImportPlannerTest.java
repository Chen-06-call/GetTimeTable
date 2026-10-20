package com.local.weekday;

import static org.junit.Assert.*;

import com.local.weekday.Models.*;
import java.util.*;
import org.junit.Test;

public class ImportPlannerTest {
  @Test
  public void appendSkipsExistingButReplacementKeepsImportedCopy() {
    Course c = c("数学");
    ImportPlanner.Plan append =
        ImportPlanner.plan(
            Arrays.asList(c, c.copy()),
            Collections.singletonList(c),
            Collections.emptyList(),
            20,
            false);
    assertEquals(0, append.accepted.size());
    assertEquals(2, append.duplicates);
    ImportPlanner.Plan replacement =
        ImportPlanner.plan(
            Arrays.asList(c, c.copy()),
            Collections.singletonList(c),
            Collections.emptyList(),
            20,
            true);
    assertEquals(1, replacement.accepted.size());
    assertEquals(1, replacement.duplicates);
  }

  @Test
  public void conflictPlanningHonorsCancellations() {
    Course old = c("数学");
    old.id = 8;
    Adjustment cancel = new Adjustment();
    cancel.courseId = 8;
    cancel.week = 1;
    cancel.canceled = true;
    Course next = c("英语");
    ImportPlanner.Plan p =
        ImportPlanner.plan(
            Collections.singletonList(next),
            Collections.singletonList(old),
            Collections.singletonList(cancel),
            1,
            false);
    assertTrue(p.conflicts.isEmpty());
    cancel.canceled = false;
    cancel.day = 1;
    cancel.start = 1;
    cancel.end = 2;
    assertFalse(
        ImportPlanner.plan(
                Collections.singletonList(next),
                Collections.singletonList(old),
                Collections.singletonList(cancel),
                1,
                false)
            .conflicts
            .isEmpty());
  }

  Course c(String name) {
    Course c = new Course();
    c.name = name;
    c.day = 1;
    c.start = 1;
    c.end = 2;
    c.weeks = 1;
    return c;
  }
}
