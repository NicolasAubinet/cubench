package com.cube.nanotimer.smartcube.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.drivers.GanMoveBuffer.BufferedMove;
import com.cube.nanotimer.smartcube.model.Face;
import org.junit.Test;

/** The reordering and gap detection behind Gen3/Gen4 move tracking. */
public class GanMoveBufferTest {

  private static BufferedMove live(int serial) {
    return new BufferedMove(serial, Face.U, false, 100L, 1000L);
  }

  private static BufferedMove recovered(int serial) {
    return new BufferedMove(serial, Face.U, false, null, null);
  }

  @Test
  public void movesAreDroppedUntilTheBufferIsAnchored() {
    GanMoveBuffer buffer = new GanMoveBuffer();
    assertTrue(buffer.needsAnchor());

    assertTrue(buffer.push(live(1)).getEvicted().isEmpty());
  }

  @Test
  public void contiguousMovesPassStraightThrough() {
    GanMoveBuffer buffer = new GanMoveBuffer();
    buffer.anchor(1);

    assertEquals(1, buffer.push(live(2)).getEvicted().size());
    assertEquals(1, buffer.push(live(3)).getEvicted().size());
    assertEquals(0, buffer.size());
  }

  @Test
  public void aGapHoldsTheMoveAndAsksForHistory() {
    GanMoveBuffer buffer = new GanMoveBuffer();
    buffer.anchor(1);

    GanMoveBuffer.Result result = buffer.push(live(4)); // 2 and 3 never arrived

    assertTrue(result.getEvicted().isEmpty());
    assertNotNull(result.getHistoryRequest());
    assertEquals(4, result.getHistoryRequest().getSerial());
    assertEquals(3, result.getHistoryRequest().getCount());
  }

  @Test
  public void historyFillsTheHoleAndReleasesEverythingBehindIt() {
    GanMoveBuffer buffer = new GanMoveBuffer();
    buffer.anchor(1);
    buffer.push(live(4));

    assertTrue(buffer.injectHistory(recovered(3)).getEvicted().isEmpty()); // 2 still missing
    GanMoveBuffer.Result result = buffer.injectHistory(recovered(2));

    assertEquals(3, result.getEvicted().size()); // 2, 3 and the queued 4, in order
    assertEquals(2, result.getEvicted().get(0).getSerial());
    assertEquals(4, result.getEvicted().get(2).getSerial());
  }

  /** Answering a history request must not ask for more, or a still-short reply would spin. */
  @Test
  public void injectingHistoryNeverAsksForMoreHistory() {
    GanMoveBuffer buffer = new GanMoveBuffer();
    buffer.anchor(1);
    buffer.push(live(6));

    assertNull(buffer.injectHistory(recovered(5)).getHistoryRequest());
  }

  @Test
  public void aMoveAlreadyHeldIsNotTakenTwice() {
    GanMoveBuffer buffer = new GanMoveBuffer();
    buffer.anchor(1);
    buffer.push(live(3));

    buffer.injectHistory(recovered(3));

    assertEquals(1, buffer.size());
  }

  @Test
  public void aRunawayBufferGivesUpAndDesyncs() {
    GanMoveBuffer buffer = new GanMoveBuffer();
    buffer.anchor(1);

    GanMoveBuffer.Result result = null;
    for (int serial = 3; serial <= 20 && (result == null || !result.isDesynced()); serial++) {
      result = buffer.push(live(serial)); // serial 2 never arrives, so nothing ever drains
    }

    assertTrue(result.isDesynced());
    assertTrue(result.getLostMoves() > 0);
    assertTrue(buffer.needsAnchor()); // moves are dropped until fresh facelets land
  }

  @Test
  public void aFaceletsSnapshotAheadOfTheModelRevealsMissedMoves() {
    GanMoveBuffer buffer = new GanMoveBuffer();
    buffer.anchor(1);

    GanMoveBuffer.HistoryRequest request = buffer.checkForMissedMoves(4);

    assertNotNull(request);
    assertEquals(5, request.getSerial());
    assertEquals(4, request.getCount());
  }

  /** A facelets packet overtaken in flight by a move is behind the model, not a cycle ahead of it. */
  @Test
  public void aFaceletsSnapshotBehindTheModelRevealsNothing() {
    GanMoveBuffer buffer = new GanMoveBuffer();
    buffer.anchor(5);

    assertNull(buffer.checkForMissedMoves(4));
  }

  /** The firmware reports a bogus facelets state as the move counter wraps past 255. */
  @Test
  public void serialZeroIsNeverTrustedForGapDetection() {
    GanMoveBuffer buffer = new GanMoveBuffer();
    buffer.anchor(250);

    assertNull(buffer.checkForMissedMoves(0));
  }

  @Test
  public void serialsWrapAroundTheByte() {
    GanMoveBuffer buffer = new GanMoveBuffer();
    buffer.anchor(254);

    assertEquals(1, buffer.push(live(255)).getEvicted().size());
    assertEquals(1, buffer.push(live(0)).getEvicted().size());
    assertEquals(1, buffer.push(live(1)).getEvicted().size());
  }

  @Test
  public void signedSerialDiffReadsBothWaysRoundTheRing() {
    assertEquals(1, GanMoveBuffer.signedSerialDiff(5, 4));
    assertEquals(-1, GanMoveBuffer.signedSerialDiff(4, 5));
    assertEquals(1, GanMoveBuffer.signedSerialDiff(0, 255)); // wrapped forward
    assertEquals(-1, GanMoveBuffer.signedSerialDiff(255, 0)); // wrapped back
  }

  @Test
  public void anchoringClearsWhateverWasHeld() {
    GanMoveBuffer buffer = new GanMoveBuffer();
    buffer.anchor(1);
    buffer.push(live(5)); // held behind a gap

    buffer.anchor(5);

    assertEquals(0, buffer.size());
    assertFalse(buffer.needsAnchor());
    assertEquals(1, buffer.push(live(6)).getEvicted().size());
  }
}
