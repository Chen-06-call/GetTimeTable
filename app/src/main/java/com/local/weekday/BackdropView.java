package com.local.weekday;

import android.content.*;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.*;
import java.io.*;

/** Ten local procedural backgrounds, including four seasonal scenes. */
public class BackdropView extends View {
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  Bitmap image;
  boolean dark;
  int brightness = 100, veil = 35;
  String key = "";

  public BackdropView(Context c) {
    super(c);
  }

  public BackdropView(Context c, AttributeSet a) {
    super(c, a);
  }

  public void configure(SharedPreferences p) {
    dark = p.getBoolean("dark", false);
    brightness = p.getInt("brightness", 100);
    veil = p.getInt("veil", 35);
    File file = new File(getContext().getFilesDir(), "background.jpg");
    int which = p.getInt("background", 5), blur = p.getInt("blur", 0);
    boolean custom = p.getBoolean("customBackground", false) && file.isFile();
    String next = which + ":" + blur + ":" + custom + ":" + file.lastModified();
    if (!next.equals(key)) {
      key = next;
      Bitmap original =
          custom ? BitmapFactory.decodeFile(file.getAbsolutePath()) : wallpaper(which);
      if (original != null) {
        int w = Math.max(12, 320 - blur * 3), h = Math.max(20, w * 16 / 9);
        Bitmap small = Bitmap.createScaledBitmap(original, w, h, true);
        image = Bitmap.createScaledBitmap(small, 360, 640, true);
        if (original != image && original != small) original.recycle();
        if (small != image) small.recycle();
      }
    }
    invalidate();
  }

  static Bitmap wallpaper(int type) {
    Bitmap bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bitmap);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    int[][] colors = {
      {0xfff4dfcb, 0xfff9f1df},
      {0xffcfe7ee, 0xfff4f5ee},
      {0xffb5cbb8, 0xffe4eadb},
      {0xff253f59, 0xff627d94},
      {0xffc9d4d0, 0xffedf0e5},
      {0xfff3f1e8, 0xfffaf7ed},
      {0xffd8ead2, 0xffffe9df},
      {0xffa9dce5, 0xffedf7d9},
      {0xffd4935f, 0xfff2dfb5},
      {0xffb9d3e5, 0xfff0f6fb}
    };
    int[] palette = colors[Math.max(0, Math.min(9, type))];
    p.setShader(new LinearGradient(0, 0, 0, 640, palette, null, Shader.TileMode.CLAMP));
    c.drawRect(0, 0, 360, 640, p);
    p.setShader(null);
    if (type == 0) {
      p.setColor(0x65fff6d6);
      c.drawCircle(280, 110, 60, p);
      hill(c, p, 0x30d29f80, 370, 55);
      hill(c, p, 0x25b4856f, 490, 73);
    } else if (type == 1) {
      p.setColor(0x80ffffff);
      c.drawOval(35, 140, 220, 190, p);
      c.drawOval(180, 270, 400, 320, p);
      hill(c, p, 0x2374a5a5, 540, 70);
    } else if (type == 2) {
      for (int i = 0; i < 12; i++) {
        float x = i * 37 - 25, y = 330 + (i % 3) * 60;
        p.setColor(0x25356b4b);
        Path tree = new Path();
        tree.moveTo(x, y - 110);
        tree.lineTo(x - 40, y + 70);
        tree.lineTo(x + 40, y + 70);
        tree.close();
        c.drawPath(tree, p);
      }
      hill(c, p, 0x30467557, 500, 64);
    } else if (type == 3) {
      java.util.Random r = new java.util.Random(9);
      p.setColor(0x99ffffff);
      for (int i = 0; i < 95; i++)
        c.drawCircle(r.nextInt(360), r.nextInt(500), i % 3 == 0 ? 1.5f : .8f, p);
      p.setColor(0xffe8e7ca);
      c.drawCircle(275, 100, 32, p);
      p.setColor(palette[0]);
      c.drawCircle(289, 89, 29, p);
      hill(c, p, 0x80273b4e, 470, 85);
    } else if (type == 4) {
      hill(c, p, 0x45647c78, 330, 120);
      hill(c, p, 0x456b8a7d, 450, 83);
      hill(c, p, 0x7064806e, 565, 65);
    } else if (type == 5) {
      p.setColor(0x12636f5a);
      p.setStrokeWidth(1);
      for (int y = 0; y < 640; y += 24) c.drawLine(0, y, 360, y, p);
    } else if (type == 6) {
      p.setColor(0x70fff4f1);
      for (int i = 0; i < 12; i++) {
        float x = 30 + (i % 4) * 105, y = 145 + (i / 4) * 95;
        c.drawCircle(x, y, 11, p);
        c.drawCircle(x + 12, y - 5, 8, p);
        c.drawCircle(x - 12, y - 5, 8, p);
      }
      hill(c, p, 0x304e9b72, 530, 76);
    } else if (type == 7) {
      p.setColor(0x72fff2a6);
      c.drawCircle(286, 112, 52, p);
      p.setColor(0x2871b8a5);
      for (int i = 0; i < 5; i++) c.drawOval(-40 + i * 95, 385 + (i % 2) * 28, 150 + i * 95, 420 + (i % 2) * 28, p);
      hill(c, p, 0x2863a9a1, 560, 65);
    } else if (type == 8) {
      java.util.Random r = new java.util.Random(18);
      for (int i = 0; i < 24; i++) {
        float x = r.nextInt(360), y = 170 + r.nextInt(330);
        p.setColor(i % 2 == 0 ? 0x99b85f3c : 0x99d58b43);
        c.save();
        c.rotate((i % 5 - 2) * 12, x, y);
        c.drawOval(x - 6, y - 14, x + 6, y + 14, p);
        c.restore();
      }
      hill(c, p, 0x457f5b45, 535, 72);
    } else {
      p.setColor(0xa8ffffff);
      for (int i = 0; i < 36; i++) {
        float x = (i * 71) % 360, y = 90 + (i * 47) % 400;
        c.drawLine(x - 5, y, x + 5, y, p);
        c.drawLine(x, y - 5, x, y + 5, p);
      }
      hill(c, p, 0x4c6f8fa3, 535, 92);
    }
    return bitmap;
  }

  static void hill(Canvas c, Paint p, int color, float base, float rise) {
    p.setColor(color);
    Path path = new Path();
    path.moveTo(0, base);
    path.cubicTo(85, base - rise, 145, base + rise, 220, base - rise * .5f);
    path.cubicTo(280, base - rise, 330, base - rise * .2f, 360, base - rise);
    path.lineTo(360, 640);
    path.lineTo(0, 640);
    path.close();
    c.drawPath(path, p);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.drawColor(dark ? 0xff17231c : 0xfff6f7f2);
    if (image != null) {
      paint.setColor(Color.WHITE);
      paint.setAlpha(255);
      c.drawBitmap(image, null, new Rect(0, 0, getWidth(), getHeight()), paint);
      paint.setColor(Color.argb((100 - brightness) * 255 / 100, 0, 0, 0));
      c.drawRect(0, 0, getWidth(), getHeight(), paint);
      paint.setColor(
          Color.argb(
              Math.min(245, (dark ? Math.max(65, veil) : veil) * 255 / 100),
              dark ? 23 : 246,
              dark ? 35 : 247,
              dark ? 28 : 242));
      c.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
  }
}
