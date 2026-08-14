package com.cube.nanotimer.util.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.cube.nanotimer.vo.BackupCounts;
import org.junit.Test;

public class BackupManifestTest {

  private static final String PACKAGE = "com.cube.nanotimer";
  private static final int DB_VERSION = 28;

  @Test
  public void aManifestComesBackAsItWentIn() {
    BackupManifest written = new BackupManifest(BackupManifest.FORMAT, 1755043200000L, PACKAGE, 65,
        "2.0.0", DB_VERSION, new BackupCounts(12043, 14, 88, 1902));

    BackupManifest read = BackupManifest.parse(written.toJson());

    assertNotNull(read);
    assertEquals(BackupManifest.FORMAT, read.getFormat());
    assertEquals(1755043200000L, read.getCreatedAt());
    assertEquals(PACKAGE, read.getAppPackage());
    assertEquals(65, read.getVersionCode());
    assertEquals("2.0.0", read.getVersionName());
    assertEquals(DB_VERSION, read.getDbVersion());
    assertEquals(12043, read.getCounts().getSolves());
    assertEquals(14, read.getCounts().getSolveTypes());
    assertEquals(88, read.getCounts().getDrills());
    assertEquals(1902, read.getCounts().getDrillReps());
  }

  @Test
  public void aBackupFromThisBuildIsAccepted() {
    assertNull(manifest(BackupManifest.FORMAT, DB_VERSION, PACKAGE).reject(DB_VERSION, PACKAGE));
  }

  /** The ordinary case: it migrates on the first open, the same way an app update does. */
  @Test
  public void anOlderSchemaIsAccepted() {
    assertNull(manifest(BackupManifest.FORMAT, 21, PACKAGE).reject(DB_VERSION, PACKAGE));
  }

  /**
   * There is no onDowngrade override, so an unrefused newer schema throws at the next open and the
   * app cannot start at all.
   */
  @Test
  public void aNewerSchemaIsRefused() {
    assertEquals(BackupRejection.NEWER_DB_VERSION,
        manifest(BackupManifest.FORMAT, DB_VERSION + 1, PACKAGE).reject(DB_VERSION, PACKAGE));
  }

  @Test
  public void aNewerContainerIsRefused() {
    assertEquals(BackupRejection.NEWER_FORMAT,
        manifest(BackupManifest.FORMAT + 1, DB_VERSION, PACKAGE).reject(DB_VERSION, PACKAGE));
  }

  @Test
  public void anotherAppsBackupIsRefused() {
    assertEquals(BackupRejection.WRONG_APP,
        manifest(BackupManifest.FORMAT, DB_VERSION, "com.cube.nanotimerpro").reject(DB_VERSION, PACKAGE));
  }

  /** A container we cannot read is reported as that, not as a schema or an app we do not know. */
  @Test
  public void aNewerContainerIsRefusedBeforeAnythingElseInItIsBelieved() {
    assertEquals(BackupRejection.NEWER_FORMAT,
        manifest(BackupManifest.FORMAT + 1, DB_VERSION + 1, "com.something.else").reject(DB_VERSION, PACKAGE));
  }

  @Test
  public void somethingThatIsNotAManifestReadsAsNothing() {
    assertNull(BackupManifest.parse("not json"));
    assertNull(BackupManifest.parse(null));
    assertNull(BackupManifest.parse("{}"));
  }

  /** Half a manifest is not one: without an app or a schema there is nothing to check against. */
  @Test
  public void aManifestMissingItsSectionsReadsAsNothing() {
    assertNull(BackupManifest.parse("{\"format\":1,\"dbVersion\":28}"));
    assertNull(BackupManifest.parse("{\"format\":1,\"app\":{\"package\":\"com.cube.nanotimer\"}}"));
  }

  private static BackupManifest manifest(int format, int dbVersion, String appPackage) {
    return new BackupManifest(format, 0, appPackage, 65, "2.0.0", dbVersion,
        new BackupCounts(0, 0, 0, 0));
  }

}
