package com.cube.nanotimer.util.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.services.db.DB;
import com.cube.nanotimer.vo.BackupCounts;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

/** What a file offered to restore is read as, before anything on the phone is touched. */
public class BackupInspectionTest {

  private static final String PACKAGE = "com.cube.nanotimer";

  @Test
  public void aBackupThisBuildWroteIsUsable() {
    BackupReader.Inspection inspection = inspect(zipWith(manifest(DB.DB_VERSION, PACKAGE)));

    assertTrue(inspection.isUsable());
    assertNull(inspection.getRejection());
    assertNotNull(inspection.getManifest());
    assertEquals(12043, inspection.getManifest().getCounts().getSolves());
  }

  @Test
  public void aZipWithoutAManifestIsNotABackup() {
    Map<String, String> entries = new LinkedHashMap<String, String>();
    entries.put("db/" + DB.DB_NAME, "not really a database");
    assertRejected(BackupRejection.NOT_A_BACKUP, inspect(zip(entries)));
  }

  @Test
  public void somethingThatIsNotAZipIsNotABackup() {
    assertRejected(BackupRejection.NOT_A_BACKUP,
        BackupReader.inspect(PACKAGE, new ByteArrayInputStream("cubetype,solvetype,time".getBytes())));
  }

  @Test
  public void anEmptyFileIsNotABackup() {
    assertRejected(BackupRejection.NOT_A_BACKUP,
        BackupReader.inspect(PACKAGE, new ByteArrayInputStream(new byte[0])));
  }

  @Test
  public void aManifestThatWillNotParseReadsAsDamaged() {
    assertRejected(BackupRejection.DAMAGED, inspect(zipWith("{ this is not json")));
  }

  @Test
  public void aBackupFromANewerBuildIsRefused() {
    assertRejected(BackupRejection.NEWER_DB_VERSION, inspect(zipWith(manifest(DB.DB_VERSION + 1, PACKAGE))));
  }

  @Test
  public void anotherAppsBackupIsRefused() {
    assertRejected(BackupRejection.WRONG_APP, inspect(zipWith(manifest(DB.DB_VERSION, "com.cube.nanotimerpro"))));
  }

  /** The manifest is read without the database being touched, so the entry order cannot matter. */
  @Test
  public void theManifestIsFoundWhereverItSitsInTheZip() {
    Map<String, String> entries = new LinkedHashMap<String, String>();
    entries.put("db/" + DB.DB_NAME, "a database would be here");
    entries.put("preferences.json", "{}");
    entries.put("manifest.json", manifest(DB.DB_VERSION, PACKAGE));

    assertTrue(inspect(zip(entries)).isUsable());
  }

  /** The entry names the tests above spell out are the format, so they are pinned here too. */
  @Test
  public void theEntryNamesAreTheOnesTheFormatSpecifies() {
    assertEquals("manifest.json", BackupFormat.MANIFEST_ENTRY);
    assertEquals("preferences.json", BackupFormat.PREFERENCES_ENTRY);
    assertEquals("db/" + DB.DB_NAME, BackupFormat.dbEntry(DB.DB_NAME));
  }

  private static void assertRejected(BackupRejection expected, BackupReader.Inspection inspection) {
    assertFalse(inspection.isUsable());
    assertEquals(expected, inspection.getRejection());
    assertNull(inspection.getManifest());
  }

  private static BackupReader.Inspection inspect(byte[] file) {
    return BackupReader.inspect(PACKAGE, new ByteArrayInputStream(file));
  }

  private static String manifest(int dbVersion, String appPackage) {
    return new BackupManifest(BackupManifest.FORMAT, 1755043200000L, appPackage, 65, "2.0.0",
        dbVersion, new BackupCounts(12043, 14, 88, 1902)).toJson();
  }

  private static byte[] zipWith(String manifest) {
    Map<String, String> entries = new LinkedHashMap<String, String>();
    entries.put("manifest.json", manifest);
    entries.put("db/" + DB.DB_NAME, "a database would be here");
    entries.put("preferences.json", "{}");
    return zip(entries);
  }

  private static byte[] zip(Map<String, String> entries) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ZipOutputStream zip = new ZipOutputStream(out);
      for (Map.Entry<String, String> entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue().getBytes("UTF-8"));
        zip.closeEntry();
      }
      zip.finish();
      zip.close();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return out.toByteArray();
  }

}
