package com.cube.nanotimer.util.coach;

import android.os.Handler;
import android.os.Looper;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.coach.CoachPayload;
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

  /** How far today's kept drill has been run, so a drill left part way picks up there; or null. */
  public static String progress(SolveType solveType) {
    return answeredToday(solveType) ? Options.INSTANCE.getCoachDailyProgress(solveType.getId())
        : null;
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
    final long askedAt = System.currentTimeMillis();
    final List<String> learned = new ArrayList<String>(Options.INSTANCE.getLearnedCases());
    App.INSTANCE.getService().getDailyPayload(solveType, method,
        new DataCallback<CoachPayload>() {
          @Override
          public void onData(final CoachPayload payload) {
            new Thread(new Runnable() {
              @Override
              public void run() {
                String reply = client.requestDaily(CoachClient.requestBody(
                    payload.withLanguage(CoachAsk.planLanguage()), learned, focus));
                if (isAnswer(reply)) {
                  Options.INSTANCE.setCoachDaily(solveType.getId(), reply, askedAt);
                }
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
