package com.local.weekday;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.*;

public class ImporterTest {
  @Before
  public void realJavaXmlParser() {
    Importer.parserFactory = org.kxml2.io.KXmlParser::new;
  }

  @Test
  public void csvSupportsBomQuotedCommasNewlinesAndEscapedQuotes() throws Exception {
    Importer.Preview p =
        Importer.csv(
            ("\ufeff课程名称,备注\r\n数学,\"A,B\n他说\"\"你好\"\"\"\r\n").getBytes(StandardCharsets.UTF_8));
    assertEquals("课程名称", p.headers()[0]);
    assertEquals(1, p.rows().size());
    assertEquals("A,B\n他说\"你好\"", p.rows().get(0)[1]);
  }

  @Test
  public void xlsxPreservesSparseColumnsSharedRichTextAndWorkbookOrder() throws Exception {
    Map<String, String> files = new LinkedHashMap<>();
    files.put(
        "xl/workbook.xml",
        "<workbook"
            + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet"
            + " name=\"表\" r:id=\"rId9\"/></sheets></workbook>");
    files.put(
        "xl/_rels/workbook.xml.rels",
        "<Relationships><Relationship Id=\"rId9\""
            + " Target=\"worksheets/sheet9.xml\"/></Relationships>");
    files.put(
        "xl/sharedStrings.xml",
        "<sst><si><r><t>课程</t></r><r><t>名称</t></r></si><si><t>数学 &amp; 应用</t></si></sst>");
    files.put(
        "xl/worksheets/sheet9.xml",
        "<worksheet><sheetData><row><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"C1\""
            + " t=\"inlineStr\"><is><t>教室</t></is></c></row><row><c r=\"A2\" t=\"s\"><v>1</v></c><c"
            + " r=\"C2\" t=\"inlineStr\"><is><t>A101</t></is></c></row></sheetData></worksheet>");
    Importer.Preview p = Importer.xlsx(zip(files));
    assertArrayEquals(new String[] {"课程名称", "", "教室"}, p.headers());
    assertArrayEquals(new String[] {"数学 & 应用", "", "A101"}, p.rows().get(0));
  }

  @Test
  public void rejectsDtdAndUnsupportedArchives() throws Exception {
    try {
      Importer.xml("<!DOCTYPE root SYSTEM 'file:///private'><root/>");
      fail();
    } catch (IOException expected) {
    }
    try {
      Importer.xlsx(zip(Collections.singletonMap("random.xml", "<a/>")));
      fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void exportedXlsxTemplateCanBeImported() throws Exception {
    byte[] data =
        java.nio.file.Files.readAllBytes(new File("src/main/assets/template.xlsx").toPath());
    Importer.Preview p = Importer.xlsx(data);
    assertEquals("课程名称", p.headers()[0]);
    assertEquals(2, p.rows().size());
    assertEquals("高等数学", p.rows().get(0)[0]);
  }

  static byte[] zip(Map<String, String> files) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(bytes)) {
      for (Map.Entry<String, String> e : files.entrySet()) {
        z.putNextEntry(new ZipEntry(e.getKey()));
        z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
      }
    }
    return bytes.toByteArray();
  }
}
