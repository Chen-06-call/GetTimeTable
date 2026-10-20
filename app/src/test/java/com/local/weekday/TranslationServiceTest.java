package com.local.weekday;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class TranslationServiceTest {
  @Test
  public void joinsTranslatedSegmentsFromGoogleCompatibleResponse() throws Exception {
    assertEquals(
        "Hello world",
        TranslationService.parseGoogleResponse(
            "[[[\"Hello\",\"你好\",null,null,1],[\" world\",\"世界\",null,null,1]],null,\"zh-CN\"]"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsResponseWithoutTranslatedText() throws Exception {
    TranslationService.parseGoogleResponse("[[]]");
  }

  @Test
  public void parsesFallbackProviderResponse() throws Exception {
    assertEquals(
        "Hello",
        TranslationService.parseMyMemoryResponse(
            "{\"responseData\":{\"translatedText\":\"Hello\"},\"responseStatus\":200}"));
  }

  @Test
  public void workingDefaultServiceAvoidsTheUnreachableGoogleDomain() throws Exception {
    List<String> requests = new ArrayList<>();
    assertEquals("你好", TranslationService.translate("hello", "en", "zh-CN", url -> {
      requests.add(url);
      return "{\"responseStatus\":200,\"responseData\":{\"translatedText\":\"你好\"}}";
    }));
    assertEquals(1, requests.size());
    assertTrue(requests.get(0).startsWith("https://api.mymemory.translated.net/"));
    assertTrue(URLDecoder.decode(requests.get(0), "UTF-8").contains("langpair=en|zh-CN"));
  }

  @Test
  public void failedConnectionAutomaticallyUsesBackupAndPreservesDirection() throws Exception {
    List<String> requests = new ArrayList<>();
    assertEquals("hello", TranslationService.translate("你好", "zh-CN", "en", url -> {
      requests.add(url);
      if (url.contains("mymemory")) throw new SocketTimeoutException("timeout");
      return "[[[\"hello\",\"你好\"]]]";
    }));
    assertEquals(2, requests.size());
    assertTrue(requests.get(1).contains("sl=zh-CN&tl=en"));
    assertTrue(URLDecoder.decode(requests.get(1), "UTF-8").contains("q=你好"));
  }

  @Test
  public void quotaMessageIsRejectedInsteadOfDisplayedAsTranslation() throws Exception {
    assertThrows(IOException.class, () -> TranslationService.parseMyMemoryResponse(
        "{\"responseStatus\":429,\"responseData\":{\"translatedText\":\"LIMIT EXCEEDED\"}}"));
    assertThrows(IOException.class, () -> TranslationService.parseMyMemoryResponse(
        "{\"responseStatus\":200,\"quotaFinished\":true,\"responseData\":{\"translatedText\":\"LIMIT\"}}"));
    assertEquals("fallback", TranslationService.translate("word", "en", "zh-CN", url ->
        url.contains("mymemory")
            ? "{\"responseStatus\":429,\"responseData\":{\"translatedText\":\"LIMIT\"}}"
            : "[[[\"fallback\",\"word\"]]]"));
  }

  @Test
  public void bothConnectionsFailWithActionableMessageAndRetainDiagnosticCauses() {
    IOException error = assertThrows(IOException.class,
        () -> TranslationService.translate("hello", "en", "zh-CN", url -> {
          throw new SocketTimeoutException("private-network-address");
        }));
    assertTrue(error.getMessage().contains("应用联网设置"));
    assertFalse(error.getMessage().contains("private-network-address"));
    assertNotNull(error.getCause());
    assertEquals(1, error.getSuppressed().length);
  }

  @Test
  public void chineseAndEmojiChunksStayWithinProviderByteLimitWithoutLosingText() {
    String text = "中文文本😀".repeat(130) + " hello world ".repeat(70);
    List<String> chunks = TranslationService.splitText(text);
    assertTrue(chunks.size() > 1);
    assertEquals(text, String.join("", chunks));
    for (String chunk : chunks) {
      assertTrue(chunk.getBytes(StandardCharsets.UTF_8).length <= 480);
      assertFalse(Character.isLowSurrogate(chunk.charAt(0)));
      assertFalse(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1)));
    }
  }
}
