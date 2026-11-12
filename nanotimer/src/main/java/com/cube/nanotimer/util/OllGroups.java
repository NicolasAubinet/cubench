package com.cube.nanotimer.util;

import com.cube.nanotimer.step.LastLayerAlgorithms;
import com.cube.nanotimer.step.OllShapes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** The OLL shape groups as a whole set: which there are, and how many a solver has complete. */
public final class OllGroups {

  private static final List<String> ALL = all();

  private OllGroups() {
  }

  /** Every group, in the order their first case comes in the OLL set. */
  public static List<String> getAll() {
    return ALL;
  }

  /** Whether every case of the group is among {@code known} ({@code oll_7} codes). */
  public static boolean isComplete(String group, Collection<String> known) {
    return known.containsAll(OllShapes.casesOf(group));
  }

  /** How many of the groups {@code known} holds every case of. */
  public static int countComplete(Collection<String> known) {
    int complete = 0;
    for (String group : ALL) {
      if (isComplete(group, known)) {
        complete++;
      }
    }
    return complete;
  }

  private static List<String> all() {
    List<String> groups = new ArrayList<String>();
    for (String[] row : LastLayerAlgorithms.ORIENTATIONS) {
      String group = OllShapes.groupOf(row[0]);
      if (group != null && !groups.contains(group)) {
        groups.add(group);
      }
    }
    return Collections.unmodifiableList(groups);
  }
}
