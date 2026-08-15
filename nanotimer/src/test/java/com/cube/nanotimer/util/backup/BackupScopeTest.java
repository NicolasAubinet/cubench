package com.cube.nanotimer.util.backup;

import static org.junit.Assert.assertArrayEquals;

import com.cube.nanotimer.util.helper.Utils;
import org.junit.Test;

/**
 * Which preferences files travel. Dropping one from the list costs the user real content and
 * nothing else notices, so the list itself is pinned.
 */
public class BackupScopeTest {

  /** The three files the app writes user content into. "apprater" and "seed_scrambles" are not. */
  @Test
  public void everyPreferencesFileHoldingUserContentIsCarried() {
    assertArrayEquals(new String[] { "default", "graph", Utils.LANGUAGE_PREFS_NAME },
      BackupScope.FILES);
  }

}
