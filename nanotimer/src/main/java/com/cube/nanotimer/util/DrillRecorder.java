package com.cube.nanotimer.util;

import com.cube.nanotimer.cube.SolveMovesFormat;
import com.cube.nanotimer.services.Service;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.smartcube.drill.CrossDrillRep;
import com.cube.nanotimer.smartcube.drill.DrillRep;
import com.cube.nanotimer.smartcube.drill.DrillSpec;
import com.cube.nanotimer.vo.drill.DrillCaseRep;
import com.cube.nanotimer.vo.drill.DrillCrossRep;
import com.cube.nanotimer.vo.drill.DrillEnd;
import com.cube.nanotimer.vo.drill.DrillRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes a drill's reps away as they finish, for a drill the user chose to record. A casual one
 * runs and is shown and leaves nothing behind, which is what choosing it means: a set drilled
 * loosely must not be claimable as a result at the end.
 *
 * <p><b>The drill row is opened by the first rep, not by the drill starting.</b> A drill nobody
 * finished a rep of is not a result and leaves no trace, and reps go in one at a time so that an
 * app killed mid-drill still keeps what was done. Reps that finish while the row is being opened
 * wait for its id and go in behind it, which is the one ordering that matters: everything after
 * that is an insert into a table of its own, and each rep carries the position it was in, so the
 * order the rows land in does not.
 *
 * <p>Nothing written here reaches the solve history or any average read from it.
 */
public class DrillRecorder {

  /**
   * The five writes a drill makes. Set apart from {@link Service} so that what a drill stores reads
   * in one place, and so that the ordering above can be exercised without a database.
   */
  interface Writes {
    void addDrill(DrillRecord drill, DataCallback<Long> callback);
    void addCaseRep(long drillId, DrillCaseRep rep);
    void addCrossRep(long drillId, DrillCrossRep rep);
    void setCrossOptimalLength(long drillId, int position, int optimalLength);
    void end(long drillId, DrillEnd end);
  }

  private final Writes writes;
  private final DrillSpec spec;
  private final boolean recording;
  /** When the drill started, not when its first rep opened the row it is stored in. */
  private final long startedAtMs = System.currentTimeMillis();

  private long drillId;
  private boolean opening;
  private int position;
  /** Reps that finished before the drill row had an id to hang them off. */
  private final List<Object> pending = new ArrayList<Object>();
  private DrillEnd endWhenOpened;
  private boolean ended;
  /** The last cross rep written, whose shortest solution may still be being searched for. */
  private DrillCrossRep lastCross;
  private int lastCrossPosition = -1;

  public DrillRecorder(Service service, DrillSpec spec, boolean recording) {
    this(writesOver(service), spec, recording);
  }

  DrillRecorder(Writes writes, DrillSpec spec, boolean recording) {
    this.writes = writes;
    this.spec = spec;
    this.recording = recording;
  }

  public synchronized void record(DrillRep rep) {
    if (!recording || ended) {
      return;
    }
    store(new DrillCaseRep(position++, rep.getCaseCode(), rep.getScramble(),
        SolveMovesFormat.format(rep.getMoves(), rep.getShownAtMs()), rep.getRecognitionMs(),
        rep.getExecutionMs(), rep.getMoveCount(), rep.getResetCount(), rep.wasRevealed(),
        rep.isAbandoned()));
  }

  public synchronized void record(CrossDrillRep rep) {
    if (!recording || ended) {
      return;
    }
    lastCrossPosition = position++;
    lastCross = new DrillCrossRep(lastCrossPosition, rep.getFace(), rep.getScramble(),
        SolveMovesFormat.format(rep.getMoves(), rep.getShownAtMs()), rep.getPlanningMs(),
        rep.getExecutionMs(), rep.getMoveCount(), rep.getOptimalLength(), rep.isBuilt(),
        rep.isPlanningExpired());
    store(lastCross);
  }

  /**
   * The shortest solution for the cross rep that has just ended, for a search that landed after it
   * did. The row is not held back waiting for one: a drill interrupted in between is worth more
   * with an unknown optimal than not at all, and 0 there already reads as unknown.
   */
  public synchronized void setLastOptimalLength(int optimalLength) {
    if (!recording || ended || lastCross == null || optimalLength <= 0) {
      return;
    }
    if (pending.contains(lastCross)) {
      lastCross.setOptimalLength(optimalLength); // not written yet, so it can still go in whole
    } else if (drillId > 0) {
      writes.setCrossOptimalLength(drillId, lastCrossPosition, optimalLength);
    }
  }

  /**
   * How the drill stopped. Nothing is written for a drill with no reps, so one left before its
   * first rep finished is not a result that happens to be empty: it never happened.
   */
  public synchronized void end(DrillEnd end) {
    if (!recording || ended) {
      return;
    }
    ended = true;
    if (drillId > 0) {
      writes.end(drillId, end);
    } else if (opening) {
      endWhenOpened = end;
    }
  }

  private void store(Object rep) {
    if (drillId > 0) {
      write(rep);
      return;
    }
    pending.add(rep);
    if (!opening) {
      opening = true;
      writes.addDrill(
          new DrillRecord(startedAtMs, spec.toJson(), spec.getId(), spec.getType().code(),
              spec.getReps()),
          new DataCallback<Long>() {
            @Override
            public void onData(Long id) {
              onOpened(id);
            }
          });
    }
  }

  private synchronized void onOpened(Long id) {
    opening = false;
    drillId = id == null ? 0 : id;
    if (drillId <= 0) {
      pending.clear(); // the row could not be written, so its reps have nowhere to go
      return;
    }
    for (Object rep : pending) {
      write(rep);
    }
    pending.clear();
    if (endWhenOpened != null) {
      writes.end(drillId, endWhenOpened);
      endWhenOpened = null;
    }
  }

  private void write(Object rep) {
    if (rep instanceof DrillCaseRep) {
      writes.addCaseRep(drillId, (DrillCaseRep) rep);
    } else {
      writes.addCrossRep(drillId, (DrillCrossRep) rep);
    }
  }

  /** Every write but the first is one nobody waits on: the screen shows the reps it already has. */
  private static Writes writesOver(final Service service) {
    return new Writes() {
      @Override
      public void addDrill(DrillRecord drill, DataCallback<Long> callback) {
        service.addDrill(drill, callback);
      }

      @Override
      public void addCaseRep(long drillId, DrillCaseRep rep) {
        service.addDrillCaseRep(drillId, rep, IGNORED);
      }

      @Override
      public void addCrossRep(long drillId, DrillCrossRep rep) {
        service.addDrillCrossRep(drillId, rep, IGNORED);
      }

      @Override
      public void setCrossOptimalLength(long drillId, int position, int optimalLength) {
        service.setDrillCrossRepOptimalLength(drillId, position, optimalLength, IGNORED);
      }

      @Override
      public void end(long drillId, DrillEnd end) {
        service.endDrill(drillId, end, IGNORED);
      }
    };
  }

  private static final DataCallback<Void> IGNORED = new DataCallback<Void>() {
    @Override
    public void onData(Void data) {
    }
  };
}
