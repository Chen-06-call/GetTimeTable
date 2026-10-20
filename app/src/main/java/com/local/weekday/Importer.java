package com.local.weekday;

import android.util.Xml;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.apache.commons.csv.*;
import org.xmlpull.v1.XmlPullParser;

/** Bounded offline CSV/XLSX reader; formulas and external links are never executed. */
public final class Importer {
  interface ParserFactory {
    XmlPullParser make() throws Exception;
  }

  static ParserFactory parserFactory = Xml::newPullParser;

  public interface Preview {
    String[] headers();

    List<String[]> rows();
  }

  private static final int MAX_ROWS = 5000, MAX_COLS = 128, MAX_XML = 32 * 1024 * 1024;

  public static Preview csv(byte[] data) throws Exception {
    String text = new String(data, StandardCharsets.UTF_8);
    if (text.startsWith("\uFEFF")) text = text.substring(1);
    if (text.indexOf('\uFFFD') >= 0) throw new IOException("CSV 不是有效的 UTF-8，请另存为 UTF-8 CSV 后重试");
    List<String[]> rows = new ArrayList<>();
    try (CSVParser parser =
        CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).get().parse(new StringReader(text))) {
      for (CSVRecord record : parser) {
        if (record.size() > MAX_COLS || rows.size() > MAX_ROWS)
          throw new IOException("表格最多 5000 行、128 列");
        String[] row = new String[record.size()];
        for (int i = 0; i < row.length; i++) row[i] = record.get(i);
        rows.add(row);
      }
    }
    return fromRows(rows);
  }

  static XmlPullParser xml(String text) throws Exception {
    if (text.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")
        || text.toUpperCase(Locale.ROOT).contains("<!ENTITY")) throw new IOException("不支持含外部实体的表格");
    XmlPullParser parser = parserFactory.make();
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
    parser.setInput(new StringReader(text));
    return parser;
  }

  public static Preview xlsx(byte[] data) throws Exception {
    Map<String, String> files = new HashMap<>();
    int total = 0, count = 0;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (++count > 2048) throw new IOException("XLSX 文件内容过多");
        String name = entry.getName();
        boolean relevant = name.endsWith(".xml") || name.endsWith(".rels");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = zip.read(b)) != -1) {
          total += n;
          if (total > MAX_XML) throw new IOException("XLSX 解压内容超过 32 MB");
          if (relevant) out.write(b, 0, n);
        }
        if (relevant) files.put(name, out.toString("UTF-8"));
      }
    }
    if (!files.containsKey("xl/workbook.xml")) throw new IOException("文件不是有效的 XLSX 工作簿");
    String relationship = null;
    XmlPullParser workbook = xml(files.get("xl/workbook.xml"));
    while (workbook.next() != XmlPullParser.END_DOCUMENT)
      if (workbook.getEventType() == XmlPullParser.START_TAG
          && workbook.getName().equals("sheet")) {
        for (int a = 0; a < workbook.getAttributeCount(); a++)
          if (workbook.getAttributeName(a).equals("id")
              || workbook.getAttributeName(a).equals("r:id"))
            relationship = workbook.getAttributeValue(a);
        break;
      }
    String path = "xl/worksheets/sheet1.xml";
    if (relationship != null && files.containsKey("xl/_rels/workbook.xml.rels")) {
      XmlPullParser rel = xml(files.get("xl/_rels/workbook.xml.rels"));
      while (rel.next() != XmlPullParser.END_DOCUMENT)
        if (rel.getEventType() == XmlPullParser.START_TAG
            && rel.getName().equals("Relationship")
            && relationship.equals(rel.getAttributeValue(null, "Id"))) {
          String target = rel.getAttributeValue(null, "Target");
          if (target == null
              || target.contains("..")
              || "External".equals(rel.getAttributeValue(null, "TargetMode")))
            throw new IOException("不支持的工作表引用");
          path = target.startsWith("/") ? target.substring(1) : "xl/" + target;
          break;
        }
    }
    List<String> strings = new ArrayList<>();
    if (files.containsKey("xl/sharedStrings.xml")) {
      XmlPullParser parser = xml(files.get("xl/sharedStrings.xml"));
      StringBuilder text = null;
      while (parser.next() != XmlPullParser.END_DOCUMENT) {
        String name = parser.getName();
        if (parser.getEventType() == XmlPullParser.START_TAG) {
          if ("si".equals(name)) text = new StringBuilder();
          else if ("t".equals(name) && text != null) text.append(parser.nextText());
        } else if (parser.getEventType() == XmlPullParser.END_TAG && "si".equals(name)) {
          if (strings.size() > 100000) throw new IOException("共享字符串过多");
          strings.add(text == null ? "" : text.toString());
          text = null;
        }
      }
    }
    String sheet = files.get(path);
    if (sheet == null) throw new IOException("找不到工作簿的第一个工作表");
    List<String[]> rows = new ArrayList<>();
    XmlPullParser parser = xml(sheet);
    List<String> row = null;
    int column = 0;
    String type = "", value = "";
    boolean inCell = false;
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      String name = parser.getName();
      if (parser.getEventType() == XmlPullParser.START_TAG) {
        if ("row".equals(name)) {
          row = new ArrayList<>();
          if (rows.size() > MAX_ROWS) throw new IOException("表格最多 5000 行");
        } else if ("c".equals(name) && row != null) {
          String ref = parser.getAttributeValue(null, "r");
          column = ref == null ? row.size() : column(ref);
          if (column >= MAX_COLS) throw new IOException("表格最多 128 列");
          type = parser.getAttributeValue(null, "t");
          value = "";
          inCell = true;
        } else if (inCell && ("v".equals(name) || "t".equals(name))) {
          String part = parser.nextText();
          value = "t".equals(name) ? value + part : part;
        }
      } else if (parser.getEventType() == XmlPullParser.END_TAG) {
        if ("c".equals(name) && inCell) {
          if ("s".equals(type)) {
            try {
              value = strings.get(Integer.parseInt(value));
            } catch (Exception e) {
              throw new IOException("XLSX 共享字符串索引无效");
            }
          }
          while (row.size() <= column) row.add("");
          row.set(column, value);
          inCell = false;
        } else if ("row".equals(name) && row != null) {
          boolean empty = true;
          for (String s : row)
            if (!s.trim().isEmpty()) {
              empty = false;
              break;
            }
          if (!empty) rows.add(row.toArray(new String[0]));
          row = null;
        }
      }
    }
    return fromRows(rows);
  }

  private static int column(String ref) throws IOException {
    int n = 0;
    for (int i = 0; i < ref.length(); i++) {
      char c = ref.charAt(i);
      if (c < 'A' || c > 'Z') break;
      n = n * 26 + c - 'A' + 1;
      if (n > MAX_COLS) throw new IOException("表格最多 128 列");
    }
    if (n == 0) throw new IOException("XLSX 单元格地址无效");
    return n - 1;
  }

  private static Preview fromRows(List<String[]> rows) throws IOException {
    if (rows.isEmpty() || rows.get(0).length == 0) throw new IOException("表格为空或缺少表头");
    String[] headers = rows.get(0);
    List<String[]> body = new ArrayList<>(rows.subList(1, rows.size()));
    return new Preview() {
      public String[] headers() {
        return headers;
      }

      public List<String[]> rows() {
        return body;
      }
    };
  }

  private Importer() {}
}
