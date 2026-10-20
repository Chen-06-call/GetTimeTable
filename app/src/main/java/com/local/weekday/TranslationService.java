package com.local.weekday;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Online Chinese-English translation with a fallback provider. */
public final class TranslationService {
  private TranslationService() {}

  public static String translate(String text, String source, String target) throws Exception {
    return translate(text, source, target, TranslationService::request);
  }

  interface Transport {
    String get(String url) throws Exception;
  }

  static String translate(String text, String source, String target, Transport transport) throws Exception {
    if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("请输入要翻译的内容");
    if (!("en".equals(source) && "zh-CN".equals(target))
        && !("zh-CN".equals(source) && "en".equals(target)))
      throw new IllegalArgumentException("请选择中文与英文互译");
    if (text.length() > 4000) throw new IllegalArgumentException("每次最多翻译 4000 个字符，请分段输入");
    Exception primary;
    try {
      StringBuilder translated = new StringBuilder();
      for (String chunk : splitText(text)) {
        translated.append(parseMyMemoryResponse(transport.get(myMemoryUrl(chunk, source, target))));
        if (Character.isWhitespace(chunk.charAt(chunk.length() - 1)))
          translated.append(chunk.charAt(chunk.length() - 1));
      }
      return translated.toString().trim();
    } catch (Exception e) {
      primary = e;
    }
    try {
      return parseGoogleResponse(transport.get(googleUrl(text, source, target)));
    } catch (Exception fallback) {
      IOException failure = new IOException(
          "翻译服务暂时不可用，请检查 Wi-Fi、移动数据及应用联网设置后重试；免费服务也可能暂时限流。", primary);
      failure.addSuppressed(fallback);
      throw failure;
    }
  }

  private static String googleUrl(String text, String source, String target) throws Exception {
    return
        "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
            + encode(source)
            + "&tl="
            + encode(target)
            + "&dt=t&q="
            + encode(text);
  }

  private static String myMemoryUrl(String text, String source, String target) throws Exception {
    return
        "https://api.mymemory.translated.net/get?q="
            + encode(text)
            + "&langpair="
            + encode(source + "|" + target);
  }

  private static String request(String query) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(query).openConnection();
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(8000);
    connection.setRequestProperty("User-Agent", "Weekday/1.3.1 Android");
    try {
      int code = connection.getResponseCode();
      if (code < 200 || code >= 300) throw new java.io.IOException("HTTP " + code);
      try (InputStream input = connection.getInputStream();
          BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          body.append(line);
          if (body.length() > 256 * 1024) throw new IOException("翻译响应过大");
        }
        return body.toString();
      }
    } finally {
      connection.disconnect();
    }
  }

  private static String encode(String value) throws Exception {
    return URLEncoder.encode(value, "UTF-8");
  }

  static String parseGoogleResponse(String body) throws Exception {
    JSONArray root = new JSONArray(body);
    JSONArray segments = root.getJSONArray(0);
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < segments.length(); i++) {
      JSONArray segment = segments.optJSONArray(i);
      if (segment == null) continue;
      String translated = segment.optString(0, "");
      if (!translated.isEmpty()) result.append(translated);
    }
    if (result.length() == 0) throw new IllegalArgumentException("翻译服务没有返回结果");
    return result.toString();
  }

  static String parseMyMemoryResponse(String body) throws Exception {
    JSONObject root = new JSONObject(body);
    if (root.optInt("responseStatus", 0) != 200 || root.optBoolean("quotaFinished", false))
      throw new IOException("翻译服务暂时限流或额度用尽，请稍后重试");
    JSONObject response = root.optJSONObject("responseData");
    String translated = response == null || response.isNull("translatedText")
        ? "" : response.optString("translatedText", "").trim();
    if (translated.isEmpty()) throw new IllegalArgumentException("翻译服务没有返回结果");
    return android.text.Html.fromHtml(translated.replace("<", "&lt;"),
        android.text.Html.FROM_HTML_MODE_LEGACY).toString();
  }

  // MyMemory accepts up to 500 UTF-8 bytes per request; split on word boundaries when possible.
  static List<String> splitText(String text) {
    List<String> chunks = new ArrayList<>();
    int begin = 0;
    while (begin < text.length()) {
      int end = begin, bytes = 0, boundary = -1;
      while (end < text.length()) {
        int codePoint = text.codePointAt(end);
        int count = codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
        if (bytes + count > 480) break;
        bytes += count;
        end += Character.charCount(codePoint);
        if (Character.isWhitespace(codePoint) || "。！？.!?".indexOf(codePoint) >= 0)
          boundary = end;
      }
      if (end < text.length() && boundary > begin) end = boundary;
      chunks.add(text.substring(begin, end));
      begin = end;
    }
    return chunks;
  }
}
