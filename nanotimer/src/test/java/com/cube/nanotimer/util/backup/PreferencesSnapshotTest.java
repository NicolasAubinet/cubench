package com.cube.nanotimer.util.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.AppLaunchStats;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class PreferencesSnapshotTest {

  @Test
  public void everyTypeComesBackAsTheTypeItWentInAs() {
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("a_boolean", Boolean.TRUE);
    values.put("an_int", Integer.valueOf(42));
    values.put("a_long", Long.valueOf(1755043200000L));
    values.put("a_float", Float.valueOf(1.5f));
    values.put("a_string", "pll_ga");
    values.put("a_set", new LinkedHashSet<String>(Arrays.asList("oll_21", "oll_22")));

    Map<String, Object> back = roundTrip(BackupScope.DEFAULT, values).get(BackupScope.DEFAULT);

    assertEquals(Boolean.TRUE, back.get("a_boolean"));
    assertEquals(Integer.valueOf(42), back.get("an_int"));
    assertEquals(Long.valueOf(1755043200000L), back.get("a_long"));
    assertEquals(Float.valueOf(1.5f), back.get("a_float"));
    assertEquals("pll_ga", back.get("a_string"));
    assertEquals(new HashSet<String>(Arrays.asList("oll_21", "oll_22")), back.get("a_set"));
  }

  /**
   * The distinction the whole grouping exists for: SharedPreferences throws ClassCastException when
   * a value stored as one of these is read as the other, and JSON on its own cannot tell them apart.
   */
  @Test
  public void aLongDoesNotComeBackAsAnInt() {
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("small_long", Long.valueOf(3));
    values.put("small_int", Integer.valueOf(3));

    Map<String, Object> back = roundTrip(BackupScope.DEFAULT, values).get(BackupScope.DEFAULT);

    assertTrue(back.get("small_long") instanceof Long);
    assertTrue(back.get("small_int") instanceof Integer);
  }

  @Test
  public void anEmptyStringSetSurvives() {
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("no_cases", new HashSet<String>());

    Object back = roundTrip(BackupScope.DEFAULT, values).get(BackupScope.DEFAULT).get("no_cases");

    assertTrue(back instanceof Set);
    assertTrue(((Set<?>) back).isEmpty());
  }

  @Test
  public void theKeysThatDescribeTheInstallAreNotWritten() {
    Map<String, Object> values = new HashMap<String, Object>();
    values.put(AppLaunchStats.LAUNCH_COUNT_KEY, Long.valueOf(408));
    values.put(AppLaunchStats.FIRST_LAUNCH_KEY, Long.valueOf(1600000000000L));
    values.put(BackupScope.RELEASE_NOTES_VERSION_KEY, "2.0.0");
    values.put("timer_font_size", Integer.valueOf(60));

    PreferencesSnapshot snapshot = new PreferencesSnapshot();
    snapshot.put(BackupScope.DEFAULT, values);
    String json = snapshot.toJson();

    assertFalse(json.contains(AppLaunchStats.LAUNCH_COUNT_KEY));
    assertFalse(json.contains(AppLaunchStats.FIRST_LAUNCH_KEY));
    assertFalse(json.contains(BackupScope.RELEASE_NOTES_VERSION_KEY));
    assertTrue(json.contains("timer_font_size"));
  }

  /** A file written before a key was excluded must not put it back on the way in either. */
  @Test
  public void anExcludedKeyPresentInAFileIsNotApplied() {
    String json = "{\"default\":{\"long\":{\"launch_count\":408},\"int\":{\"timer_font_size\":60}}}";

    PreferencesSnapshot snapshot = PreferencesSnapshot.parse(json);

    assertNotNull(snapshot);
    assertNull(snapshot.get(BackupScope.DEFAULT).get(AppLaunchStats.LAUNCH_COUNT_KEY));
    assertEquals(Integer.valueOf(60), snapshot.get(BackupScope.DEFAULT).get("timer_font_size"));
  }

  /** The same key in two files is two values: only the default file's exclusions apply. */
  @Test
  public void anExcludedKeyIsOnlyExcludedFromTheFileItBelongsTo() {
    Map<String, Object> values = new HashMap<String, Object>();
    values.put(AppLaunchStats.LAUNCH_COUNT_KEY, Integer.valueOf(7));

    Map<String, Object> back = roundTrip(BackupScope.GRAPH, values).get(BackupScope.GRAPH);

    assertEquals(Integer.valueOf(7), back.get(AppLaunchStats.LAUNCH_COUNT_KEY));
  }

  @Test
  public void aFileTheBackupDidNotCarryReadsAsEmptyRatherThanNull() {
    PreferencesSnapshot snapshot = PreferencesSnapshot.parse("{}");

    assertNotNull(snapshot);
    assertTrue(snapshot.get(BackupScope.LANGUAGE).isEmpty());
  }

  @Test
  public void aTypeALaterVersionAddsIsLeftAloneRatherThanGuessedAt() {
    String json = "{\"default\":{\"double\":{\"whatever\":1.5},\"int\":{\"timer_font_size\":60}}}";

    PreferencesSnapshot snapshot = PreferencesSnapshot.parse(json);

    assertNotNull(snapshot);
    assertNull(snapshot.get(BackupScope.DEFAULT).get("whatever"));
    assertEquals(Integer.valueOf(60), snapshot.get(BackupScope.DEFAULT).get("timer_font_size"));
  }

  @Test
  public void somethingThatIsNotPreferencesReadsAsNothing() {
    assertNull(PreferencesSnapshot.parse("not json"));
    assertNull(PreferencesSnapshot.parse(null));
  }

  private static PreferencesSnapshot roundTrip(String file, Map<String, Object> values) {
    PreferencesSnapshot snapshot = new PreferencesSnapshot();
    snapshot.put(file, values);
    PreferencesSnapshot back = PreferencesSnapshot.parse(snapshot.toJson());
    assertNotNull(back);
    return back;
  }

}
