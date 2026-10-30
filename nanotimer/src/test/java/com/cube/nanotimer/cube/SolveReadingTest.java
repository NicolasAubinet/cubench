package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.coach.PairExecution;
import com.cube.nanotimer.session.AlgorithmFigures;
import com.cube.nanotimer.vo.SolveTime;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A kept reading has to say exactly what reading the solve again would, or the figures quietly
 * change the first time a window is opened twice. Solves are built as {@link CaseExecutionsTest}
 * builds them.
 */
public class SolveReadingTest {

  private static final String SUNE = "R U R' U R U U R'";
  private static final String TPERM = "R U R' U' R' F R R U' R' U' R U R' F'";
  /** F2L case 4 into each slot in turn, front right first. */
  private static final String PAIRS = "R U R' B U B' L U L' F U F'";

  private static List<SolveTime> solves() {
    return CaseExecutionsTest.solves(PAIRS + " " + SUNE + " " + TPERM);
  }

  @Test
  public void readsAPairTheSameWayOutOfAKeptReadingAsOutOfTheSolve() {
    List<SolveTime> solves = solves();
    SolveReadings read = new SolveReadings(null);
    List<PairExecution> fresh = F2lHabitReader.readFrom(solves, read);

    SolveReadings kept = new SolveReadings(read.unkept());
    List<PairExecution> again = F2lHabitReader.readFrom(solves, kept);

    assertTrue("nothing should have been read a second time", kept.unkept().isEmpty());
    assertEquals(fresh.size(), again.size());
    for (int i = 0; i < fresh.size(); i++) {
      assertEquals(fresh.get(i).getCaseCode(), again.get(i).getCaseCode());
      assertEquals(fresh.get(i).getSolveId(), again.get(i).getSolveId());
      assertEquals(fresh.get(i).getTimeMs(), again.get(i).getTimeMs());
      assertEquals(fresh.get(i).getMoves(), again.get(i).getMoves());
      assertEquals(fresh.get(i).getUsualMoves(), again.get(i).getUsualMoves());
      assertEquals(fresh.get(i).isListed(), again.get(i).isListed());
      assertEquals(fresh.get(i).isComparable(), again.get(i).isComparable());
    }
  }

  @Test
  public void readsTheFiguresTheSameWayOutOfAKeptReadingAsOutOfTheSolve() {
    List<SolveTime> solves = solves();
    SolveReadings read = new SolveReadings(null);
    Map<String, AlgorithmFigures> fresh = AlgorithmFiguresReader.readFrom(solves, read);

    SolveReadings kept = new SolveReadings(read.unkept());
    Map<String, AlgorithmFigures> again = AlgorithmFiguresReader.readFrom(solves, kept);

    assertTrue(kept.unkept().isEmpty());
    for (String family : fresh.keySet()) {
      assertEquals(family, fresh.get(family).getStandard(), again.get(family).getStandard());
      assertEquals(family, fresh.get(family).getNotStandard(), again.get(family).getNotStandard());
      assertEquals(family, fresh.get(family).getNotSeen(), again.get(family).getNotSeen());
      assertEquals(family, fresh.get(family).getExtraMoves(), again.get(family).getExtraMoves());
    }
    assertTrue("the fixture should have said something", fresh.get("pll").getStandard() > 0);
  }

  /** One window's reading serves the other reader, since both fold the same executions. */
  @Test
  public void oneReadingServesBothReaders() {
    List<SolveTime> solves = solves();
    SolveReadings read = new SolveReadings(null);
    AlgorithmFiguresReader.readFrom(solves, read);

    SolveReadings kept = new SolveReadings(read.unkept());
    List<PairExecution> pairs = F2lHabitReader.readFrom(solves, kept);

    assertTrue(kept.unkept().isEmpty());
    assertEquals(4, pairs.size());
  }

  /** A cache is never the only copy: text nothing can read is read out of the solve again. */
  @Test
  public void readsTheSolveAgainWhenTheKeptTextCannotBeRead() {
    List<SolveTime> solves = solves();
    Map<Long, String> rubbish = new HashMap<Long, String>();
    rubbish.put(Long.valueOf(solves.get(0).getId()), "not a reading");

    SolveReadings readings = new SolveReadings(rubbish);
    List<PairExecution> pairs = F2lHabitReader.readFrom(solves, readings);

    assertEquals(4, pairs.size());
    assertFalse("it should have been read and kept", readings.unkept().isEmpty());
  }

  /** A pair set against nothing still happened, so it survives being kept as one. */
  @Test
  public void keepsAPairThatCanBeSetAgainstNothingAsAPairThatHappened() {
    List<SolveReading.Execution> round = SolveReading.parse(SolveReading.toJson(
        new SolveReading(Collections.singletonList(
            new SolveReading.Execution("f2l", null, 1200, 0, 0, false, 1))))).getExecutions();

    assertEquals(1, round.size());
    assertNull(round.get(0).getCaseCode());
    assertFalse(round.get(0).isRead());
    assertEquals(1200, round.get(0).getTimeMs());
  }

  /** A two-looked last layer is not standard however usual each of its algorithms was. */
  @Test
  public void keepsHowManyAlgorithmsAStepTookApartFromWhetherTheyWereListed() {
    List<SolveReading.Execution> round = SolveReading.parse(SolveReading.toJson(
        new SolveReading(Collections.singletonList(
            new SolveReading.Execution("oll", "oll_21", 3400, 11, 11, true, 2))))).getExecutions();

    assertEquals(1, round.size());
    assertTrue(round.get(0).isListed());
    assertEquals(2, round.get(0).getAlgorithms());
  }

  @Test
  public void saysNothingWasReadOutOfASolveWithNoMoves() {
    List<SolveTime> solves = solves();
    solves.get(0).setSmartcubeMoves(null);

    assertTrue(SolveReading.read(solves.get(0)).isEmpty());
  }

  /** Each step's turning is kept with the executions and comes back as it was read. */
  @Test
  public void readsTheStepsTheSameWayOutOfAKeptReadingAsOutOfTheSolve() {
    List<SolveTime> solves = solves();
    SolveReadings read = new SolveReadings(null);
    SolveReading fresh = read.readingOf(solves.get(0));

    SolveReadings kept = new SolveReadings(read.unkept());
    SolveReading again = kept.readingOf(solves.get(0));

    assertTrue(kept.unkept().isEmpty());
    assertTrue("a solve read to the end is whole", fresh.isWhole());
    assertEquals(fresh.isWhole(), again.isWhole());
    assertFalse("the fixture should have said something", fresh.getTurnings().isEmpty());
    assertEquals(fresh.getTurnings().size(), again.getTurnings().size());
    for (int i = 0; i < fresh.getTurnings().size(); i++) {
      SolveReading.Turning a = fresh.getTurnings().get(i);
      SolveReading.Turning b = again.getTurnings().get(i);
      assertEquals(a.getCode(), b.getCode());
      assertEquals(a.getTimeMs(), b.getTimeMs());
      assertEquals(a.getMoves(), b.getMoves());
      assertEquals(a.getStalls(), b.getStalls());
      assertEquals(a.getStallMs(), b.getStallMs());
      assertEquals(a.getStopMs(), b.getStopMs());
    }
  }

  /** A row kept before steps were read has none, and is read again rather than believed empty. */
  @Test
  public void readsAgainARowKeptBeforeStepsWereRead() {
    assertNull(SolveReading.parse(
        "[{\"family\":\"f2l\",\"time_ms\":1200,\"moves\":0,\"usual_moves\":0,"
            + "\"listed\":false,\"algorithms\":1}]"));
  }

  /**
   * A solve stopped one AUF short keeps its named PLL and puts what is left in a tail after it,
   * which is no step of the method: it is not read as one, and the solve is not whole.
   */
  @Test
  public void readsNoTailAndNoWholeSolveOutOfASolveStoppedInsideItsPll() {
    SolveTime solve = CaseExecutionsTest.solves(PAIRS + " " + SUNE + " " + TPERM + " U").get(0);
    String[] played = solve.getSmartcubeMoves().split(" ");
    solve.setSmartcubeMoves(String.join(" ", Arrays.copyOf(played, played.length - 1)));

    SolveReading reading = SolveReading.read(solve);

    assertFalse(reading.isWhole());
    assertFalse("the fixture should still read its F2L", reading.getTurnings().isEmpty());
    for (SolveReading.Turning turning : reading.getTurnings()) {
      assertNotEquals(SolveBreakdown.UNFINISHED_STEP, turning.getCode());
    }
  }
}
