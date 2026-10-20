package com.local.weekday;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class SchoolImporterTest {
  JSONObject row() throws Exception {
    return new JSONObject()
        .put("name", "高等数学")
        .put("day", "1")
        .put("sections", "1-2,4")
        .put("weeks", "1-16周(单)")
        .put("teacher", "王老师")
        .put("room", "A101");
  }

  JSONObject payload(JSONArray rows) throws Exception {
    return new JSONObject()
        .put("url", SchoolImporter.LOGIN_URL)
        .put("term", "2026-2027 · 第1学期")
        .put("entries", rows);
  }

  @Test
  public void splitsNonAdjacentSectionsAndDeduplicates() throws Exception {
    SchoolImporter.Result r =
        SchoolImporter.parse(payload(new JSONArray().put(row()).put(row())), 20);
    assertEquals(2, r.rows.size());
    assertEquals("1", r.rows.get(0)[2]);
    assertEquals("2", r.rows.get(0)[3]);
    assertEquals("4", r.rows.get(1)[2]);
    assertEquals(
        CalendarEngine.parseWeeks("1,3,5,7,9,11,13,15", 20),
        CalendarEngine.parseWeeks(r.rows.get(0)[4], 20));
    assertEquals(8, r.preview().headers().length);
    assertTrue(r.schoolTerm.contains("2026"));
  }

  @Test
  public void mixedParityAndChineseFormatsPreserveWeekMeaning() {
    assertEquals(
        CalendarEngine.parseWeeks("1,3,5,8,10,12", 20),
        CalendarEngine.parseWeeks(SchoolImporter.normalizeWeeks("1－6周（单）,8～12周（双）", 20), 20));
    assertThrows(IllegalArgumentException.class, () -> SchoolImporter.normalizeWeeks("1-21周", 20));
    assertThrows(IllegalArgumentException.class, () -> SchoolImporter.normalizeWeeks("", 20));
  }

  @Test
  public void incompleteRowsAreReportedRatherThanAssumedAllWeeks() throws Exception {
    SchoolImporter.Result r =
        SchoolImporter.parse(
            payload(
                new JSONArray().put(row()).put(row().put("weeks", "")).put(row().put("day", "0"))),
            20);
    assertEquals(2, r.rows.size());
    assertEquals(2, r.errors.size());
    assertThrows(
        IllegalArgumentException.class,
        () -> SchoolImporter.parse(payload(new JSONArray().put(row().put("weeks", ""))), 20));
  }

  @Test
  public void refusesForeignPageAndDirtySemester() throws Exception {
    assertTrue(SchoolImporter.trusted(SchoolImporter.LOGIN_URL));
    assertFalse(SchoolImporter.trusted("http://218.197.82.133.evil.example/"));
    assertFalse(SchoolImporter.trusted("http://user@218.197.82.133/"));
    assertFalse(SchoolImporter.trusted("file:///218.197.82.133"));
    JSONObject p = payload(new JSONArray().put(row())).put("url", "https://example.org");
    assertThrows(IllegalArgumentException.class, () -> SchoolImporter.parse(p, 20));
    p.put("url", SchoolImporter.LOGIN_URL).put("error", "请重新查询学期");
    assertThrows(IllegalArgumentException.class, () -> SchoolImporter.parse(p, 20));
  }

}
