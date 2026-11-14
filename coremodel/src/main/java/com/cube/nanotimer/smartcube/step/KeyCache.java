package com.cube.nanotimer.smartcube.step;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers {@link AlgorithmForm#keyAsDrawn} answers by case and moves. Each one replays the moves
 * in all 24 grips, and a screen listing a case's algorithms asks for the same few keys again and
 * again: redrawing the U perm's choose list took 100 to 150 ms on a OnePlus 8T, 25 ms with this.
 * The answer depends on nothing but the case and the moves, so it never goes stale.
 */
final class KeyCache {

  private static final int CAPACITY = 1024;
  /** Stands for a null key, so an unreadable spelling is not replayed again either. */
  private static final String NONE = "\u0000";

  private static final Map<String, String> KEYS =
      new LinkedHashMap<String, String>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
          return size() > CAPACITY;
        }
      };

  private KeyCache() {
  }

  static String keyAsDrawn(String caseCode, String moves, AlgorithmForm.Drawn drawn) {
    if (moves == null) {
      return null;
    }
    String entry = caseCode + '\u0000' + moves;
    synchronized (KEYS) {
      String known = KEYS.get(entry);
      if (known != null) {
        return known.equals(NONE) ? null : known;
      }
    }
    String key = AlgorithmForm.keyAsDrawn(moves, drawn);
    synchronized (KEYS) {
      KEYS.put(entry, key == null ? NONE : key);
    }
    return key;
  }
}
