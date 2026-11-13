package com.cube.nanotimer.util.coach;

import android.os.Handler;
import android.os.Looper;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.coach.CoachPayload;
import com.cube.nanotimer.coach.PairExecution;
import com.cube.nanotimer.coach.StepTurning;
import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveType;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The practice sets under a plan: per family, the cases costing the solver the most, then the PLLs
 * or OLLs to learn next while either is not fully known, chosen by the coach service with no model
 * behind it. Cached as the service sent them, so the section draws at once and a failed refresh
 * keeps the last answer.
 *
 * <p>Asked for once a day per solve type, on the first visit of the day, and at once when a learning
 * drill clears cases, since that changes the answer. Each ask wakes the service's database for
 * minutes, and a day's new solves are what move the sets anyway. A case the plan also drills stays
 * in its set, so "your costliest" always means the costliest.
 */
public final class CoachPractice {

  /** One family's cases, costliest first, and the drill that runs them. */
  public static final class Set {
    private final String family;
    private final List<String> cases;
    private final DrillSpec drill;

    Set(String family, List<String> cases, DrillSpec drill) {
      this.family = family;
      this.cases = Collections.unmodifiableList(cases);
      this.drill = drill;
    }

    public String getFamily() {
      return family;
    }

    public List<String> getCases() {
      return cases;
    }

    public DrillSpec getDrill() {
      return drill;
    }

    /** Whether this is the "learn next" set: PLLs or OLLs to learn, best first, rather than to speed up. */
    public boolean isLearning() {
      return drill.isLearning();
    }

    /**
     * The first {@code count} cases of a learning set and the drill of just them, leaving out any
     * cleared in a drill since the service answered. Null when none are left.
     */
    public Set firstToLearn(int count, Collection<String> learned) {
      List<String> left = new ArrayList<String>();
      for (String code : cases) {
        if (left.size() < count && !learned.contains(code)) {
          left.add(code);
        }
      }
      return left.isEmpty() ? null : withCases(left);
    }

    /**
     * This learning set's drill on cases the user chose. The service's algorithm is kept for any
     * case it sent; the others fall back to the user's star or the choose step.
     */
    public Set withCases(List<String> chosen) {
      Map<String, String> algorithms = new LinkedHashMap<String, String>();
      for (String code : chosen) {
        if (drill.getCaseAlgorithm(code) != null) {
          algorithms.put(code, drill.getCaseAlgorithm(code));
        }
      }
      List<String> left = new ArrayList<String>(chosen);
      return new Set(family, left, DrillSpec.learning(drill.getId(), drill.getType(), left,
          drill.getSelection(), drill.getReps(), drill.getWinsToRetire(), algorithms,
          drill.getLabel()));
    }

    /** How many cases a learning set still has to offer, the cleared ones left out. */
    public int countToLearn(Collection<String> learned) {
      int count = 0;
      for (String code : cases) {
        if (!learned.contains(code)) {
          count++;
        }
      }
      return count;
    }
  }

  public interface Listener {
    /** On the main thread, only when the service answered. */
    void onPractice(List<Set> sets);

    /** On the main thread, when the coach answered that nothing lets this install in any more. */
    void onAccessEnded();
  }

  private CoachPractice() {
  }

  /** The sets last kept for this solve type, empty when there are none. */
  public static List<Set> kept(SolveType solveType) {
    return parse(Options.INSTANCE.getCoachPractice(solveType.getId()));
  }

  /**
   * Asks the service afresh when the kept sets are due ({@link #due}). Only the case figures matter
   * here, so the payload is built without the replayed pairs and turnings a plan waits on.
   */
  public static void refresh(final CoachClient client, final SolveType solveType,
      final CubeMethod method, final Listener listener) {
    if (!client.hasAccess()) {
      return;
    }
    List<String> learned = new ArrayList<String>(Options.INSTANCE.getLearnedCases());
    // The daily drill's cases are left to it, so the sets are asked again once it is in.
    List<String> daily = CoachDaily.keptCases(solveType);
    String with = daily.isEmpty() ? askedWith(learned)
        : askedWith(learned) + "|" + askedWith(daily);
    int id = solveType.getId();
    if (!due(Options.INSTANCE.getCoachPractice(id), Options.INSTANCE.getCoachPracticeAt(id),
        Options.INSTANCE.getCoachPracticeWith(id), System.currentTimeMillis(), with,
        TimeZone.getDefault())) {
      return;
    }
    ask(client, solveType, method, learned, daily, with, listener);
  }

  /**
   * Whether the kept sets should be asked for again: none kept, kept on an earlier local day, or
   * asked with other cleared cases than now.
   */
  static boolean due(String kept, long askedAt, String askedWith, long now, String with,
      TimeZone zone) {
    return kept == null || askedAt <= 0 || !with.equals(askedWith)
        || !sameDay(askedAt, now, zone);
  }

  /** The cleared cases, sorted, as one string to compare an earlier ask by. */
  static String askedWith(Collection<String> learned) {
    List<String> sorted = new ArrayList<String>(learned);
    Collections.sort(sorted);
    return join(sorted);
  }

  private static String join(List<String> codes) {
    StringBuilder joined = new StringBuilder();
    for (String code : codes) {
      if (joined.length() > 0) {
        joined.append(',');
      }
      joined.append(code);
    }
    return joined.toString();
  }

  private static boolean sameDay(long a, long b, TimeZone zone) {
    Calendar first = Calendar.getInstance(zone);
    first.setTimeInMillis(a);
    Calendar second = Calendar.getInstance(zone);
    second.setTimeInMillis(b);
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
        && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
  }

  private static void ask(final CoachClient client, final SolveType solveType, CubeMethod method,
      final List<String> learned, final List<String> daily, final String with,
      final Listener listener) {
    final Handler main = new Handler(Looper.getMainLooper());
    final long askedAt = System.currentTimeMillis();
    App.INSTANCE.getService().getCoachPayload(solveType, method,
        Collections.<PairExecution>emptyList(), Collections.<StepTurning>emptyList(),
        new DataCallback<CoachPayload>() {
          @Override
          public void onData(final CoachPayload payload) {
            new Thread(new Runnable() {
              @Override
              public void run() {
                String reply = client.requestPractice(CoachClient.requestBody(
                    payload.withLanguage(CoachAsk.planLanguage()), learned, daily));
                final List<Set> sets = parse(reply);
                if (reply == null && !client.hasAccess()) {
                  main.post(new Runnable() {
                    @Override
                    public void run() {
                      listener.onAccessEnded();
                    }
                  });
                }
                if (reply == null || (sets.isEmpty() && !answersEmpty(reply))) {
                  return;
                }
                Options.INSTANCE.setCoachPractice(solveType.getId(), reply, askedAt, with);
                main.post(new Runnable() {
                  @Override
                  public void run() {
                    listener.onPractice(sets);
                  }
                });
              }
            }).start();
          }
        });
  }

  /** A set whose drill this app cannot run is left out rather than drawn with a dead button. */
  static List<Set> parse(String reply) {
    List<Set> sets = new ArrayList<Set>();
    if (reply == null) {
      return sets;
    }
    try {
      JSONArray array = new JSONObject(reply).getJSONArray("sets");
      for (int i = 0; i < array.length(); i++) {
        JSONObject set = array.getJSONObject(i);
        DrillSpec drill;
        try {
          drill = DrillSpec.fromJson(set.getJSONObject("drill").toString());
        } catch (IllegalArgumentException e) {
          continue;
        }
        sets.add(new Set(set.getString("family"), drill.getCases(), drill));
      }
    } catch (JSONException e) {
      return new ArrayList<Set>();
    }
    return sets;
  }

  /** Nothing to practise is an answer worth keeping; text that is no answer at all is not. */
  private static boolean answersEmpty(String reply) {
    try {
      return new JSONObject(reply).getJSONArray("sets").length() == 0;
    } catch (JSONException e) {
      return false;
    }
  }
}
