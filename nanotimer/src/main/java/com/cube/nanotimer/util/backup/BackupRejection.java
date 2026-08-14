package com.cube.nanotimer.util.backup;

/**
 * Why a file offered to restore cannot be used. Each one is a separate message on the screen, so
 * they are kept apart rather than folded into one "bad file".
 */
public enum BackupRejection {

  /** Not a zip, or a zip without a manifest in it: whatever it is, Cubench did not write it. */
  NOT_A_BACKUP,
  /** A manifest that is there but unreadable, which reads to the user the same way. */
  DAMAGED,
  /** A container layout this build does not know. Only a newer Cubench can have written it. */
  NEWER_FORMAT,
  /** A database schema newer than this build's. Restoring it would leave the app unable to open. */
  NEWER_DB_VERSION,
  /** Another app's backup: the pro unlocker's, or a file renamed to look like ours. */
  WRONG_APP,
  /** The manifest is fine but the database inside it is not: SQLite refuses to vouch for it. */
  CORRUPT_DATABASE

}
