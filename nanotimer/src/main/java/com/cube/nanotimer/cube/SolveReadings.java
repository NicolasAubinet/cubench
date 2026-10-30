package com.cube.nanotimer.cube;

import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A window's readings, taken from what was kept and read only where that comes up short.
 *
 * <p>One of these is made per window, handed the rows the store already holds, and asked for a
 * solve at a time. A solve it has to read for itself is remembered in {@link #unkept}, for the
 * caller to hand back to the store, so the next window over the same solves reads nothing.
 *
 * <p>It is asked solve by solve rather than filled up front on purpose: a reader that stops early,
 * as the Analysis figures do once every case has had its latest say, should not pay for the solves
 * it never looked at.
 */
public final class SolveReadings {

  private final Map<Long, String> kept;
  private final Map<Long, String> unkept = new LinkedHashMap<Long, String>();
  private final Map<Long, SolveReading> parsed = new HashMap<Long, SolveReading>();

  /** @param kept what the store holds for this window, by solve id; empty reads everything */
  public SolveReadings(Map<Long, String> kept) {
    this.kept = kept == null ? Collections.<Long, String>emptyMap() : kept;
  }

  /** Reads nothing back and keeps nothing: every solve is read where it is asked for. */
  public static SolveReadings none() {
    return new SolveReadings(null);
  }

  /** One solve's executions, off a kept row where there is one and out of the solve otherwise. */
  public List<SolveReading.Execution> of(SolveTime solve) {
    return readingOf(solve).getExecutions();
  }

  /** One solve's whole reading, its steps as well as its executions, got the same way. */
  public SolveReading readingOf(SolveTime solve) {
    Long id = Long.valueOf(solve.getId());
    SolveReading reading = parsed.get(id);
    if (reading != null) {
      return reading;
    }
    String text = kept.get(id);
    reading = text == null ? null : SolveReading.parse(text, solve.getId());
    if (reading == null) { // never read, read by another version, or kept as text nothing reads
      reading = SolveReading.read(solve);
      unkept.put(id, SolveReading.toJson(reading));
    }
    parsed.put(id, reading);
    return reading;
  }

  /** The ids of a window's solves, as the store is asked for what it holds of them. */
  public static List<Long> idsOf(List<SolveTime> solves) {
    List<Long> ids = new ArrayList<Long>(solves.size());
    for (SolveTime solve : solves) {
      ids.add(Long.valueOf(solve.getId()));
    }
    return ids;
  }

  /** What had to be read here, as text, for the caller to keep. Empty when everything was held. */
  public Map<Long, String> unkept() {
    return unkept;
  }
}
