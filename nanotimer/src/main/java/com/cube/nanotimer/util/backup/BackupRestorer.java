package com.cube.nanotimer.util.backup;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.BackupCounts;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Puts a backup the user has picked in place of what is on the phone: reads it, asks, replaces,
 * restarts. The GUI half of {@link BackupReader}, kept beside it rather than in an activity so the
 * import path stays a picker and a dialog, with no screen of its own.
 *
 * <p>Every word it shows appears at the moment the decision is made. The counts in the file sit
 * beside the counts on the phone, which is the comparison "this replaces everything" needs to mean
 * anything.
 */
public class BackupRestorer {

  private static final String TAG = "[NanoTimer]";
  /** The first bytes of every zip, which is the only thing a backup can be. */
  private static final byte[] ZIP_MAGIC = { 0x50, 0x4B, 0x03, 0x04 };

  private final Activity activity;

  public BackupRestorer(Activity activity) {
    this.activity = activity;
  }

  /**
   * Whether a picked file is worth offering to restore, decided on content: a name and a mime type
   * are both things a provider is free to get wrong. A zip that turns out not to be ours is refused
   * with its own message further in.
   */
  public static boolean looksLikeABackup(Activity activity, Uri uri) {
    InputStream is = null;
    try {
      is = activity.getContentResolver().openInputStream(uri);
      return is != null && startsWithZipMagic(is);
    } catch (IOException e) {
      Log.e(TAG, "Could not read the picked file", e);
      return false;
    } finally {
      close(is);
    }
  }

  /**
   * The four bytes every zip opens with. Separated from the Uri so the one decision that sends a
   * file down the replace-everything path rather than the add-some-solves path can be tested.
   *
   * <p>A stream shorter than the magic is not a zip, which covers an empty file as well.
   */
  static boolean startsWithZipMagic(InputStream is) throws IOException {
    byte[] head = new byte[ZIP_MAGIC.length];
    int read = 0;
    while (read < head.length) {
      int got = is.read(head, read, head.length - read);
      if (got < 0) {
        return false;
      }
      read += got;
    }
    for (int i = 0; i < ZIP_MAGIC.length; i++) {
      if (head[i] != ZIP_MAGIC[i]) {
        return false;
      }
    }
    return true;
  }

  /** Reads the file, then asks, then replaces. Nothing is touched until the user has agreed. */
  public void restoreFrom(final Uri uri) {
    // Cancelable, and a cancel is honoured when the read lands: back has to mean something, and a
    // confirmation appearing after it was pressed reads as the press having been missed.
    final AtomicBoolean cancelled = new AtomicBoolean(false);
    final ProgressDialog progressDialog = showProgress(R.string.restore_reading, true);
    progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        cancelled.set(true);
      }
    });
    new Thread(new Runnable() {
      @Override
      public void run() {
        BackupReader.Inspection inspection = null;
        InputStream is = null;
        try {
          is = activity.getContentResolver().openInputStream(uri);
          if (is != null) {
            inspection = BackupReader.inspect(activity, is);
          }
        } catch (IOException e) {
          Log.e(TAG, "Could not read the picked file", e);
        } finally {
          close(is);
        }
        final BackupReader.Inspection read = inspection;
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            progressDialog.dismiss();
            if (cancelled.get() || activity.isFinishing()) {
              return;
            }
            if (read == null) {
              // Nothing readable came back at all, which to the user is a file we cannot open.
              showRejection(BackupRejection.NOT_A_BACKUP);
            } else if (!read.isUsable()) {
              showRejection(read.getRejection());
            } else {
              loadCountsThenConfirm(uri, read.getManifest());
            }
          }
        });
      }
    }).start();
  }

  /**
   * The phone's own counts, fetched only once a usable backup is in hand. They are what the dialog
   * says is about to be lost, and {@link BackupReader#restore} writes them into the manifest of the
   * pre-restore backup it takes first.
   */
  private void loadCountsThenConfirm(final Uri uri, final BackupManifest manifest) {
    App.INSTANCE.getService().getBackupCounts(new DataCallback<BackupCounts>() {
      @Override
      public void onData(final BackupCounts counts) {
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (!activity.isFinishing()) {
              confirm(uri, manifest, counts);
            }
          }
        });
      }
    });
  }

  private void confirm(final Uri uri, BackupManifest manifest, final BackupCounts phoneCounts) {
    View body = activity.getLayoutInflater().inflate(R.layout.restore_confirm_dialog, null);
    ((TextView) body.findViewById(R.id.tvRestoreMadeOn)).setText(
      activity.getString(R.string.restore_made_on,
        FormatterService.INSTANCE.formatDateTime(manifest.getCreatedAt())));
    ((TextView) body.findViewById(R.id.tvFileContents)).setText(contents(manifest.getCounts()));
    ((TextView) body.findViewById(R.id.tvPhoneContentsNow)).setText(contents(phoneCounts));

    new AlertDialog.Builder(activity, R.style.NanoTimerDangerDialogTheme)
      .setTitle(R.string.restore_confirm_title)
      .setView(body)
      .setPositiveButton(R.string.restore_confirm, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          replace(uri, phoneCounts);
        }
      })
      .setNegativeButton(R.string.cancel, null)
      .show();
  }

  /**
   * Puts the backup in place and restarts. Nothing may touch the service between the two: the
   * connection is closed inside {@code restore}, so a stray query would find a closed handle.
   */
  private void replace(final Uri uri, final BackupCounts phoneCounts) {
    // Not cancelable, and this is the one place where that is right: past the swap there is nothing
    // back could undo, so a cancel would either lie or leave half a database behind.
    final ProgressDialog progressDialog = showProgress(R.string.restore_running, false);
    new Thread(new Runnable() {
      @Override
      public void run() {
        BackupRejection rejection = null;
        boolean failed = false;
        InputStream is = null;
        try {
          is = activity.getContentResolver().openInputStream(uri);
          if (is == null) {
            failed = true;
          } else {
            rejection = BackupReader.restore(activity, is, phoneCounts);
          }
        } catch (IOException e) {
          Log.e(TAG, "Could not restore the backup", e);
          failed = true;
        } finally {
          close(is);
        }

        final BackupRejection refused = rejection;
        final boolean broke = failed;
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (!broke && refused == null) {
              Utils.restartApp(activity.getBaseContext()); // does not return
              return;
            }
            progressDialog.dismiss();
            if (broke) {
              DialogUtils.showOkDialog(activity, activity.getString(R.string.restore_failed),
                activity.getString(R.string.restore_failed_body));
            } else {
              showRejection(refused);
            }
          }
        });
      }
    }).start();
  }

  private void showRejection(BackupRejection rejection) {
    DialogUtils.showOkDialog(activity, activity.getString(R.string.restore_failed),
      activity.getString(messageFor(rejection)));
  }

  /** One message per reason, so a file we cannot use says which kind of file it is. */
  static int messageFor(BackupRejection rejection) {
    switch (rejection) {
      case DAMAGED:
        return R.string.restore_rejected_damaged;
      case NEWER_FORMAT:
        return R.string.restore_rejected_newer_format;
      case NEWER_DB_VERSION:
        return R.string.restore_rejected_newer_db_version;
      case WRONG_APP:
        return R.string.restore_rejected_wrong_app;
      case CORRUPT_DATABASE:
        return R.string.restore_rejected_corrupt_database;
      case NOT_A_BACKUP:
      default:
        return R.string.restore_rejected_not_a_backup;
    }
  }

  private ProgressDialog showProgress(int messageId, boolean cancelable) {
    ProgressDialog dialog = new ProgressDialog(activity);
    dialog.setMessage(activity.getString(messageId));
    dialog.setIndeterminate(true);
    dialog.setCancelable(cancelable);
    dialog.show();
    return dialog;
  }

  private String contents(BackupCounts counts) {
    if (counts == null) {
      return "";
    }
    return activity.getResources().getQuantityString(R.plurals.export_solves_count,
        counts.getSolves(), counts.getSolves())
      + "\n" + activity.getResources().getQuantityString(R.plurals.backup_drills_count,
        counts.getDrills(), counts.getDrills())
      + "\n" + activity.getResources().getQuantityString(R.plurals.backup_solve_types_count,
        counts.getSolveTypes(), counts.getSolveTypes());
  }

  private static void close(InputStream is) {
    if (is != null) {
      try {
        is.close();
      } catch (IOException ignored) {
      }
    }
  }
}
