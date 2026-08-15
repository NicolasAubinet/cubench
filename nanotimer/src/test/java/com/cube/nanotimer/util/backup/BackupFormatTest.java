package com.cube.nanotimer.util.backup;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

/**
 * The two things a backup file says about itself before it is opened: who wrote it, and when.
 *
 * <p>The name is what the user picks in a file manager a year later, and the package is what
 * decides whether a file is ours at all, so both are pinned rather than left to the writer.
 */
public class BackupFormatTest {

  @Test
  public void aDebugBuildsBackupIsTheSameAppsAsAReleaseBuilds() {
    assertEquals("com.cube.nanotimer", BackupFormat.stripDebugSuffix("com.cube.nanotimer.debug"));
  }

  @Test
  public void aReleaseBuildsPackageIsLeftAlone() {
    assertEquals("com.cube.nanotimer", BackupFormat.stripDebugSuffix("com.cube.nanotimer"));
  }

  /** Only the end of the name is a build suffix. A package that merely contains it is not one. */
  @Test
  public void theSuffixIsOnlyStrippedFromTheEnd() {
    assertEquals("com.cube.nanotimer.debug.tool",
      BackupFormat.stripDebugSuffix("com.cube.nanotimer.debug.tool"));
  }

  /** Another app's backup has to stay another app's, or the wrong-app refusal never fires. */
  @Test
  public void anotherAppsPackageIsNotTurnedIntoOurs() {
    assertEquals("com.cube.nanotimerpro",
      BackupFormat.stripDebugSuffix("com.cube.nanotimerpro"));
  }

  @Test
  public void theFileIsNamedForTheDayItWasTaken() {
    assertEquals("cubench-backup-2026-08-15.zip", BackupFormat.fileName(dayOf(2026, 8, 15)));
  }

  /** Zero padded, so a folder of backups sorts by date when it is sorted by name. */
  @Test
  public void aSingleDigitMonthAndDayArePadded() {
    assertEquals("cubench-backup-2027-01-09.zip", BackupFormat.fileName(dayOf(2027, 1, 9)));
  }

  /** The name is the same on every phone: no Arabic-Indic digits, no other calendar's year. */
  @Test
  public void theNameDoesNotFollowTheDevicesLocale() {
    Date day = dayOf(2026, 8, 15);
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("ar-SA"));
      assertEquals("cubench-backup-2026-08-15.zip", BackupFormat.fileName(day));
    } finally {
      Locale.setDefault(previous);
    }
  }

  private static Date dayOf(int year, int month, int day) {
    Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
    calendar.clear();
    calendar.set(year, month - 1, day, 12, 0, 0);
    return calendar.getTime();
  }

}
