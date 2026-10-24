package com.cube.nanotimer.util.coach;

import com.cube.nanotimer.App;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.coach.CoachPayload;
import com.cube.nanotimer.coach.CoachPayloadBuilder;
import com.cube.nanotimer.coach.PairExecution;
import com.cube.nanotimer.coach.StepTurning;
import com.cube.nanotimer.coach.StoredCoachPlan;
import com.cube.nanotimer.cube.F2lHabitReader;
import com.cube.nanotimer.cube.LastLayerHabitReader;
import com.cube.nanotimer.cube.SolveReading;
import com.cube.nanotimer.cube.SolveReadings;
import com.cube.nanotimer.cube.TurningReader;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One ask for a plan, from the history to the plan kept. It needs no screen, so a background job
 * and the Plan tab ask the same way.
 *
 * <p><b>A request that got no answer is kept and sent again as it was, never rebuilt.</b> The phone
 * can give up after the service has already stored a plan, and only the same payload text is
 * answered from that store rather than bought a second time. A refusal says no plan was stored, so
 * it lets the request go and the next ask reads the history afresh. So does an hour: a request
 * left that long would be answered from solves the history has since moved past.
 *
 * <p><b>One ask runs at a time</b>, whether the job or a screen started it. A second one for the
 * same solve type gets the first one's answer, and one for another solve type is told to wait.
 */
final class CoachAsk {

  /** How long an unanswered request is resent as it was before the history is read afresh. */
  static final long PENDING_MAX_AGE_MS = 60L * 60 * 1000;

  interface Listener {
    /**
     * Called once, on any thread and possibly before {@link #ask} returns, a plan having been
     * saved first.
     *
     * @param failure how the ask ended without a plan, null when one was saved
     * @param solveCount how many solves the method has been read from, or -1 when nothing counted
     */
    void onAnswer(CoachReply failure, int solveCount);
  }

  interface Watcher {
    /**
     * Called on the asking thread before an ask starts.
     *
     * @return a listener to answer as well, or null
     */
    Listener onAsking(SolveType solveType);
  }

  /** The ask under way, null when there is none. Guarded by the class. */
  private static CoachAsk running;
  private static Watcher watcher;

  private final CoachClient client;
  private final SolveType solveType;
  private final CubeMethod method;
  /** Guarded by the class. */
  private final List<Listener> listeners = new ArrayList<Listener>();
  private int solveCount = -1;

  private CoachAsk(CoachClient client, SolveType solveType, CubeMethod method) {
    this.client = client;
    this.solveType = solveType;
    this.method = method;
  }

  /**
   * Asks for a plan, sending again what went unanswered if anything did. Returns at once.
   *
   * @param lastPlanAt the last plan's time as the caller read it: a plan saved since then is the
   *     answer, and nothing is asked
   * @return whether this call started an ask, rather than being answered by another one
   */
  static boolean ask(CoachClient client, SolveType solveType, CubeMethod method, long lastPlanAt,
      Listener listener) {
    CoachAsk started = null;
    boolean busy = false;
    synchronized (CoachAsk.class) {
      if (running != null) {
        if (running.solveType.getId() == solveType.getId()) {
          running.listen(listener);
          return false;
        }
        busy = true;
      } else if (Options.INSTANCE.getCoachLastPlanAt() == lastPlanAt) {
        started = new CoachAsk(client, solveType, method);
        started.listen(listener);
        if (watcher != null) {
          started.listen(watcher.onAsking(solveType));
        }
        running = started;
      }
    }
    if (started == null) {
      listener.onAnswer(busy ? CoachReply.refused(CoachRefusal.RATE_LIMITED) : null, -1);
      return false;
    }
    started.start();
    return true;
  }

  /** Gives the listener the answer of the ask under way for the solve type, if there is one. */
  static synchronized boolean join(SolveType solveType, Listener listener) {
    if (running == null || running.solveType.getId() != solveType.getId()) {
      return false;
    }
    running.listen(listener);
    return true;
  }

  /** Once each: a screen can be handed the same ask twice, as its watcher and as it opens. */
  private void listen(Listener listener) {
    if (listener != null && !listeners.contains(listener)) {
      listeners.add(listener);
    }
  }

  /** Who is told when an ask starts, so a screen can show one it did not start. */
  static synchronized void setWatcher(Watcher watcher) {
    CoachAsk.watcher = watcher;
  }

  /** Whether an unanswered request kept at the given time is too old to send again as it was. */
  static boolean stale(long keptAt, long now) {
    return keptAt > now || now - keptAt > PENDING_MAX_AGE_MS;
  }

  private void start() {
    // Before the history is read: nothing is built to be sent to a coach that will not take it.
    if (client.accessEnded()) {
      answer(CoachReply.refused(CoachRefusal.NO_ACCESS));
      return;
    }
    String pending = Options.INSTANCE.getCoachPendingRequest(solveType.getId());
    if (pending != null) {
      long keptAt = Options.INSTANCE.getCoachPendingRequestAt(solveType.getId());
      if (!stale(keptAt, System.currentTimeMillis())) {
        send(pending);
        return;
      }
      Options.INSTANCE.setCoachPendingRequest(solveType.getId(), null);
    }
    App.INSTANCE.getService().getCoachSolveCount(solveType, method, new DataCallback<Integer>() {
      @Override
      public void onData(Integer count) {
        solveCount = count.intValue();
        // Below the floor every figure is withheld, so the answer is known without asking.
        if (solveCount < CoachPayloadBuilder.PLAN_FLOOR) {
          answer(CoachReply.refused(CoachRefusal.INSUFFICIENT_DATA));
        } else {
          read();
        }
      }
    });
  }

  /**
   * Reads the F2L move window's solves for their pairs, last layers and steps first, which only the
   * app can do.
   * A solve read before is taken from the store rather than solved again, which is what keeps this
   * off the wait the solver is watching: only solves done since the last plan cost anything.
   */
  private void read() {
    if (method != CubeMethod.CFOP) {
      build(Collections.<PairExecution>emptyList(), Collections.<StepTurning>emptyList());
      return;
    }
    App.INSTANCE.getService().getMethodSolves(solveType, method,
        CoachPayloadBuilder.F2L_MOVE_WINDOW,
        new DataCallback<List<SolveTime>>() {
          @Override
          public void onData(final List<SolveTime> solves) {
            App.INSTANCE.getService().getSolveReadings(SolveReadings.idsOf(solves),
                SolveReading.VERSION, new DataCallback<Map<Long, String>>() {
                  @Override
                  public void onData(Map<Long, String> kept) { // off the main thread already
                    SolveReadings readings = new SolveReadings(kept);
                    List<PairExecution> executions = F2lHabitReader.readFrom(solves, readings);
                    executions.addAll(LastLayerHabitReader.readFrom(solves, readings));
                    List<StepTurning> turnings = TurningReader.readFrom(solves, readings);
                    App.INSTANCE.getService().saveSolveReadings(readings.unkept(),
                        SolveReading.VERSION, null);
                    build(executions, turnings);
                  }
                });
          }
        });
  }

  private void build(List<PairExecution> executions, List<StepTurning> turnings) {
    App.INSTANCE.getService().getCoachPayload(solveType, method, executions, turnings,
        new DataCallback<CoachPayload>() {
          @Override
          public void onData(final CoachPayload payload) {
            App.INSTANCE.getService().getCoachStream(solveType, new DataCallback<String>() {
              @Override
              public void onData(String stream) {
                String body = CoachClient.requestBody(
                    payload.withLanguage(Utils.appLanguage(App.INSTANCE.getContext())), stream);
                // Kept before it leaves, so a request the process dies on is still the one resent.
                Options.INSTANCE.setCoachPendingRequest(solveType.getId(), body);
                send(body);
              }
            });
          }
        });
  }

  private void send(final String body) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        final CoachReply reply = client.requestPlan(body);
        switch (reply.getKind()) {
          case PLAN:
            final StoredCoachPlan stored = new StoredCoachPlan(reply.getPlan(),
                CoachClient.payloadOf(body), System.currentTimeMillis());
            App.INSTANCE.getService().saveCoachPlan(solveType, stored, new DataCallback<Void>() {
              @Override
              public void onData(Void unused) {
                Options.INSTANCE.setCoachPendingRequest(solveType.getId(), null);
                Options.INSTANCE.setCoachLastPlanAt(stored.getWrittenAt());
                answer(null);
              }
            });
            break;
          case REFUSED:
            Options.INSTANCE.setCoachPendingRequest(solveType.getId(), null);
            answer(reply);
            break;
          default:
            // No answer, or one this app cannot read: either way a plan may be sitting in the store.
            answer(reply);
            break;
        }
      }
    }).start();
  }

  /** Ends the ask. Only then can another start, so the last plan's time is already written. */
  private void answer(CoachReply failure) {
    List<Listener> told;
    synchronized (CoachAsk.class) {
      running = null;
      told = new ArrayList<Listener>(listeners);
    }
    for (Listener listener : told) {
      listener.onAnswer(failure, solveCount);
    }
  }
}
