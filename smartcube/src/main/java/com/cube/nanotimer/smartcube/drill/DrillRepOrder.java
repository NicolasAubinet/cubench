package com.cube.nanotimer.smartcube.drill;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Ranking a drill's reps by what each of them cost.
 *
 * <p>A skipped rep is put at the end whichever way round the list is asked for, rather than at
 * whichever end its seconds would land it. It has no time to compare: the seconds before giving up
 * are not a fast rep and not a slow one, so ranking it among the rest would say something about it
 * that is not true either way.
 */
public final class DrillRepOrder {

  /** Which of a rep's figures the reps are ranked on. */
  public enum Key {
    RECOGNITION,
    EXECUTION,
    TOTAL
  }

  private DrillRepOrder() {
  }

  /** What the rep cost on that figure. */
  public static long timeMs(DrillRep rep, Key key) {
    switch (key) {
      case RECOGNITION:
        return rep.getRecognitionMs();
      case EXECUTION:
        return rep.getExecutionMs();
      default:
        return rep.getTotalMs();
    }
  }

  /** Ranks a list in place. Stable, so reps that cost the same stay in the order they were dealt. */
  public static void sort(List<DrillRep> reps, final Key key, final boolean slowestFirst) {
    Collections.sort(reps, new Comparator<DrillRep>() {
      @Override
      public int compare(DrillRep one, DrillRep other) {
        if (one.isAbandoned() != other.isAbandoned()) {
          return one.isAbandoned() ? 1 : -1;
        }
        if (one.isAbandoned()) {
          return 0;
        }
        int order = Long.compare(timeMs(one, key), timeMs(other, key));
        return slowestFirst ? -order : order;
      }
    });
  }
}
