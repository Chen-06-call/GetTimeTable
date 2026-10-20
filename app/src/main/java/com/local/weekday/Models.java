package com.local.weekday;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

public final class Models {
  @Entity(tableName = "terms")
  public static class Term {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name = "新学期";
    public String startDate = java.time.LocalDate.now().toString();
    public int weeks = 20;
    public long routineId;
    // The calibration anchors the Monday of the chosen week, never a frozen week number.
    public String calibratedMonday;
    public int calibratedWeek;
  }

  @Entity(
      tableName = "courses",
      indices = {@Index("termId")})
  public static class Course {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long termId;
    public String name = "", teacher = "", room = "", note = "";
    public int day = 1, start = 1, end = 2, weeks = 0;
    public int color = 0xffb8d9c1;

    public Course copy() {
      Course c = new Course();
      c.id = id;
      c.termId = termId;
      c.name = name;
      c.teacher = teacher;
      c.room = room;
      c.note = note;
      c.day = day;
      c.start = start;
      c.end = end;
      c.weeks = weeks;
      c.color = color;
      return c;
    }
  }

  @Entity(
      tableName = "adjustments",
      indices = {
        @Index(
            value = {"courseId", "week"},
            unique = true)
      })
  public static class Adjustment {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long courseId;
    public int week;
    public boolean canceled;
    public int day, start, end;
    public String room = "";
  }

  @Entity(tableName = "routines")
  public static class Routine {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name = "默认作息";
    static final String LEGACY_DEFAULT_SLOTS =
        "上午,08:00,08:45\n"
            + "上午,08:55,09:40\n"
            + "上午,10:00,10:45\n"
            + "上午,10:55,11:40\n"
            + "下午,14:00,14:45\n"
            + "下午,14:55,15:40\n"
            + "下午,16:00,16:45\n"
            + "下午,16:55,17:40\n"
            + "晚上,19:00,19:45\n"
            + "晚上,19:55,20:40";
    static final String DEFAULT_SLOTS =
        "上午,08:00,08:45\n"
            + "上午,08:50,09:35\n"
            + "上午,09:50,10:35\n"
            + "上午,10:40,11:25\n"
            + "上午,11:30,12:15\n"
            + "下午,14:00,14:45\n"
            + "下午,14:50,15:35\n"
            + "下午,15:50,16:35\n"
            + "下午,16:40,17:25\n"
            + "晚上,18:30,19:15\n"
            + "晚上,19:20,20:05\n"
            + "晚上,20:10,20:55";
    // One line per period: segment,start,end. Kept as text to preserve custom rest intervals.
    public String slots = DEFAULT_SLOTS;
  }

  @Entity(tableName = "schools")
  public static class School {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name = "", alias = "", keywords = "", url = "";
  }

  @Entity(
      tableName = "exams",
      indices = {@Index("termId")})
  public static class Exam {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long termId;
    public String name = "",
        date = java.time.LocalDate.now().toString(),
        startTime = "09:00",
        endTime = "11:00",
        room = "",
        note = "";
    public int remindMinutes = 30;
  }

  private Models() {}
}
