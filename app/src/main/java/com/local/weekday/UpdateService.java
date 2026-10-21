package com.local.weekday;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import org.json.*;

/** Checks the GitHub release manifest and downloads a verified APK. */
public final class UpdateService {
  public static final String RELEASE_MANIFEST_URL =
      "https://github.com/Chen-06-call/GetTimeTable/releases/latest/download/version.json";
  public static final String RAW_MANIFEST_URL =
      "https://raw.githubusercontent.com/Chen-06-call/GetTimeTable/main/version.json";
  private static final int CONNECT_TIMEOUT_MS = 8000;
  private static final int READ_TIMEOUT_MS = 15000;
  private static final int MAX_MANIFEST_BYTES = 256 * 1024;
  private static final int MAX_APK_BYTES = 100 * 1024 * 1024;

  public interface CheckCallback {
    void complete(Manifest manifest, Exception error);
  }

  public interface DownloadCallback {
    void progress(long downloaded, long total);
    void complete(File apk, Exception error);
  }

  public static final class Manifest {
    public final String name, versionName, downloadUrl, sha256, releaseDate;
    public final int versionCode;
    public final String[] releaseNotes;

    Manifest(JSONObject root) throws Exception {
      name = root.optString("name", "一周 · Weekday");
      versionName = root.getString("versionName");
      versionCode = root.getInt("versionCode");
      downloadUrl = root.getString("downloadUrl");
      sha256 = root.optString("sha256", "").trim().toUpperCase(java.util.Locale.US);
      releaseDate = root.optString("releaseDate", "");
      JSONArray notes = root.optJSONArray("releaseNotes");
      releaseNotes = new String[notes == null ? 0 : notes.length()];
      for (int i = 0; i < releaseNotes.length; i++) releaseNotes[i] = notes.optString(i);
      validateUrl(downloadUrl);
      if (versionCode < 1 || versionName.trim().isEmpty()) throw new IOException("版本信息无效");
      if (!sha256.isEmpty() && !sha256.matches("[0-9A-F]{64}"))
        throw new IOException("版本校验值无效");
    }

    public String notes() {
      StringBuilder out = new StringBuilder();
      for (String note : releaseNotes) {
        if (note == null || note.trim().isEmpty()) continue;
        if (out.length() > 0) out.append('\n');
        out.append("· ").append(note.trim());
      }
      return out.toString();
    }
  }

  private UpdateService() {}

  public static void check(CheckCallback callback) {
    new Thread(
            () -> {
              Exception first = null;
              try {
                callback.complete(fetch(RELEASE_MANIFEST_URL), null);
                return;
              } catch (Exception e) {
                first = e;
              }
              try {
                callback.complete(fetch(RAW_MANIFEST_URL), null);
              } catch (Exception fallback) {
                String releaseMessage = first == null ? "未知错误" : message(first);
                String rawMessage = message(fallback);
                callback.complete(
                    null,
                    new IOException(
                        "GitHub Release 清单："
                            + releaseMessage
                            + "；仓库 version.json："
                            + rawMessage));
              }
            },
            "version-check")
        .start();
  }

  static Manifest fetch(String endpoint) throws Exception {
    HttpURLConnection connection = open(endpoint);
    try {
      if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300)
        throw new IOException("版本服务返回 HTTP " + connection.getResponseCode());
      byte[] data = readLimited(connection.getInputStream(), MAX_MANIFEST_BYTES);
      return new Manifest(new JSONObject(new String(data, java.nio.charset.StandardCharsets.UTF_8)));
    } finally {
      connection.disconnect();
    }
  }

  public static boolean isNewer(Manifest manifest, int currentCode) {
    return manifest != null && manifest.versionCode > currentCode;
  }

  public static void download(Context context, Manifest manifest, DownloadCallback callback) {
    new Thread(
            () -> {
              File temp = new File(context.getCacheDir(), "weekday-update.apk.download");
              File target = new File(context.getCacheDir(), "weekday-update.apk");
              Exception error = null;
              try {
                HttpURLConnection connection = open(manifest.downloadUrl);
                try {
                  if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300)
                    throw new IOException("下载失败，HTTP " + connection.getResponseCode());
                  long total = connection.getContentLengthLong();
                  try (InputStream in = connection.getInputStream();
                      OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                    byte[] buffer = new byte[8192];
                    long downloaded = 0;
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                      downloaded += n;
                      if (downloaded > MAX_APK_BYTES) throw new IOException("安装包过大");
                      out.write(buffer, 0, n);
                      callback.progress(downloaded, total);
                    }
                  }
                } finally {
                  connection.disconnect();
                }
                if (!manifest.sha256.isEmpty() && !manifest.sha256.equals(sha256(temp)))
                  throw new IOException("安装包校验失败，请重新下载");
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                callback.complete(target, null);
                return;
              } catch (Exception e) {
                error = e;
                try {
                  Files.deleteIfExists(temp.toPath());
                } catch (Exception ignored) {
                }
              }
              callback.complete(null, error);
            },
            "version-download")
        .start();
  }

  static HttpURLConnection open(String endpoint) throws Exception {
    URL url = new URL(endpoint);
    if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("更新地址必须使用 HTTPS");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    connection.setInstanceFollowRedirects(true);
    connection.setRequestProperty("Accept", "application/json, application/octet-stream");
    connection.setRequestProperty("User-Agent", "Weekday-Android-Updater");
    return connection;
  }

  static void validateUrl(String endpoint) throws Exception {
    URL url = new URL(endpoint);
    if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost() == null)
      throw new IOException("更新地址无效");
    String host = url.getHost().toLowerCase(java.util.Locale.US);
    if (!(host.equals("github.com")
        || host.endsWith(".github.com")
        || host.equals("raw.githubusercontent.com")))
      throw new IOException("更新地址不是受支持的 GitHub 地址");
  }

  static String message(Exception error) {
    String value = error == null ? "未知错误" : error.getMessage();
    return value == null || value.trim().isEmpty() ? error.getClass().getSimpleName() : value;
  }

  static byte[] readLimited(InputStream input, int limit) throws IOException {
    try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = in.read(buffer)) != -1) {
        if (out.size() + n > limit) throw new IOException("版本信息过大");
        out.write(buffer, 0, n);
      }
      return out.toByteArray();
    }
  }

  static String sha256(File file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
    }
    StringBuilder out = new StringBuilder(64);
    for (byte b : digest.digest()) out.append(String.format(java.util.Locale.US, "%02X", b));
    return out.toString();
  }
}
