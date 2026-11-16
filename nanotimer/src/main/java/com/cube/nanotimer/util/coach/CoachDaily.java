package com.cube.nanotimer.util.coach;

import android.os.Handler;
import android.os.Looper;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.coach.CoachPayload;
import com.cube.nanotimer.coach.FocusArea;
import com.cube.nanotimer.coach.StoredCoachPlan;
import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.smartcube.drill.DailyRun;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveType;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.TimeZone;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The coach's daily drill: a few minutes on the learned OLLs and PLLs least likely to hold, the
 * cases needing review folded in, chosen by the service with no model behind it. Asked for once a
 * day per solve type and kept for the day, so the card draws at once and the drill is the same
 * all day.
 */
public final class CoachDaily {

  public interface Listener {
    /** On the main thread, only when the service answered: empty for nothing due today. */
    void onDaily(List<DailyRun.Block> blocks);
  }

  /** Solve types with an ask under way, so a tab drawn twice asks once. */
  private static final Set<Integer> ASKING = Collections.synchronizedSet(new HashSet<Integer>());

  private CoachDaily() {
  }

  /** Today's drill as kept for this solve type; empty when none was kept today. */
  public static List<DailyRun.Block> kept(SolveType solveType) {
    int id = solveType.getId();
    if (!sameDay(Options.INSTANCE.getCoachDailyAt(id), System.currentTimeMillis(),
        TimeZone.getDefault())) {
      return new ArrayList<DailyRun.Block>();
    }
    return DailyRun.parse(Options.INSTANCE.getCoachDaily(id));
  }

  /** Whether today's answer is kept, an empty day included. */
  public static boolean answeredToday(SolveType solveType) {
    int id = solveType.getId();
    return Options.INSTANCE.getCoachDaily(id) != null && sameDay(
        Options.INSTANCE.getCoachDailyAt(id), System.currentTimeMillis(), TimeZone.getDefault());
  }

  /**
   * How far the kept drill has been run, so a drill left part way picks up there; or null. Kept with
   * its answer and cleared by the next one, so a drill begun before midnight resumes after it.
   */
  public static String progress(SolveType solveType) {
    return Options.INSTANCE.getCoachDailyProgress(solveType.getId());
  }

  /** The cases of today's kept drill, which the practice sets leave to it. */
  public static List<String> keptCases(SolveType solveType) {
    List<String> cases = new ArrayList<String>();
    for (DailyRun.Block block : kept(solveType)) {
      for (DailyRun.Entry entry : block.getEntries()) {
        cases.add(entry.getCode());
      }
    }
    return cases;
  }

  /**
   * Asks the service when no answer was kept today.
   *
   * @param focus the cases the current plan's drills run, which the service may add a few of
   */
  public static void refresh(final CoachClient client, final SolveType solveType,
      CubeMethod method, final Collection<String> focus, final Listener listener) {
    if (!client.hasAccess() || answeredToday(solveType) || !ASKING.add(solveType.getId())) {
      return;
    }
    final Handler main = new Handler(Looper.getMainLooper());
    final List<String> learned = new ArrayList<String>(Options.INSTANCE.getLearnedCases());
    App.INSTANCE.getService().getDailyPayload(solveType, method,
        new DataCallback<CoachPayload>() {
          @Override
          public void onData(final CoachPayload payload) {
            new Thread(new Runnable() {
              @Override
              public void run() {
                String reply = ask(client, solveType, payload, learned, focus);
                ASKING.remove(solveType.getId());
                if (!isAnswer(reply)) {
                  return;
                }
                final List<DailyRun.Block> blocks = DailyRun.parse(reply);
                main.post(new Runnable() {
                  @Override
                  public void run() {
                    listener.onDaily(blocks);
                  }
                });
              }
            }).start();
          }
        });
  }

  /**
   * Today's drill, asked for now when none was kept today, or null when the service did not
   * answer. Blocks on the service and the network, so never on the main thread.
   */
  public static List<DailyRun.Block> keptOrAsk(CoachClient client, SolveType solveType,
      CubeMethod method, StoredCoachPlan plan) throws InterruptedException {
    if (answeredToday(solveType)) {
      return kept(solveType);
    }
    if (client == null || !client.hasAccess() || !ASKING.add(solveType.getId())) {
      return null;
    }
    try {
      CoachPlanJob.Held<CoachPayload> payload = new CoachPlanJob.Held<CoachPayload>();
      App.INSTANCE.getService().getDailyPayload(solveType, method, payload);
      String reply = ask(client, solveType, payload.get(),
          new ArrayList<String>(Options.INSTANCE.getLearnedCases()), focusCases(plan));
      return isAnswer(reply) ? DailyRun.parse(reply) : null;
    } finally {
      ASKING.remove(solveType.getId());
    }
  }

  /** The cases the plan's drills run, which the service may add a few of to the day's drill. */
  public static List<String> focusCases(StoredCoachPlan plan) {
    List<String> focus = new ArrayList<String>();
    for (FocusArea area : plan.getPlan().getFocus()) {
      if (area.getDrill() != null) {
        try {
          focus.addAll(DrillSpec.fromJson(area.getDrill()).getCases());
        } catch (IllegalArgumentException e) {
          // a drill this app cannot read has no focus cases to offer
        }
      }
    }
    return focus;
  }

  /** Today's drill as far as it was run, or null when it is done or nothing is due. */
  public static DailyRun waiting(SolveType solveType, List<DailyRun.Block> blocks) {
    if (blocks == null || blocks.isEmpty()) {
      return null;
    }
    String progress = progress(solveType);
    DailyRun run = new DailyRun(blocks);
    run.restore(progress);
    return DailyRun.isDone(progress) || run.isDone() ? null : run;
  }

  private static String ask(CoachClient client, SolveType solveType, CoachPayload payload,
      List<String> learned, Collection<String> focus) {
    String reply = client.requestDaily(CoachClient.requestBody(
        payload.withLanguage(CoachAsk.planLanguage()), learned, focus));
    if (isAnswer(reply)) {
      // Dated on arrival: asked at 23:59 and answered at 00:01, it is the new day's.
      Options.INSTANCE.setCoachDaily(solveType.getId(), reply, System.currentTimeMillis());
    }
    return reply;
  }

  /** Text holding a blocks array, an empty one included: a day with nothing due is an answer. */
  static boolean isAnswer(String reply) {
    if (reply == null) {
      return false;
    }
    try {
      return new JSONObject(reply).optJSONArray("blocks") != null;
    } catch (JSONException e) {
      return false;
    }
  }

  static boolean sameDay(long a, long b, TimeZone zone) {
    if (a <= 0) {
      return false;
    }
    Calendar first = Calendar.getInstance(zone);
    first.setTimeInMillis(a);
    Calendar second = Calendar.getInstance(zone);
    second.setTimeInMillis(b);
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
        && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
  }
}
