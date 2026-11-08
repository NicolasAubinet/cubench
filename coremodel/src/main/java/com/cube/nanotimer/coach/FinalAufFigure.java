package com.cube.nanotimer.coach;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * How long a solver waits before the final AUF, the U turn that lines up the last layer after the
 * PLL, over the family window's solves. The pause runs from the algorithm's last turn to the AUF
 * and includes the AUF's own turn, about 100 ms when it follows straight on.
 *
 * <p>Measured on the owner's 85 smart-cube PLLs (2026-11-08): 57 ended on a final AUF, a mean 401
 * ms and a median 356 ms after the algorithm; the quickest took 54 to 105 ms.
 */
public final class FinalAufFigure {

  /** A mean pause from which the coach mentions predicting the AUF: about 200 ms of waiting. */
  public static final int LONG_PAUSE_MS = 300;

  private final int solves;
  private final int aufs;
  private final long pauseMs;

  FinalAufFigure(int solves, int aufs, long pauseMs) {
    this.solves = solves;
    this.aufs = aufs;
    this.pauseMs = pauseMs;
  }

  /**
   * The figure, or null under {@code floor} final AUFs.
   *
   * @param solves the PLLs read, with a final AUF or without
   * @param pausesMs the pause before each final AUF among them
   */
  static FinalAufFigure of(int solves, List<Long> pausesMs, int floor) {
    if (pausesMs.size() < floor || pausesMs.isEmpty()) {
      return null;
    }
    long total = 0;
    for (Long pause : pausesMs) {
      total += pause.longValue();
    }
    return new FinalAufFigure(solves, pausesMs.size(), Math.round((double) total / pausesMs.size()));
  }

  /** PLLs read. */
  public int getSolves() {
    return solves;
  }

  /** Those of them that ended on a final AUF. */
  public int getAufs() {
    return aufs;
  }

  /** Mean time from the algorithm's last turn to the final AUF, the AUF's turn included. */
  public long getPauseMs() {
    return pauseMs;
  }

  public boolean isLong() {
    return pauseMs >= LONG_PAUSE_MS;
  }

  /** A field by its payload name, or null for a name it has not. */
  public Double field(String name) {
    if ("solves".equals(name)) {
      return Double.valueOf(solves);
    }
    if ("aufs".equals(name)) {
      return Double.valueOf(aufs);
    }
    if ("pause_ms".equals(name)) {
      return Double.valueOf(pauseMs);
    }
    return null;
  }

  public JSONObject toJson() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("solves", solves);
    json.put("aufs", aufs);
    json.put("pause_ms", pauseMs);
    return json;
  }

  public static FinalAufFigure fromJson(JSONObject json) throws JSONException {
    return new FinalAufFigure(json.getInt("solves"), json.getInt("aufs"),
        json.getLong("pause_ms"));
  }
}
