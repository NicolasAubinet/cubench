package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import org.junit.Test;

/**
 * Driven by readings measured on a real V10 (capture of 2026-07-25): a scripted {@code M'},
 * {@code M}, then a genuine two-handed turn of the same two faces, each held still either side.
 * The slices stepped the gyro 88.9° and 82.4°; the two-hander moved it 4.1°, its noise floor.
 * Deriving these by hand instead is how the rotation work lost three days to sign errors.
 */
public class SliceSpinDetectorTest {

  /** Either side of the scripted {@code M'} — the cube reported it as {@code L R'}. */
  private static final CubeOrientation BEFORE_M_PRIME =
      new CubeOrientation(0.9920, -0.0567, -0.0070, 0.1121);
  private static final CubeOrientation AFTER_M_PRIME =
      new CubeOrientation(0.6718, -0.7358, -0.0716, 0.0460);

  /** Either side of the scripted {@code M} back — reported as {@code L' R}, rocking the other way. */
  private static final CubeOrientation AFTER_M =
      new CubeOrientation(0.9917, -0.0838, 0.0016, 0.0973);

  /** Either side of the two-handed control: the same faces, the core untouched. */
  private static final CubeOrientation BEFORE_TWO_HANDED =
      new CubeOrientation(0.9867, -0.0342, 0.0187, 0.1580);
  private static final CubeOrientation AFTER_TWO_HANDED =
      new CubeOrientation(0.9817, -0.0322, 0.0442, 0.1825);

  @Test
  public void readsAPairWithACoreRockAsASlice() {
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("R'", 1030));
    detector.onMove(move("U", 1400));

    List<RotationTracker.Rotation> spins =
        detector.coreSpins(rockedAt(1230, BEFORE_M_PRIME, AFTER_M_PRIME));

    assertEquals(1, spins.size());
    assertEquals("x", spins.get(0).getNotation());
    assertEquals(1031, spins.get(0).getTimestampMs()); // just behind the pair, where the fold looks
  }

  @Test
  public void leavesAPairAloneWhenTheCoreDidNotMove() {
    // The same two faces, turned two-handed: the core never rocks, so this is not a slice.
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("R'", 1030));

    assertEquals(0, detector.coreSpins(rockedAt(1230, BEFORE_TWO_HANDED, AFTER_TWO_HANDED)).size());
  }

  @Test
  public void ignoresACoreRockTheOtherWay() {
    // L R' rocks x; this reading is the x' of the slice back. The gyro moved, but not as this
    // slice moves it — so the pair is two honest faces that happen to sit next to a reorientation.
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("R'", 1030));

    assertEquals(0, detector.coreSpins(rockedAt(1230, AFTER_M_PRIME, AFTER_M)).size());
  }

  /**
   * The pick-up frame is wanted while the solve runs, before any reading can prove a rock, so it
   * asks for the pairs by their shape alone. A two-hander is reported here as well as a slice —
   * deliberately: the frame read between two faces that close is mid-turn whichever it was.
   */
  @Test
  public void reportsAPairByItsShapeBeforeAnyRockIsProved() {
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("R'", 1030));
    detector.onMove(move("U", 1400));

    List<RotationTracker.Rotation> pairs = detector.possiblePairs();

    assertEquals(1, pairs.size());
    assertEquals(1000, pairs.get(0).getPairFromMs()); // the window either face was read inside
    assertEquals(1030, pairs.get(0).getTimestampMs());
  }

  @Test
  public void reportsNoPairWhereTheFacesAreNotOneSlicesOwn() {
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("R", 1000));
    detector.onMove(move("L", 1030)); // the two faces turned opposite ways: never a slice
    detector.onMove(move("U", 1100));
    detector.onMove(move("D'", 1500)); // one slice's faces, but too far apart to be one turn

    assertEquals(0, detector.possiblePairs().size());
  }

  @Test
  public void leavesFacesTurnedTooFarApartAlone() {
    // Beyond the slice window the two turns are a deliberate pair, however the core moved — here
    // it moved between them, which is a solver reorienting mid-pair and is neither face's wide.
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("R'", 1500));

    assertEquals(0, detector.coreSpins(rockedAt(1250, BEFORE_M_PRIME, AFTER_M_PRIME)).size());
  }

  @Test
  public void foldsBothHalvesOfASliceAndItsUndo() {
    // The captured script itself: M' then M a second later, two rocks, each its own spin.
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("R'", 1030));
    detector.onMove(move("L'", 2000));
    detector.onMove(move("R", 2030));
    detector.onMove(move("U", 2500));

    TreeMap<Long, CubeOrientation> gyro = new TreeMap<Long, CubeOrientation>();
    gyro.put(0L, BEFORE_M_PRIME);
    gyro.put(1230L, AFTER_M_PRIME);
    gyro.put(2230L, AFTER_M);
    List<RotationTracker.Rotation> spins = detector.coreSpins(history(gyro));

    assertEquals(2, spins.size());
    assertEquals("x", spins.get(0).getNotation());
    assertEquals("x'", spins.get(1).getNotation());
  }

  @Test
  public void theSpinLandsWhereTheDisplayFoldLooksForIt() {
    // The contract between the two halves, which nothing else pins: the stored form writes a
    // rotation ahead of the move it precedes, and the fold wants it directly behind the pair.
    // Only the slice's own name is asserted — what the later faces relabel to is SolveSolution's.
    List<CubeMove> moves = Arrays.asList(move("L", 1000), move("R'", 1030), move("U", 1400));
    SliceSpinDetector detector = new SliceSpinDetector();
    for (CubeMove move : moves) {
      detector.onMove(move);
    }

    String stored = SolveMovesFormat.format(moves,
        detector.coreSpins(rockedAt(1230, BEFORE_M_PRIME, AFTER_M_PRIME)), 1000);
    SolveSolution solution = SolveSolution.from(stored,
        Arrays.asList(new SolveStep(0, "cross", 0, 1000, new ArrayList<SolveStep>())));

    assertEquals("L@0 R'@30 x@31 U@400", stored);
    assertEquals(2, solution.getMoveCount()); // the slice and the U; the spin is not a move
    assertEquals("M'", solution.getSteps().get(0).getMoves().split(" ")[0]);
  }

  @Test
  public void foldsAnM2WhoseHalvesHideEachOthersRock() {
    // Timings from a real PLL: two M pairs 115 ms apart. Neither half sees a clean 90° step —
    // each reading spans the other's rock — so the four turns are measured as one 180° instead.
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 27353));
    detector.onMove(move("R'", 27356));
    detector.onMove(move("L", 27471));
    detector.onMove(move("R'", 27477));
    detector.onMove(move("U", 28000));

    TreeMap<Long, CubeOrientation> gyro = new TreeMap<Long, CubeOrientation>();
    gyro.put(0L, BEFORE_M_PRIME);
    gyro.put(27400L, AFTER_M_PRIME); // mid-M2, where a single half would take its reading
    gyro.put(27600L, rockedTwice());
    List<RotationTracker.Rotation> spins = detector.coreSpins(history(gyro));

    assertEquals(2, spins.size()); // one spin per half: the display collapses them into M2
    assertEquals("x", spins.get(0).getNotation());
    assertEquals("x", spins.get(1).getNotation());
    assertEquals(27357, spins.get(0).getTimestampMs());
    assertEquals(27478, spins.get(1).getTimestampMs());
  }

  @Test
  public void readsARockThatLandsFortyDegreesOffAsTheSliceItIs() {
    // Measured on solve 140: a real M' whose settled reading sat 40° off the quarter turn. Judged
    // as a match against the 24 that is too far to call, but the pair leaves only two answers —
    // rocked or held still — and 40° is the rock.
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("R'", 1030));
    detector.onMove(move("U", 1400));

    List<RotationTracker.Rotation> spins =
        detector.coreSpins(rockedAt(1230, BEFORE_M_PRIME, fortyDegreesShort()));

    assertEquals(1, spins.size());
    assertEquals("x", spins.get(0).getNotation());
  }

  @Test
  public void findsNothingWithoutAGyro() {
    // A cube that reports no orientation folds nothing: the raw faces stand and still replay.
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("R'", 1030));

    SliceSpinDetector.Orientations none = timestampMs -> null;
    assertEquals(0, detector.coreSpins(none).size());
  }

  /** Same measured rock as the {@code M'} above: only the missing second face says which it was. */
  @Test
  public void readsALoneFaceWithACoreRockAsAWide() {
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("U", 1600));

    List<RotationTracker.Rotation> spins =
        detector.coreSpins(rockedAt(1050, BEFORE_M_PRIME, AFTER_M_PRIME));

    assertEquals(1, spins.size());
    assertEquals("x", spins.get(0).getNotation()); // L with a core x is the wide r
    assertEquals(999, spins.get(0).getTimestampMs()); // a millisecond ahead, where the fold looks
  }

  @Test
  public void leavesALoneFaceAloneWhenTheCoreDidNotMove() {
    // An ordinary L: the core never swung, so there is no wide here and nothing to fold.
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("U", 1600));

    assertEquals(0, detector.coreSpins(rockedAt(1050, BEFORE_TWO_HANDED, AFTER_TWO_HANDED)).size());
  }

  @Test
  public void ignoresALoneFaceWhoseCoreSwungTheOtherWay() {
    // L is a wide only with an x. This core swung x', which is the wide of the opposite face —
    // so this is an honest L that happened to land beside a reorientation.
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("U", 1600));

    assertEquals(0, detector.coreSpins(rockedAt(1050, AFTER_M_PRIME, AFTER_M)).size());
  }

  /** The core had arrived before the turn: a reorientation then a face, which is two moves. */
  @Test
  public void ignoresACoreThatHadFinishedSwingingBeforeTheTurn() {
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("U", 1600));

    // Settled into the new frame well before the window opens at 800 ms.
    assertEquals(0, detector.coreSpins(rockedAt(700, BEFORE_M_PRIME, AFTER_M_PRIME)).size());
  }

  /** A slice's two faces are spoken for: neither may also be read as a wide of its own. */
  @Test
  public void doesNotAlsoReadASlicesFacesAsWides() {
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("R'", 1030));
    detector.onMove(move("U", 1400));

    List<RotationTracker.Rotation> spins =
        detector.coreSpins(rockedAt(1230, BEFORE_M_PRIME, AFTER_M_PRIME));

    assertEquals(1, spins.size()); // the slice, and not a wide for either of its faces
    assertEquals(1031, spins.get(0).getTimestampMs());
  }

  /** The contract with the display: a millisecond ahead of the face, or the fold cannot see it. */
  @Test
  public void theWideSpinLandsWhereTheDisplayFoldLooksForIt() {
    List<CubeMove> moves = Arrays.asList(move("L", 1000), move("U", 1600));
    SliceSpinDetector detector = new SliceSpinDetector();
    for (CubeMove move : moves) {
      detector.onMove(move);
    }

    String stored = SolveMovesFormat.format(moves,
        detector.coreSpins(rockedAt(1050, BEFORE_M_PRIME, AFTER_M_PRIME)), 900);
    SolveSolution solution = SolveSolution.from(stored,
        Arrays.asList(new SolveStep(0, "f2l", 0, 1000, new ArrayList<SolveStep>())));

    assertEquals("x@99 L@100 U@700", stored);
    assertEquals(2, solution.getMoveCount()); // the wide and the U; the spin is not a move
    assertEquals("r", solution.getSteps().get(0).getMoves().split(" ")[0]);
  }

  @Test
  public void findsNoWideWithoutAGyro() {
    SliceSpinDetector detector = new SliceSpinDetector();
    detector.onMove(move("L", 1000));
    detector.onMove(move("U", 1600));

    SliceSpinDetector.Orientations none = timestampMs -> null;
    assertEquals(0, detector.coreSpins(none).size());
  }

  /** The reading after the same rock twice over: an M2's 180°. */
  private static CubeOrientation rockedTwice() {
    return AFTER_M_PRIME.multiply(BEFORE_M_PRIME.deltaTo(AFTER_M_PRIME));
  }

  /** The measured rock, only 50° round its axis: 40° short of the quarter turn it is part of. */
  private static CubeOrientation fortyDegreesShort() {
    CubeOrientation rock = BEFORE_M_PRIME.deltaTo(AFTER_M_PRIME);
    double axis = Math.sqrt(rock.getX() * rock.getX() + rock.getY() * rock.getY()
        + rock.getZ() * rock.getZ());
    double half = Math.toRadians(50) / 2;
    double scale = Math.sin(half) / axis;
    return BEFORE_M_PRIME.multiply(new CubeOrientation(Math.cos(half), rock.getX() * scale,
        rock.getY() * scale, rock.getZ() * scale));
  }

  private static CubeMove move(String notation, long cubeMs) {
    boolean prime = notation.endsWith("'");
    return new CubeMove(Face.valueOf(notation.substring(0, 1)), prime, cubeMs);
  }

  /** A gyro reading {@code before} until {@code fromMs}, and {@code after} from then on. */
  private static SliceSpinDetector.Orientations rockedAt(long fromMs, CubeOrientation before,
      CubeOrientation after) {
    TreeMap<Long, CubeOrientation> samples = new TreeMap<Long, CubeOrientation>();
    samples.put(0L, before);
    samples.put(fromMs, after);
    return history(samples);
  }

  private static SliceSpinDetector.Orientations history(TreeMap<Long, CubeOrientation> samples) {
    return wallMs -> {
      java.util.Map.Entry<Long, CubeOrientation> entry = samples.floorEntry(wallMs);
      return entry == null ? null : entry.getValue();
    };
  }
}
