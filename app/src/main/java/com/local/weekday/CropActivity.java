package com.local.weekday;

import android.app.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;

public class CropActivity extends Activity {
  public static final String EXTRA_PATH = "path";
  CropView crop;

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    try {
      String path = getIntent().getStringExtra(EXTRA_PATH);
      BitmapFactory.Options o = new BitmapFactory.Options();
      o.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(path, o);
      if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("请选择有效的图片");
      o.inSampleSize = 1;
      while (o.outWidth / o.inSampleSize > 2400 || o.outHeight / o.inSampleSize > 2400)
        o.inSampleSize *= 2;
      o.inJustDecodeBounds = false;
      Bitmap bitmap = BitmapFactory.decodeFile(path, o);
      if (bitmap == null) throw new IOException("无法解码图片");
      int orientation = 1;
      try {
        android.media.ExifInterface exif = new android.media.ExifInterface(path);
        orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
      } catch (IOException ignored) {
        // PNGs and other decodable images may have no readable EXIF metadata.
      }
      Matrix rotate = new Matrix();
      switch (orientation) {
        case 2:
          rotate.setScale(-1, 1);
          break;
        case 3:
          rotate.setRotate(180);
          break;
        case 4:
          rotate.setScale(1, -1);
          break;
        case 5:
          rotate.setRotate(90);
          rotate.postScale(-1, 1);
          break;
        case 6:
          rotate.setRotate(90);
          break;
        case 7:
          rotate.setRotate(270);
          rotate.postScale(-1, 1);
          break;
        case 8:
          rotate.setRotate(270);
          break;
      }
      if (orientation != 1)
        bitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), rotate, true);
      LinearLayout root = new LinearLayout(this);
      root.setOrientation(LinearLayout.VERTICAL);
      root.setBackgroundColor(0xff17231c);
      TextView tip = new TextView(this);
      tip.setText("拖动调整位置 · 双指缩放 · 框内为最终背景");
      tip.setTextColor(Color.WHITE);
      tip.setPadding(24, 24, 24, 16);
      root.addView(tip);
      crop = new CropView(bitmap);
      root.addView(crop, new LinearLayout.LayoutParams(-1, 0, 1));
      Button save = new Button(this);
      save.setText("裁剪并使用背景");
      root.addView(save);
      save.setOnClickListener(
          v -> {
            save.setEnabled(false);
            Bitmap result = crop.output();
            new Thread(
                    () -> {
                      try {
                        File temp = new File(getFilesDir(), "background.tmp");
                        try (FileOutputStream out = new FileOutputStream(temp)) {
                          if (!result.compress(Bitmap.CompressFormat.JPEG, 88, out))
                            throw new IOException("图片保存失败");
                          out.getFD().sync();
                        }
                        Files.move(
                            temp.toPath(),
                            new File(getFilesDir(), "background.jpg").toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                        getSharedPreferences("appearance", MODE_PRIVATE)
                            .edit()
                            .putBoolean("customBackground", true)
                            .commit();
                        runOnUiThread(
                            () -> {
                              setResult(RESULT_OK);
                              finish();
                            });
                      } catch (Exception e) {
                        runOnUiThread(
                            () -> {
                              save.setEnabled(true);
                              Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                      }
                    },
                    "crop-save")
                .start();
          });
      setContentView(root);
      root.setOnApplyWindowInsetsListener(
          (v, insets) -> {
            v.setPadding(
                insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
            return insets;
          });
    } catch (Exception e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
      finish();
    }
  }

  class CropView extends View {
    final Bitmap bitmap;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final RectF frame = new RectF();
    float scale, minScale, x, y, lastX, lastY;
    ScaleGestureDetector detector;

    CropView(Bitmap bitmap) {
      super(CropActivity.this);
      this.bitmap = bitmap;
      detector =
          new ScaleGestureDetector(
              getContext(),
              new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                  float old = scale;
                  scale = Math.max(minScale, Math.min(minScale * 6, scale * d.getScaleFactor()));
                  x = d.getFocusX() - (d.getFocusX() - x) * scale / old;
                  y = d.getFocusY() - (d.getFocusY() - y) * scale / old;
                  clamp();
                  invalidate();
                  return true;
                }
              });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
      float ratio =
          getResources().getDisplayMetrics().widthPixels
              / (float) getResources().getDisplayMetrics().heightPixels;
      float fw = w - 36, fh = fw / ratio;
      if (fh > h - 36) {
        fh = h - 36;
        fw = fh * ratio;
      }
      frame.set((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2);
      scale =
          minScale =
              Math.max(frame.width() / bitmap.getWidth(), frame.height() / bitmap.getHeight());
      x = frame.centerX() - bitmap.getWidth() * scale / 2;
      y = frame.centerY() - bitmap.getHeight() * scale / 2;
    }

    void clamp() {
      x = Math.min(frame.left, Math.max(frame.right - bitmap.getWidth() * scale, x));
      y = Math.min(frame.top, Math.max(frame.bottom - bitmap.getHeight() * scale, y));
    }

    @Override
    protected void onDraw(Canvas c) {
      c.drawColor(0xff101c15);
      c.save();
      c.clipRect(frame);
      c.drawBitmap(
          bitmap,
          null,
          new RectF(x, y, x + bitmap.getWidth() * scale, y + bitmap.getHeight() * scale),
          paint);
      c.restore();
      paint.setColor(0xffa4cbb0);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(2);
      c.drawRect(frame, paint);
      paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
      detector.onTouchEvent(e);
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
        lastX = e.getX();
        lastY = e.getY();
      } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE
          && !detector.isInProgress()
          && e.getPointerCount() == 1) {
        x += e.getX() - lastX;
        y += e.getY() - lastY;
        lastX = e.getX();
        lastY = e.getY();
        clamp();
        invalidate();
      } else if (e.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
        int i = e.getActionIndex() == 0 ? 1 : 0;
        lastX = e.getX(i);
        lastY = e.getY(i);
      }
      return true;
    }

    Bitmap output() {
      int w = 1080, h = Math.min(2400, Math.round(w * frame.height() / frame.width()));
      Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(result);
      float factor = w / frame.width();
      c.scale(factor, factor);
      c.translate(-frame.left, -frame.top);
      c.drawBitmap(
          bitmap,
          null,
          new RectF(x, y, x + bitmap.getWidth() * scale, y + bitmap.getHeight() * scale),
          paint);
      return result;
    }
  }
}
