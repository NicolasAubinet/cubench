package com.cube.nanotimer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.cube.SolveMovesFormat;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.smartcube.drill.CrossDrillRep;
import com.cube.nanotimer.smartcube.drill.CrossDrillSession;
import com.cube.nanotimer.smartcube.drill.DrillRep;
import com.cube.nanotimer.smartcube.drill.DrillSession;
import com.cube.nanotimer.smartcube.drill.DrillSpec;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.vo.drill.DrillCaseRep;
import com.cube.nanotimer.vo.drill.DrillCrossRep;
import com.cube.nanotimer.vo.drill.DrillEnd;
import com.cube.nanotimer.vo.drill.DrillRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Test;

/**
 * What a recorded drill leaves behind, and in what order. The row a rep hangs off is written by the
 * first rep and takes a round trip to come back with an id, so the interesting cases are all about
 * what happens to reps that finish inside that gap.
 */
public class DrillRecorderTest {

  private static final long SHOWN_AT_MS = 100000;
  private static final long GAP_MS = 40;

  @Test
  public void aCasualDrillWritesNothingAtAll() {
    Writer writer = new Writer();
    DrillRecorder recorder = new DrillRecorder(writer, caseSpec(3), false);

    recorder.record(abandonedRep());
    recorder.end(DrillEnd.FINISHED);

    assertEquals(0, writer.drills.size());
    assertEquals(0, writer.caseReps.size());
    assertEquals(0, writer.ends.size());
  }

  /** A drill left before a single rep finished never happened, so there is no row saying it did. */
  @Test
  public void aDrillWithNoRepsStoresNothing() {
    Writer writer = new Writer();
    DrillRecorder recorder = new DrillRecorder(writer, caseSpec(20), true);

    recorder.end(DrillEnd.STOPPED);

    assertEquals(0, writer.drills.size());
    assertEquals(0, writer.ends.size());
  }

  @Test
  public void theFirstRepOpensTheDrillAndTheRestGoStraightIn() {
    Writer writer = new Writer();
    DrillRecorder recorder = new DrillRecorder(writer, caseSpec(3), true);

    recorder.record(abandonedRep());
    assertEquals(1, writer.drills.size());
    assertEquals(0, writer.caseReps.size()); // nothing to hang it off yet

    writer.openWith(7);
    assertEquals(1, writer.caseReps.size());

    recorder.record(abandonedRep());
    assertEquals(1, writer.drills.size()); // opened once, not once per rep
    assertEquals(2, writer.caseReps.size());
    assertEquals(7, writer.drillIds.get(1).longValue());
  }

  /** Rows are written as reps finish, so the order they were done in is stored, not implied. */
  @Test
  public void repsCarryThePositionTheyWereIn() {
    Writer writer = new Writer();
    DrillRecorder recorder = new DrillRecorder(writer, caseSpec(3), true);

    recorder.record(abandonedRep());
    recorder.record(abandonedRep());
    writer.openWith(1);
    recorder.record(abandonedRep());

    assertEquals(3, writer.caseReps.size());
    assertEquals(0, writer.caseReps.get(0).getPosition());
    assertEquals(1, writer.caseReps.get(1).getPosition());
    assertEquals(2, writer.caseReps.get(2).getPosition());
  }

  /** The end has to wait for the row it is about, which the reps ahead of it are also waiting for. */
  @Test
  public void anEndInsideTheGapLandsOnceTheRowIsThere() {
    Writer writer = new Writer();
    DrillRecorder recorder = new DrillRecorder(writer, caseSpec(20), true);

    recorder.record(abandonedRep());
    recorder.end(DrillEnd.CUBE_LOST);
    assertEquals(0, writer.ends.size());

    writer.openWith(4);
    assertEquals(1, writer.caseReps.size());
    assertEquals(1, writer.ends.size());
    assertEquals(DrillEnd.CUBE_LOST, writer.ends.get(0));
  }

  @Test
  public void whatWasTurnedIsStoredAsASolution() {
    Writer writer = new Writer();
    DrillSpec spec = caseSpec(1);
    DrillRecorder recorder = new DrillRecorder(writer, spec, true);
    DrillSession session = new DrillSession(spec, new Random(3));
    assertTrue(session.nextRep());
    session.markCaseShown(SHOWN_AT_MS);

    List<CubeMove> turns = moves(inverse(session.getCurrentScramble()));
    DrillRep rep = null;
    for (CubeMove move : turns) {
      rep = session.onMove(move);
    }
    assertNotNull(rep);

    recorder.record(rep);
    writer.openWith(1);

    List<SolveMovesFormat.Move> stored = SolveMovesFormat.parse(writer.caseReps.get(0).getMoves());
    assertEquals(turns.size(), stored.size());
    for (int i = 0; i < turns.size(); i++) {
      assertEquals(turns.get(i).getNotation(), stored.get(i).getNotation());
    }
    // Offsets run from the case going up, so the first one is the looking that preceded the turn.
    assertEquals(GAP_MS, stored.get(0).getOffsetMs());
  }

  /**
   * A cross rep is written when it ends, and the search for its shortest solution can land after
   * that. Inside the gap the row is not out yet, so the length goes in with it.
   */
  @Test
  public void aLateOptimalInsideTheGapGoesInWithTheRow() {
    Writer writer = new Writer();
    DrillRecorder recorder = new DrillRecorder(writer, crossSpec(), true);

    recorder.record(crossRep());
    recorder.setLastOptimalLength(6);
    writer.openWith(2);

    assertEquals(1, writer.crossReps.size());
    assertEquals(6, writer.crossReps.get(0).getOptimalLength());
    assertEquals(0, writer.optimalUpdates.size());
  }

  /** And once the row is out, the length is an update to it rather than a rep held back. */
  @Test
  public void aLateOptimalAfterTheRowUpdatesIt() {
    Writer writer = new Writer();
    DrillRecorder recorder = new DrillRecorder(writer, crossSpec(), true);

    recorder.record(crossRep());
    writer.openWith(2);
    recorder.setLastOptimalLength(8);

    assertEquals(0, writer.crossReps.get(0).getOptimalLength());
    assertEquals(1, writer.optimalUpdates.size());
    assertEquals("2/0/8", writer.optimalUpdates.get(0));
  }

  private static DrillSpec caseSpec(int reps) {
    return new DrillSpec("local-pllpicked", DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL,
        Arrays.asList("pll_ga"), DrillSpec.Selection.ROUND_ROBIN, reps, 0, "G perms");
  }

  private static DrillSpec crossSpec() {
    return DrillSpec.cross("local-cross-d", "D", 3, 0, "Cross");
  }

  private static DrillRep abandonedRep() {
    DrillSession session = new DrillSession(caseSpec(20), new Random(1));
    session.nextRep();
    session.markCaseShown(SHOWN_AT_MS);
    return session.abandon();
  }

  private static CrossDrillRep crossRep() {
    CrossDrillSession session = new CrossDrillSession(crossSpec());
    session.nextRep("R U F2 L' D");
    session.markCaseShown(SHOWN_AT_MS);
    session.onMove(new CubeMove(Face.R, false, SHOWN_AT_MS + GAP_MS));
    return session.declareFinished();
  }

  /** The scramble read back as the cube would report it: quarter turns, one at a time. */
  private static List<CubeMove> moves(String sequence) {
    List<CubeMove> moves = new ArrayList<CubeMove>();
    long at = SHOWN_AT_MS;
    for (String token : sequence.trim().split("\\s+")) {
      Face face = Face.valueOf(token.substring(0, 1));
      boolean prime = token.endsWith("'");
      int turns = token.endsWith("2") ? 2 : 1;
      for (int i = 0; i < turns; i++) {
        at += GAP_MS;
        moves.add(new CubeMove(face, prime, at));
      }
    }
    return moves;
  }

  private static String inverse(String sequence) {
    String[] tokens = sequence.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = tokens.length - 1; i >= 0; i--) {
      String token = tokens[i];
      sb.append(sb.length() == 0 ? "" : " ");
      if (token.endsWith("2")) {
        sb.append(token);
      } else if (token.endsWith("'")) {
        sb.append(token.substring(0, token.length() - 1));
      } else {
        sb.append(token).append("'");
      }
    }
    return sb.toString();
  }

  /** Holds the drill row's id back until a test hands it over, which is the gap under test. */
  private static class Writer implements DrillRecorder.Writes {
    final List<DrillRecord> drills = new ArrayList<DrillRecord>();
    final List<DrillCaseRep> caseReps = new ArrayList<DrillCaseRep>();
    final List<DrillCrossRep> crossReps = new ArrayList<DrillCrossRep>();
    final List<Long> drillIds = new ArrayList<Long>();
    final List<String> optimalUpdates = new ArrayList<String>();
    final List<DrillEnd> ends = new ArrayList<DrillEnd>();
    private DataCallback<Long> waiting;

    @Override
    public void addDrill(DrillRecord drill, DataCallback<Long> callback) {
      drills.add(drill);
      waiting = callback;
    }

    void openWith(long id) {
      DataCallback<Long> callback = waiting;
      waiting = null;
      callback.onData(id);
    }

    @Override
    public void addCaseRep(long drillId, DrillCaseRep rep) {
      drillIds.add(drillId);
      caseReps.add(rep);
    }

    @Override
    public void addCrossRep(long drillId, DrillCrossRep rep) {
      drillIds.add(drillId);
      crossReps.add(rep);
    }

    @Override
    public void setCrossOptimalLength(long drillId, int position, int optimalLength) {
      optimalUpdates.add(drillId + "/" + position + "/" + optimalLength);
    }

    @Override
    public void end(long drillId, DrillEnd end) {
      ends.add(end);
    }
  }
}
