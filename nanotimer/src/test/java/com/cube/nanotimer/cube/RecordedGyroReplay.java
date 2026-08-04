package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.Face;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A real solve replayed from the cube's own reports: the gyro readings and the move stream of a
 * capture, run through the reconstruction end to end.
 *
 * <p>{@link RecordedSolveReplay} starts from a stored stream and so only exercises the display; this
 * starts from what the app was handed — the reading at the first scramble move, the reading at each
 * solve move, and the gyro history — so it exercises where the frame is actually decided.
 */
final class RecordedGyroReplay {

  /** As {@code OrientationHistory}: samples arrive every ~50 ms, so further out than this is a gap. */
  private static final long MATCH_TOLERANCE_MS = 150;

  private final List<Long> sampleTimes = new ArrayList<Long>();
  private final List<CubeOrientation> samples = new ArrayList<CubeOrientation>();
  private final GyroReference reference = new GyroReference();
  private String scramble;
  private String storedMoves;

  RecordedGyroReplay(String fixture) {
    RotationTracker tracker = new RotationTracker(reference);
    SliceSpinDetector sliceSpins = new SliceSpinDetector();
    List<CubeMove> moves = new ArrayList<CubeMove>();
    for (String line : lines(fixture)) {
      String[] parts = line.split(" ");
      if (parts[0].equals("scramble")) {
        scramble = line.substring("scramble ".length());
      } else if (parts[0].equals("reference")) {
        reference.anchor(orientation(parts, 1));
      } else if (parts[0].equals("move")) {
        long timestampMs = Long.parseLong(parts[1]);
        CubeMove move = new CubeMove(Face.valueOf(parts[2].substring(0, 1)),
            parts[2].endsWith("'"), timestampMs);
        moves.add(move);
        tracker.onMove(orientation(parts, 3), timestampMs);
        sliceSpins.onMove(move);
      } else if (parts[0].equals("sample")) {
        sampleTimes.add(Long.parseLong(parts[1]));
        samples.add(orientation(parts, 2));
      }
    }
    storedMoves = SolveMovesFormat.format(moves,
        tracker.getRotations(sliceSpins.coreSpins(this::sampleAt)), 0);
  }

  String getScramble() {
    return scramble;
  }

  /** The solve as it would have been stored, had this capture been recorded by the tree as it is. */
  String getStoredMoves() {
    return storedMoves;
  }

  /** The whole solve as the screen would show it, in the frames the reconstruction read. */
  String display() {
    return new RecordedSolveReplay(scramble, storedMoves).display();
  }

  /** The frame the step detector reads the solve in, off the states alone — no gyro in it. */
  String detectedFrame() {
    return new RecordedSolveReplay(scramble, storedMoves).detectedFrame();
  }

  /** The frame the gyro was in at a moment, snapped as the tracker snaps it; null where nothing read. */
  CubeRotation frameAt(long timestampMs) {
    return reference.frameOf(sampleAt(timestampMs));
  }

  private CubeOrientation sampleAt(long timestampMs) {
    int nearest = -1;
    for (int i = 0; i < sampleTimes.size(); i++) {
      if (nearest < 0 || distance(i, timestampMs) < distance(nearest, timestampMs)) {
        nearest = i;
      }
    }
    return nearest >= 0 && distance(nearest, timestampMs) <= MATCH_TOLERANCE_MS
        ? samples.get(nearest)
        : null;
  }

  private long distance(int index, long timestampMs) {
    return Math.abs(sampleTimes.get(index) - timestampMs);
  }

  private static CubeOrientation orientation(String[] parts, int from) {
    return new CubeOrientation(Double.parseDouble(parts[from]), Double.parseDouble(parts[from + 1]),
        Double.parseDouble(parts[from + 2]), Double.parseDouble(parts[from + 3]));
  }

  private static List<String> lines(String fixture) {
    List<String> lines = new ArrayList<String>();
    try (InputStream in = RecordedGyroReplay.class.getResourceAsStream("/gyro/" + fixture)) {
      if (in == null) {
        throw new IllegalArgumentException("no such capture fixture: " + fixture);
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        lines.add(line);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return lines;
  }
}
