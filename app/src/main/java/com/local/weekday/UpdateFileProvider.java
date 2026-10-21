package com.local.weekday;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Minimal private provider used to hand the downloaded APK to Android's installer. */
public final class UpdateFileProvider extends ContentProvider {
  @Override public boolean onCreate() { return true; }

  @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }

  @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!"update.apk".equals(uri.getLastPathSegment())) throw new FileNotFoundException("文件不存在");
    return ParcelFileDescriptor.open(
        new File(getContext().getCacheDir(), "weekday-update.apk"),
        ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
    MatrixCursor cursor = new MatrixCursor(new String[] {"_display_name", "_size"});
    File file = new File(getContext().getCacheDir(), "weekday-update.apk");
    cursor.addRow(new Object[] {"weekday-update.apk", file.length()});
    return cursor;
  }

  @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
  @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
  @Override public Uri insert(Uri uri, ContentValues values) { return null; }
}
