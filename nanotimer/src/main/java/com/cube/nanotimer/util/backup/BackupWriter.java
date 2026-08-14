package com.cube.nanotimer.util.backup;

import android.content.Context;
import com.cube.nanotimer.services.db.DB;
import com.cube.nanotimer.services.db.DBHelper;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.BackupCounts;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes the whole of a user's Cubench to one zip: the database file as it stands, the preferences
 * worth carrying, and a manifest describing both.
 *
 * <p>The database travels as the SQLite file rather than as rows, so a restore into a later version
 * migrates through the ordinary upgrade chain instead of through a second, barely exercised
 * compatibility layer, and so tables added later are carried without this class being told.
 *
 * <p>Everything here touches the disk and the database; call it off the main thread.
 */
public class BackupWriter {

  private BackupWriter() {
  }

  /**
   * Builds a backup in the cache directory and returns it, so the caller can hand it to the
   * document picker or to the share sheet the way an export is handed over.
   */
  public static File write(Context context, BackupCounts counts) throws IOException {
    File file = new File(context.getCacheDir(), BackupFormat.fileName(new Date()));
    OutputStream os = new FileOutputStream(file);
    try {
      write(context, counts, os);
      os.close();
    } catch (IOException e) {
      try { os.close(); } catch (IOException ignored) { }
      file.delete(); // a half written backup must not be offered as one
      throw e;
    }
    return file;
  }

  static void write(Context context, BackupCounts counts, OutputStream os) throws IOException {
    DBHelper.checkpointWal();

    ZipOutputStream zip = new ZipOutputStream(os);
    putText(zip, BackupFormat.MANIFEST_ENTRY, manifest(context, counts).toJson());
    putDatabase(zip, context.getDatabasePath(DB.DB_NAME));
    putText(zip, BackupFormat.PREFERENCES_ENTRY, BackupPreferences.capture(context).toJson());
    zip.finish();
  }

  private static BackupManifest manifest(Context context, BackupCounts counts) {
    return new BackupManifest(
      BackupManifest.FORMAT,
      System.currentTimeMillis(),
      BackupFormat.appPackage(context),
      BackupFormat.versionCode(context),
      Utils.getAppVersion(context),
      DB.DB_VERSION,
      counts);
  }

  private static void putText(ZipOutputStream zip, String name, String text) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(text.getBytes("UTF-8"));
    zip.closeEntry();
  }

  private static void putDatabase(ZipOutputStream zip, File db) throws IOException {
    if (!db.exists()) {
      throw new IOException("No database to back up");
    }
    zip.putNextEntry(new ZipEntry(BackupFormat.dbEntry(DB.DB_NAME)));
    InputStream is = new FileInputStream(db);
    try {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = is.read(buffer)) != -1) {
        zip.write(buffer, 0, read);
      }
    } finally {
      is.close();
    }
    zip.closeEntry();
  }

}
