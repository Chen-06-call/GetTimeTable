package com.local.weekday;

import com.local.weekday.Models.*;
import java.util.*;

/** Import planning uses bounded week/day/period occupancy, avoiding quadratic scans. */
public final class ImportPlanner {
  public static final class Plan {
    public final List<Course> accepted = new ArrayList<>();
    public final Set<String> conflicts = new LinkedHashSet<>();
    public int duplicates;
  }

  public static String signature(Course c) {
    return c.name.trim()
        + "\u001f"
        + c.teacher
        + "\u001f"
        + c.room
        + "\u001f"
        + c.note
        + "\u001f"
        + c.day
        + ":"
        + c.start
        + ":"
        + c.end
        + ":"
        + c.weeks;
  }

  public static Plan plan(
      List<Course> imported,
      List<Course> existing,
      List<Adjustment> adjustments,
      int total,
      boolean replace) {
    Plan plan = new Plan();
    Set<String> seen = new HashSet<>();
    Map<Integer, String> occupancy = new HashMap<>();
    if (!replace)
      for (Course c : existing) {
        seen.add(signature(c));
        occupy(c, adjustments, total, occupancy, null);
      }
    for (Course c : imported) {
      if (!seen.add(signature(c))) {
        plan.duplicates++;
        continue;
      }
      plan.accepted.add(c);
      occupy(c, Collections.emptyList(), total, occupancy, plan.conflicts);
    }
    return plan;
  }

  private static void occupy(
      Course source,
      List<Adjustment> adjustments,
      int total,
      Map<Integer, String> occupancy,
      Set<String> hits) {
    for (int week = 1; week <= total; week++) {
      Course c = CalendarEngine.occurrence(source, week, adjustments);
      if (c == null) continue;
      for (int p = c.start; p <= c.end; p++) {
        int key = week * 1000 + c.day * 100 + p;
        String before = occupancy.putIfAbsent(key, c.name);
        if (before != null && hits != null && hits.size() < 30)
          hits.add("第 " + week + " 周 · " + c.name + " / " + before);
      }
    }
  }

  private ImportPlanner() {}
}
