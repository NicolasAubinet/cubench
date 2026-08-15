package com.cube.nanotimer.util.backup;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.util.Log;
import com.cube.nanotimer.services.db.DB;
import com.cube.nanotimer.services.db.DBHelper;
import com.cube.nanotimer.vo.BackupCounts;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads a backup back in: first to say what is in it, then, once the user has agreed, to put it in
 * place of what is on the phone.
 *
 * <p>A restore replaces, it never merges. There is no answer to an id colliding across the solve,
 * session and drill tables that is better than the one the user asked for, which is the other
 * phone's Cubench exactly as it was.
 *
 * <p>Both entry points read a stream to the end, so a caller working from a document Uri opens it
 * once to inspect and again to restore.
 */
public class BackupReader {

  private static final String TAG = "[NanoTimer]";
  private static final String INTEGRITY_OK = "ok";
  private static final String PRE_RESTORE_NAME = "cubench-backup-before-restore.zip";

  private BackupReader() {
  }

  /** What a file says it is, or why it cannot be used. */
  public static class Inspection {
    private final BackupManifest manifest;
    private final BackupRejection rejection;

    private Inspection(BackupManifest manifest, BackupRejection rejection) {
      this.manifest = manifest;
      this.rejection = rejection;
    }

    /** Null when the file was rejected. */
    public BackupManifest getManifest() {
      return manifest;
    }

    /** Null when the file can be restored. */
    public BackupRejection getRejection() {
      return rejection;
    }

    public boolean isUsable() {
      return rejection == null;
    }
  }

  public static Inspection inspect(Context context, InputStream is) {
    return inspect(BackupFormat.appPackage(context), is);
  }

  // Takes the package rather than the Context so the rejections can be tested without a device.
  static Inspection inspect(String appPackage, InputStream is) {
    BackupManifest manifest;
    try {
      manifest = BackupManifest.parse(readEntry(is, BackupFormat.MANIFEST_ENTRY));
    } catch (IOException e) {
      return new Inspection(null, BackupRejection.NOT_A_BACKUP);
    }
    if (manifest == null) {
      return new Inspection(null, BackupRejection.DAMAGED);
    }
    BackupRejection rejection = manifest.reject(DB.DB_VERSION, appPackage);
    return new Inspection(rejection == null ? manifest : null, rejection);
  }

  /**
   * Puts the backup in place, and returns null once it has. A rejection means nothing was touched.
   *
   * <p>{@code currentCounts} describe what is being replaced; they go into the manifest of the
   * pre-restore backup this writes first, so a file picked by mistake can be undone.
   *
   * <p>The order matters twice over. The database is validated before anything on the phone is
   * written, so a bad file costs nothing. The preferences are written before the database is put in
   * place, because the next open is where the upgrade chain runs and one of its scripts reads
   * preferences: a migrating backup has to find its own, not this device's.
   *
   * <p>Nothing here reopens the database. The caller restarts the process, which is what makes the
   * swap safe, and gives the migration a clean first open.
   */
  public static BackupRejection restore(Context context, InputStream is, BackupCounts currentCounts)
      throws IOException {
    File dbFile = context.getDatabasePath(DB.DB_NAME);
    File staged = new File(dbFile.getParentFile(), DB.DB_NAME + ".restoring");

    boolean placed = false;
    try {
      BackupManifest manifest = null;
      PreferencesSnapshot preferences = null;
      boolean gotDatabase = false;

      ZipInputStream zip = new ZipInputStream(is);
      try {
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
          String name = entry.getName();
          if (BackupFormat.MANIFEST_ENTRY.equals(name)) {
            manifest = BackupManifest.parse(readFully(zip));
          } else if (BackupFormat.PREFERENCES_ENTRY.equals(name)) {
            preferences = PreferencesSnapshot.parse(readFully(zip));
          } else if (BackupFormat.dbEntry(DB.DB_NAME).equals(name)) {
            copyTo(zip, staged);
            gotDatabase = true;
          }
        }
      } catch (StagingFailure e) {
        throw e.getFailure(); // our disk, not their file: do not blame what they picked
      } catch (IOException e) {
        return BackupRejection.NOT_A_BACKUP;
      } finally {
        closeQuietly(zip);
      }

      BackupRejection rejection = check(context, manifest, gotDatabase, preferences, staged);
      if (rejection != null) {
        return rejection;
      }

      savePreRestoreBackup(context, currentCounts);
      BackupPreferences.apply(context, preferences);

      DBHelper.closeConnection();
      swapIn(staged, dbFile);
      placed = true;
      return null;
    } finally {
      if (!placed) {
        staged.delete(); // a staged copy is the size of the whole database; never leave one behind
      }
    }
  }

  private static BackupRejection check(Context context, BackupManifest manifest,
      boolean gotDatabase, PreferencesSnapshot preferences, File staged) {
    if (manifest == null) {
      return BackupRejection.NOT_A_BACKUP;
    }
    // The writer always emits all three. A backup short of one is damaged, and restoring the
    // database while quietly keeping this phone's settings is the wrong half of what was asked.
    if (!gotDatabase || preferences == null) {
      return BackupRejection.DAMAGED;
    }
    BackupRejection rejection = manifest.reject(DB.DB_VERSION, BackupFormat.appPackage(context));
    if (rejection != null) {
      return rejection;
    }
    return isIntact(staged) ? null : BackupRejection.CORRUPT_DATABASE;
  }

  /** Asks SQLite to vouch for the file before anything on the phone is replaced with it. */
  private static boolean isIntact(File file) {
    SQLiteDatabase db = null;
    try {
      db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
      Cursor cursor = db.rawQuery("PRAGMA integrity_check", null);
      if (cursor == null) {
        return false;
      }
      try {
        return cursor.moveToFirst() && INTEGRITY_OK.equalsIgnoreCase(cursor.getString(0));
      } finally {
        cursor.close();
      }
    } catch (SQLiteException e) {
      Log.e(TAG, "Backup database will not open", e);
      return false;
    } finally {
      if (db != null) {
        db.close();
      }
    }
  }

  /**
   * The backup taken before the last restore, or null where there is none to go back to. The undo
   * reads it from here rather than through the picker, which cannot reach the app's own folder on
   * Android 11 and up.
   */
  public static File preRestoreBackup(Context context) {
    File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
    if (dir == null) {
      return null;
    }
    File file = new File(dir, PRE_RESTORE_NAME);
    return file.isFile() ? file : null;
  }

  /** A copy of what is about to be replaced, so the wrong file picked in the dialog is undoable. */
  private static void savePreRestoreBackup(Context context, BackupCounts currentCounts) {
    File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
    if (dir == null) {
      Log.w(TAG, "No app storage for the pre-restore backup");
      return; // no safety net, but the restore the user asked for still goes ahead
    }
    OutputStream os = null;
    try {
      os = new FileOutputStream(new File(dir, PRE_RESTORE_NAME));
      BackupWriter.write(context, currentCounts, os);
    } catch (IOException e) {
      Log.e(TAG, "Could not write the pre-restore backup", e);
    } finally {
      closeQuietly(os);
    }
  }

  /**
   * Puts the staged file in place of the live one, within the databases directory so the move is a
   * rename and not a copy, and clears the companions of the database being replaced.
   *
   * <p>The old database is moved aside rather than deleted, and put back if the rename fails. A
   * delete followed by a failed rename would lose it outright, which is the one outcome a restore
   * must not be able to produce.
   */
  private static void swapIn(File staged, File dbFile) throws IOException {
    new File(dbFile.getPath() + "-wal").delete();
    new File(dbFile.getPath() + "-shm").delete();
    new File(dbFile.getPath() + "-journal").delete();

    File aside = new File(dbFile.getPath() + ".replaced");
    aside.delete();
    if (dbFile.exists() && !dbFile.renameTo(aside)) {
      throw new IOException("Could not move the current database aside");
    }
    if (!staged.renameTo(dbFile)) {
      aside.renameTo(dbFile);
      throw new IOException("Could not put the restored database in place");
    }
    aside.delete();
  }

  private static String readEntry(InputStream is, String wanted) throws IOException {
    ZipInputStream zip = new ZipInputStream(is);
    try {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (wanted.equals(entry.getName())) {
          return readFully(zip);
        }
      }
    } finally {
      closeQuietly(zip);
    }
    throw new IOException("No " + wanted + " in the file");
  }

  private static String readFully(InputStream is) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int read;
    while ((read = is.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    return out.toString("UTF-8");
  }

  /**
   * Stages the database out of the zip, telling a failure to write from a failure to read.
   *
   * <p>Both are {@code IOException} and they mean opposite things: one is the phone running out of
   * room, the other is the file not being a backup. Reporting the first as the second sends the
   * user off to delete a file that was never at fault.
   */
  private static void copyTo(InputStream is, File file) throws IOException {
    OutputStream os;
    try {
      os = new FileOutputStream(file);
    } catch (IOException e) {
      throw new StagingFailure(e);
    }
    try {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = is.read(buffer)) != -1) {
        try {
          os.write(buffer, 0, read);
        } catch (IOException e) {
          throw new StagingFailure(e);
        }
      }
      try {
        os.close();
      } catch (IOException e) {
        throw new StagingFailure(e); // a buffered write can only fail here
      }
    } finally {
      closeQuietly(os);
    }
  }

  /** Carries a write failure out through a loop whose read failures mean something else. */
  private static class StagingFailure extends IOException {
    private final IOException failure;

    private StagingFailure(IOException failure) {
      super(failure);
      this.failure = failure;
    }

    private IOException getFailure() {
      return failure;
    }
  }

  private static void closeQuietly(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException ignored) {
      }
    }
  }

}
