package com.local.weekday;

import java.net.URI;
import java.util.*;
import com.local.weekday.Models.*;
import java.util.regex.*;
import org.json.*;

/** Hubei University of Economics / ZF adapter. It consumes only explicit course fields. */
public final class SchoolImporter {
  public static final String LOGIN_URL = "http://218.197.82.133/xtgl/login_slogin.html";
  static final String[] HEADERS = {"课程名称", "星期", "开始节次", "结束节次", "周次", "教师", "教室", "备注"};

  public static boolean trusted(String url) {
    try {
      URI u = URI.create(url);
      return ("http".equals(u.getScheme()) || "https".equals(u.getScheme()))
          && "218.197.82.133".equals(u.getHost())
          && (u.getPort() == -1 || u.getPort() == 80 || u.getPort() == 443)
          && u.getUserInfo() == null;
    } catch (Exception e) {
      return false;
    }
  }

  public static class Result {
    public final List<String[]> rows = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();
    public String schoolTerm = "";

    public Importer.Preview preview() {
      return new Importer.Preview() {
        public String[] headers() {
          return HEADERS.clone();
        }

        public List<String[]> rows() {
          return rows;
        }
      };
    }
  }

  public static Result parse(JSONObject payload, int total) throws Exception {
    if (!trusted(payload.getString("url")))
      throw new IllegalArgumentException("当前页面不是湖北经济学院教务系统，请返回学校入口");
    if (!payload.optString("error", "").isEmpty())
      throw new IllegalArgumentException(payload.getString("error"));
    JSONArray entries = payload.optJSONArray("entries");
    if (entries == null) entries = new JSONArray();
    if (entries.length() > 5000) throw new IllegalArgumentException("课程条目过多，请确认打开的是个人课表");
    Result result = new Result();
    result.schoolTerm = payload.optString("term", "");
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < entries.length(); i++)
      try {
        JSONObject row = entries.getJSONObject(i);
        String name = row.optString("name", "").trim(), day = row.optString("day", "").trim();
        if (name.isEmpty()) throw new Exception("缺少课程名称");
        int d = Integer.parseInt(day);
        if (d < 1 || d > 7) throw new Exception("星期无效");
        String weeks = normalizeWeeks(row.optString("weeks", ""), total);
        List<int[]> sections = sections(row.optString("sections", ""));
        for (int[] span : sections) {
          String[] data = {
            name,
            String.valueOf(d),
            String.valueOf(span[0]),
            String.valueOf(span[1]),
            weeks,
            row.optString("teacher", ""),
            row.optString("room", ""),
            row.optString("note", "")
          };
          String signature = Arrays.toString(data);
          if (seen.add(signature)) result.rows.add(data);
        }
      } catch (Exception e) {
        result.errors.add("第 " + (i + 1) + " 条：" + e.getMessage());
      }
    if (result.rows.isEmpty())
      throw new IllegalArgumentException(
          "没有识别到可导入的课程。请先登录并打开个人课表页面，查询后再读取。"
              + (result.errors.isEmpty()
                  ? ""
                  : "\n"
                      + String.join(
                          "\n", result.errors.subList(0, Math.min(8, result.errors.size())))));
    return result;
  }

  static String normalizeWeeks(String raw, int total) {
    String v =
        raw.replaceAll("[\\s　]", "")
            .replace('（', '(')
            .replace('）', ')')
            .replace('，', ',')
            .replace('、', ',')
            .replace('～', '-')
            .replace('—', '-')
            .replace('－', '-')
            .replace("周次:", "")
            .replace("周次：", "");
    if (v.isEmpty()) throw new IllegalArgumentException("缺少周次，不能默认导入整个学期");
    if (v.equals("每周")) return CalendarEngine.weekLabel(CalendarEngine.allWeeks(total), total);
    int mask = 0;
    for (String part : v.split("[,;；]")) {
      boolean odd = part.contains("单"), even = part.contains("双");
      if (odd && even) throw new IllegalArgumentException("单双周描述不明确");
      String range = part.replaceAll("[()单双周第]", "");
      int m;
      if (range.isEmpty() && (odd || even)) m = CalendarEngine.allWeeks(total);
      else m = CalendarEngine.parseWeeks(range, total);
      for (int w = 1; w <= total; w++)
        if (CalendarEngine.hasWeek(m, w) && (!odd || w % 2 == 1) && (!even || w % 2 == 0))
          mask |= 1 << (w - 1);
    }
    if (mask == 0) throw new IllegalArgumentException("周次没有有效教学周");
    return CalendarEngine.weekLabel(mask, total);
  }

  static List<int[]> sections(String raw) {
    String v =
        raw.replaceAll("[\\s第节次()（）]", "")
            .replace('，', ',')
            .replace('、', ',')
            .replace('～', '-')
            .replace('—', '-')
            .replace('－', '-');
    SortedSet<Integer> numbers = new TreeSet<>();
    for (String part : v.split(",")) {
      if (!part.matches("\\d{1,2}(-\\d{1,2})?"))
        throw new IllegalArgumentException("无法识别节次：" + raw);
      String[] pair = part.split("-");
      int a = Integer.parseInt(pair[0]), b = pair.length == 1 ? a : Integer.parseInt(pair[1]);
      if (a < 1 || b < a || b > 24) throw new IllegalArgumentException("节次须为 1–24");
      for (int p = a; p <= b; p++) numbers.add(p);
    }
    List<int[]> spans = new ArrayList<>();
    for (int n : numbers) {
      if (!spans.isEmpty() && spans.get(spans.size() - 1)[1] + 1 == n)
        spans.get(spans.size() - 1)[1] = n;
      else spans.add(new int[] {n, n});
    }
    return spans;
  }

  private SchoolImporter() {}
}
