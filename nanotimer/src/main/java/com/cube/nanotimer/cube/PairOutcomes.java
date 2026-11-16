package com.cube.nanotimer.cube;

import android.util.Log;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hands the database the F2L pairs each smart-cube solve put in, with their moves, so that case
 * knowledge can read a pair's solves ({@code PairOutcomeStore}). Only a replay of the solve says what
 * a pair was turned with, so solves are read in the background: the whole history once, then each
 * new solve as it is saved.
 */
public final class PairOutcomes {

  /** Bump when what is handed over changes: every solve is then read again. */
  public static final int VERSION = 1;

  /** Solves read per round trip to the database. */
  static final int BATCH = 50;

  private static final String TAG = "PairOutcomes";

  private static final AtomicBoolean RUNNING = new AtomicBoolean();
  private static final AtomicBoolean AGAIN = new AtomicBoolean();

  private PairOutcomes() {
  }

  /** Reads every solve not read yet. A call made while one runs makes it go round once more. */
  public static void catchUp() {
    if (Options.INSTANCE.getPairOutcomesVersion() != VERSION) {
      if (!RUNNING.compareAndSet(false, true)) {
        AGAIN.set(true);
        return;
      }
      App.INSTANCE.getService().forgetPairOutcomes(new DataCallback<Void>() {
        @Override
        public void onData(Void data) {
          Options.INSTANCE.setPairOutcomesVersion(VERSION);
          next(0);
        }
      });
      return;
    }
    if (!RUNNING.compareAndSet(false, true)) {
      AGAIN.set(true);
      return;
    }
    next(0);
  }

  /** @param read how many solves this run has read so far */
  private static void next(final int read) {
    AGAIN.set(false);
    App.INSTANCE.getService().getSolvesWithoutPairOutcomes(BATCH,
        new DataCallback<List<SolveTime>>() {
          @Override
          public void onData(List<SolveTime> solves) {
            if (solves.isEmpty()) {
              finish(read);
              return;
            }
            Map<Long, List<String[]>> outcomes = new LinkedHashMap<Long, List<String[]>>();
            for (SolveTime solve : solves) {
              outcomes.put(Long.valueOf(solve.getId()), readSafely(solve));
            }
            final int total = read + solves.size();
            // A save or two refreshes its own cases; a backfill rebuilds once at the end instead.
            final boolean small = read == 0 && solves.size() < BATCH;
            App.INSTANCE.getService().savePairOutcomes(outcomes, small, new DataCallback<Void>() {
              @Override
              public void onData(Void data) {
                if (small) {
                  done();
                } else {
                  next(total);
                }
              }
            });
          }
        });
  }

  private static void finish(int read) {
    if (read == 0) {
      done();
      return;
    }
    App.INSTANCE.getService().refreshCaseKnowledge(Collections.<String>emptyList(),
        new DataCallback<Void>() {
          @Override
          public void onData(Void data) {
            done();
          }
        });
  }

  private static void done() {
    RUNNING.set(false);
    if (AGAIN.getAndSet(false)) {
      catchUp();
    }
  }

  private static List<String[]> readSafely(SolveTime solve) {
    try {
      return read(solve);
    } catch (RuntimeException e) {
      Log.w(TAG, "Solve " + solve.getId() + " could not be read", e);
      return Collections.emptyList(); // kept as read, so it is not tried on every start
    }
  }

  /**
   * One solve's pairs as {case, moves}: the basic cases only, put in with at least one move, from a
   * CFOP solve that can be replayed. None for anything else, a pair the solve stopped inside included.
   */
  static List<String[]> read(SolveTime solve) {
    final List<String[]> pairs = new ArrayList<String[]>();
    CaseExecutions.readSteps(CaseExecutions.replay(solve), new CaseExecutions.StepReader() {
      @Override
      public void read(SolveStep step, SolveSolution.Step turned) {
        if (!SolveReading.F2L.equals(step.getName())) {
          return;
        }
        List<SolveStep> parts = step.getSubSteps();
        for (int i = 0; i < parts.size() && i < turned.getGroups().size(); i++) {
          String caseCode = CaseExecutions.caseOfPart(parts.get(i).getName());
          String moves = turned.getPartMoves(i);
          if (CaseAlgorithms.isBasicPair(caseCode) && moves != null && !moves.trim().isEmpty()) {
            pairs.add(new String[] { caseCode, moves });
          }
        }
      }
    });
    return pairs;
  }
}
