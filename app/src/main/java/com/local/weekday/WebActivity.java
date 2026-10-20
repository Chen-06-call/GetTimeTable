package com.local.weekday;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;

/** Login stays in Android WebView. No password fields, JavaScript bridge, or credential export. */
public class WebActivity extends Activity {
  WebView web;
  Button capture;
  TextView tip;
  String captureScript = "", extractScript = "";
  boolean reading;
  final Handler injection = new Handler(Looper.getMainLooper());
  final Runnable install =
      new Runnable() {
        public void run() {
          if (web == null) return;
          installCapture();
          injection.postDelayed(this, 1000);
        }
      };

  String asset(String name) throws Exception {
    try (java.io.InputStream in = getAssets().open(name)) {
      return new String(
          Repository.readLimited(in, 128 * 1024), java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  void installCapture() {
    boolean supported = web != null && SchoolImporter.trusted(web.getUrl());
    capture.setEnabled(supported && !reading);
    tip.setText(
        supported
            ? "自行登录并完成验证码 → 打开个人课表页面并查询 → 点击一键读取。读取后选择课表保存位置。"
            : "该网址暂未适配自动读取，可下载 CSV / XLSX 后从设置导入。");
    if (supported && !captureScript.isEmpty()) web.evaluateJavascript(captureScript, null);
  }

  void readCourses() {
    if (reading || !SchoolImporter.trusted(web.getUrl())) return;
    reading = true;
    capture.setEnabled(false);
    capture.setText("正在读取…");
    web.evaluateJavascript(
        extractScript,
        value -> {
          if (isFinishing() || isDestroyed()) return;
          try {
            if (!SchoolImporter.trusted(web.getUrl())) throw new Exception("页面已离开教务系统，请返回个人课表重试");
            if (value == null || value.length() > 2 * 1024 * 1024)
              throw new Exception("读取内容过大，请打开个人课表再试");
            Object decoded = new org.json.JSONTokener(value).nextValue();
            if (!(decoded instanceof String)) throw new Exception("读取失败，请等待课表加载完成后重试");
            org.json.JSONObject data = new org.json.JSONObject((String) decoded);
            if (!data.optString("error", "").isEmpty())
              throw new Exception(data.getString("error"));
            int initialCourseCount = data.optJSONArray("entries") == null ? 0 : data.getJSONArray("entries").length();
            if (initialCourseCount == 0)
              throw new Exception("未找到课程。请先打开个人课表页面并查询后再读取。");
            if (!SchoolImporter.trusted(data.getString("url"))) throw new Exception("无效的课表来源");
            // Persist only explicitly selected course fields, never the HTML or login form.
            byte[] bytes = data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            new Thread(
                    () -> {
                      try {
                        // Validate before closing the website so a failed read can be retried there.
                        SchoolImporter.parse(data, getIntent().getIntExtra("termWeeks", 20));
                        java.io.File temp = new java.io.File(getCacheDir(), "school-import.tmp"),
                            target = new java.io.File(getCacheDir(), "school-import.json");
                        try (java.io.FileOutputStream out = new java.io.FileOutputStream(temp)) {
                          out.write(bytes);
                          out.getFD().sync();
                        }
                        java.nio.file.Files.move(
                            temp.toPath(),
                            target.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        int entryCount = data.optJSONArray("entries") == null ? 0 : data.getJSONArray("entries").length();
                        runOnUiThread(
                            () -> {
                              if (isDestroyed() || isFinishing()) return;
                              chooseImportTarget(entryCount);
                            });
                      } catch (Exception e) {
                        runOnUiThread(() -> readError(e.getMessage() == null ? "读取或保存课程失败，请重试" : e.getMessage()));
                      }
                    },
                    "school-import")
                .start();
          } catch (Exception e) {
            readError(e.getMessage());
          }
        });
  }

  void chooseImportTarget(int courseCount) {
    if (isDestroyed() || isFinishing()) return;
    new AlertDialog.Builder(this)
        .setTitle("导入到哪个课表？")
        .setMessage("已读取 " + courseCount + " 条课程。请选择保存位置。")
        .setNegativeButton("取消", (dialog, which) -> resetReader())
        .setNeutralButton("新建课表", (dialog, which) -> askCoursebookName())
        .setPositiveButton("当前课表", (dialog, which) -> finishImport(0))
        .show();
  }

  void askCoursebookName() {
    String termName = getIntent().getStringExtra("termName");
    if (termName == null || termName.trim().isEmpty()) termName = "湖北经济学院课表";
    String defaultName = termName.trim() + " · 新课表";
    EditText name = new EditText(this);
    name.setSingleLine(true);
    name.setHint("例如：2026 春季课表");
    name.setText(defaultName);
    name.selectAll();
    new AlertDialog.Builder(this)
        .setTitle("新建课表")
        .setMessage("请输入课表名称")
        .setView(name)
        .setNegativeButton("取消", (dialog, which) -> resetReader())
        .setPositiveButton("导入", (dialog, which) -> {
          String value = name.getText().toString().trim();
          finishImport(1, value.isEmpty() ? defaultName : value);
        })
        .show();
  }

  void resetReader() {
    reading = false;
    capture.setText("一键读取课表");
    installCapture();
  }

  void finishImport(int mode) {
    finishImport(mode, "");
  }

  void finishImport(int mode, String importName) {
    setResult(
        RESULT_OK,
        new Intent()
            .putExtra("termId", getIntent().getLongExtra("termId", 0))
            .putExtra("importMode", mode)
            .putExtra("importName", importName));
    finish();
  }

  void readError(String message) {
    if (isDestroyed() || isFinishing()) return;
    reading = false;
    capture.setText("一键读取课表");
    installCapture();
    new AlertDialog.Builder(this)
        .setTitle("课表读取提示")
        .setMessage(message)
        .setPositiveButton("知道了", null)
        .show();
  }

  @Override
  public void onCreate(Bundle b) {
    setTheme(
        getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("dark", false)
            ? R.style.AppThemeDark
            : R.style.AppTheme);
    super.onCreate(b);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(0, 36, 0, 24);
    LinearLayout toolbar = new LinearLayout(this);
    Button back = new Button(this);
    back.setText("返回");
    back.setOnClickListener(
        v -> {
          if (web.canGoBack()) web.goBack();
          else finish();
        });
    toolbar.addView(back);
    TextView title = new TextView(this);
    title.setText("学校教务网站");
    title.setTextSize(18);
    title.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.addView(title, new LinearLayout.LayoutParams(0, 60, 1));
    Button menu = new Button(this);
    menu.setText("操作");
    toolbar.addView(menu);
    root.addView(toolbar);
    tip = new TextView(this);
    tip.setText("登录和验证码请自行完成，进入个人课表后读取课程。");
    tip.setTextSize(12);
    tip.setPadding(20, 12, 20, 12);
    root.addView(tip);
    ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    root.addView(progress, new LinearLayout.LayoutParams(-1, 5));
    capture = new Button(this);
    capture.setText("一键读取课表");
    capture.setEnabled(false);
    capture.setOnClickListener(v -> readCourses());
    root.addView(capture, new LinearLayout.LayoutParams(-1, -2));
    try {
      captureScript = asset("hbue-capture.js");
      extractScript = asset("hbue-extract.js");
    } catch (Exception e) {
      Toast.makeText(this, "课表适配脚本加载失败", Toast.LENGTH_LONG).show();
    }
    web = new WebView(this);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setAllowFileAccess(false);
    web.getSettings().setAllowContentAccess(false);
    web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    CookieManager.getInstance().setAcceptCookie(true);
    web.setWebChromeClient(
        new WebChromeClient() {
          @Override
          public void onProgressChanged(WebView v, int n) {
            progress.setProgress(n);
            installCapture();
            progress.setVisibility(n == 100 ? View.GONE : View.VISIBLE);
          }
        });
    web.setWebViewClient(
        new WebViewClient() {
          @Override
          public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
            installCapture();
          }

          @Override
          public void onPageFinished(WebView v, String url) {
            installCapture();
          }

          @Override
          public void onPageCommitVisible(WebView v, String url) {
            installCapture();
          }

          @Override
          public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String scheme = request.getUrl().getScheme();
            if ("https".equals(scheme) || "http".equals(scheme)) return false;
            Toast.makeText(WebActivity.this, "该链接需要其他应用打开", Toast.LENGTH_SHORT).show();
            return true;
          }

          @Override
          public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (r.isForMainFrame())
              Toast.makeText(WebActivity.this, "页面加载失败，请检查网络或网址后重试", Toast.LENGTH_LONG).show();
          }
        });
    web.setDownloadListener(
        (url, agent, disposition, mime, length) ->
            new AlertDialog.Builder(this)
                .setTitle("下载表格？")
                .setMessage("将下载到本机，完成后可从课程表的导入页面选择文件。")
                .setNegativeButton("取消", null)
                .setPositiveButton(
                    "下载",
                    (d, p) -> {
                      try {
                        String scheme = Uri.parse(url).getScheme();
                        if (!"https".equals(scheme) && !"http".equals(scheme))
                          throw new Exception("暂不支持此下载链接，请在学校网站另存表格");
                        String name = URLUtil.guessFileName(url, disposition, mime);
                        android.app.DownloadManager.Request request =
                            new android.app.DownloadManager.Request(Uri.parse(url))
                                .setTitle(name)
                                .setNotificationVisibility(
                                    android.app.DownloadManager.Request
                                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        String cookies = CookieManager.getInstance().getCookie(url);
                        if (cookies != null) request.addRequestHeader("Cookie", cookies);
                        request.addRequestHeader("User-Agent", agent);
                        if (Build.VERSION.SDK_INT >= 29)
                          request.setDestinationInExternalPublicDir(
                              Environment.DIRECTORY_DOWNLOADS, name);
                        else
                          request.setDestinationInExternalFilesDir(
                              this, Environment.DIRECTORY_DOWNLOADS, name);
                        ((android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE))
                            .enqueue(request);
                        Toast.makeText(this, "已开始下载", Toast.LENGTH_SHORT).show();
                      } catch (Exception e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                      }
                    })
                .show());
    menu.setOnClickListener(
        v ->
            new AlertDialog.Builder(this)
                .setItems(
                    new String[] {"刷新页面", "清除登录状态", "在浏览器打开"},
                    (d, p) -> {
                      if (p == 0) web.reload();
                      else if (p == 1)
                        new AlertDialog.Builder(this)
                            .setTitle("清除网站登录状态？")
                            .setNegativeButton("取消", null)
                            .setPositiveButton(
                                "清除",
                                (a, c) ->
                                    CookieManager.getInstance()
                                        .removeAllCookies(
                                            ok -> {
                                              CookieManager.getInstance().flush();
                                              WebStorage.getInstance().deleteAllData();
                                              web.clearCache(true);
                                              web.clearHistory();
                                              web.reload();
                                            }))
                            .show();
                      else
                        try {
                          startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(web.getUrl())));
                        } catch (Exception e) {
                          Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show();
                        }
                    })
                .show());
    root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
          return insets;
        });
    String url = getIntent().getStringExtra("url");
    try {
      java.net.URI u = new java.net.URI(url);
      if (!"https".equalsIgnoreCase(u.getScheme()) && !"http".equalsIgnoreCase(u.getScheme()))
        throw new Exception();
      if (b == null || web.restoreState(b) == null) web.loadUrl(url);
      injection.post(install);
    } catch (Exception e) {
      Toast.makeText(this, "无效的教务网址", Toast.LENGTH_SHORT).show();
      finish();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    if (web != null) web.saveState(out);
  }

  @Override
  public void onBackPressed() {
    if (web != null && web.canGoBack()) web.goBack();
    else super.onBackPressed();
  }

  @Override
  protected void onDestroy() {
    injection.removeCallbacksAndMessages(null);
    if (web != null) {
      web.stopLoading();
      web.setWebViewClient(new WebViewClient());
      web.destroy();
    }
    super.onDestroy();
  }
}
