package com.cube.nanotimer.gui;

import android.graphics.drawable.GradientDrawable;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.CubePatternFormat;
import com.cube.nanotimer.cube.CubeStickering;
import com.cube.nanotimer.gui.widget.CrossSolutionPanel;
import com.cube.nanotimer.gui.widget.DrillOutcomeStrip;
import com.cube.nanotimer.scrambler.ScramblerService;
import com.cube.nanotimer.scrambler.cross.CrossFace;
import com.cube.nanotimer.scrambler.cross.CrossFormatter;
import com.cube.nanotimer.scrambler.cross.CrossSolvers;
import com.cube.nanotimer.scrambler.cross.CrossSolvers.FaceSolutions;
import com.cube.nanotimer.smartcube.drill.CrossDrillRep;
import com.cube.nanotimer.smartcube.drill.CrossDrillSession;
import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.view.DrillRepFlourish;
import com.cube.nanotimer.util.view.DrillRepParts;
import com.cube.nanotimer.util.view.DrillSplitBarView;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.vo.CubeType;

import java.util.List;

/**
 * Runs a cross drill: a whole scramble stands on the screen's cube, and the user has to build the
 * cross on one face in as few moves as there were.
 *
 * <p><b>It is a blind execution drill, not only a move counting one.</b> The scramble opens with the
 * four cross edges and the six centres in colour and everything else grey, and on the user's first
 * turn the <em>whole</em> cube goes grey. So the cross is planned while it can be read and built
 * from memory, which is what inspection actually asks of a solver. The centres are not decoration in
 * that first view: with them grey there is nothing to say which face an edge belongs to and the case
 * cannot be read at all.
 *
 * <p><b>A wrong cross is announced, not detected.</b> A built cross ends its own rep; a wrong one
 * never will, because there is no state that says the user thinks they are finished. The one button
 * is that announcement, and it is not a stop control: pressing it with the cross really built ends
 * the rep the same way turning into it would have.
 *
 * <p><b>Extra moves are a finish, not a miss.</b> The colours come back exactly as they do on a rep
 * that found the short way, and what is said is that there was a shorter one. The search runs the
 * moment the scramble is drawn, on a background thread, so the solutions are waiting by the time
 * anyone asks for them.
 *
 * <p><b>The three finishes have to be told apart without being read.</b> A cross built in the fewest
 * moves there were is the whole point of the drill, and for as long as it was scored in one grey line
 * of small print it looked like the two finishes that are not that. So each has a beat of its own
 * over the cube, loud, quiet and none, and a verdict of its own beside the count. What the beat says
 * the {@link Verdict} decides; how it says it is {@link DrillRepFlourish}'s business.
 */
public class CrossDrillActivity extends DrillScreenActivity {

  /** The drill to run, as its JSON text. */
  public static final String EXTRA_SPEC = "drillSpec";

  /**
   * What a finished rep came to. Named as one thing because the screen has to say it in three places
   * at once and they must not be able to disagree: the beat over the cube, the verdict beside the
   * count, and the badge.
   */
  private enum Verdict {
    /** Announced finished with the cross not there, which is the only way a wrong one ends. */
    MISSED,
    /** Built, and there was no shorter way. What the drill is for. */
    OPTIMAL,
    /** Built, and there was a shorter way. A finish, and not the one that was being practised. */
    LONGER,
    /** Built, and the search that says which of the two it was has not landed yet. */
    UNSCORED
  }

  /**
   * How long the user may stop turning, mid-rep, before the screen says what it can see. Long
   * enough not to talk over somebody thinking, short enough to answer the question they are asking
   * themselves when they stop, which is "is that it?".
   */
  private static final long STALLED_MS = 3500;

  /** How many scrambles to refuse for having their cross already built before taking one anyway. */
  private static final int MAX_DEAL_ATTEMPTS = 5;

  /** The step family the summary takes its colour from. */
  private static final String CROSS_FAMILY = "cross";

  /**
   * How long a stepped turn takes to draw. Several times the speed the cube is mirrored at, which
   * is set so the drawn cube is never seen lagging the hands and is far too fast to follow: a move
   * you are being shown has to be watchable, and one that is over before the eye finds the face
   * only leaves a cube that changed.
   */
  private static final int STEP_TURN_MS = 280;

  /**
   * And a half turn, which is twice as far to go. Not twice as long: an R2 drawn over the time two
   * turns take reads as two turns rather than as the one it is.
   */
  private static final int STEP_HALF_TURN_MS = 380;

  private final CrossSolvers solvers = new CrossSolvers();
  private final Handler handler = new Handler(Looper.getMainLooper());

  private CrossDrillSession session;
  private CrossFace face;
  /** What this drill is called, for the summary once the bar that named it has gone. */
  private String label;

  /** The optimal solutions for the scramble on screen, or null while the search is still running. */
  private FaceSolutions solutions;
  /** Which rep a search belongs to, so one that lands late is dropped rather than shown. */
  private int repSeq;

  private TextView tvStatus;
  /** The status line's pill, which gives way to the split between reps. */
  private View statusPill;
  private DrillSplitBarView repSplit;
  /** The whole rep line, since the beat scales it. */
  private View lastRepRow;
  private Button btDone;
  private CrossSolutionPanel panel;
  private View repWash;
  private View repBadge;

  /**
   * The verdict the beat on screen was played for, or null before there has been one. A search that
   * lands after its own rep turns an unscored finish into a scored one, and the beat that is owed
   * has to be able to tell that from a beat it has already played.
   */
  private Verdict beatPlayed;

  /**
   * Whether the cube has been turned since the rep ended. The ways are walked on the same cube the
   * beat washes over, so a beat that arrives late has to give way: covering the moves the user has
   * just asked to be shown is worse than not celebrating at all.
   */
  private boolean exploring;

  /** Between reps: the cube stands finished and the button takes the next scramble. */
  private boolean betweenReps;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.crossdrill_screen);
    bindDrillScreen();

    tvStatus = findViewById(R.id.tvDrillCrossStatus);
    statusPill = findViewById(R.id.drillStatusPill);
    repSplit = findViewById(R.id.vDrillRepSplit);
    lastRepRow = findViewById(R.id.llDrillLastRep);
    btDone = findViewById(R.id.btDrillCrossDone);
    panel = findViewById(R.id.crossSolutionPanel);
    repWash = findViewById(R.id.drillRepWash);
    repBadge = findViewById(R.id.tvDrillCrossBadge);
    fitToOrientation();

    btDone.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (betweenReps) {
          nextRep();
        } else {
          declareFinished();
        }
      }
    });
    // The panel turns the cube rather than doing it itself: the drawn cube and the session's own
    // are one picture, and only this screen holds both.
    panel.setListener(new CrossSolutionPanel.Listener() {
      @Override
      public void onSolutionMove(String notation) {
        turnExploring(notation);
      }

      @Override
      public void onRewind() {
        backToStart();
      }
    });
    findViewById(R.id.btDrillDone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });

    DrillSpec spec;
    try {
      spec = DrillSpec.fromJson(getIntent().getStringExtra(EXTRA_SPEC));
      session = new CrossDrillSession(spec);
      face = CrossFace.valueOf(spec.getCrossFace());
    } catch (RuntimeException e) {
      session = null; // half-read is not runnable, and nothing may take it for a drill in progress
      showUnavailable(getString(R.string.drill_spec_unreadable));
      return;
    }
    // Before the first rep: the panel writes the user's own moves in this frame from their first
    // turn, which is long before there is a solution to show.
    panel.setCrossFace(face);
    label = spec.getLabel() == null ? getString(R.string.drill_cross_title) : spec.getLabel();
    setTitle(label);
    initRecording(spec);

    startWhenCubeConnected();
  }

  @Override
  protected void startDrill() {
    nextRep();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    handler.removeCallbacksAndMessages(null);
    DrillRepFlourish.cancel(repWash, lastRepRow, repBadge);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    fitToOrientation();
  }

  /**
   * The count and the button are smaller on its side, where the reading column is short. Set here
   * because a rotation does not recreate this screen, so the layout's sizes are not re-read.
   */
  private void fitToOrientation() {
    setRepValueSize(getResources().getDimension(R.dimen.drill_cross_count_size));
    btDone.getLayoutParams().height =
        getResources().getDimensionPixelSize(R.dimen.drill_cross_button_height);
    btDone.requestLayout();
  }

  /** On the bare ground in its pool of shadow, like the other drills' cubes. */
  @Override
  protected boolean standsOnMat() {
    return true;
  }

  @Override
  protected boolean isDrillRunning() {
    return session != null;
  }

  /** A cross announced finished without being there is a rep, but not a solved one. */
  @Override
  protected boolean hasSolvedRep() {
    for (CrossDrillRep rep : session.getReps()) {
      if (rep.isBuilt()) {
        return true;
      }
    }
    return false;
  }

  /** The scramble is on screen, which is where the planning runs from. */
  @Override
  protected void onCaseVisible() {
    if (session.isRunning()) {
      startPlanning();
    }
  }

  /** An untouched scramble was in view the whole pause, so a fresh one is dealt to plan. */
  @Override
  protected void onCubeBack() {
    if (cubeReady && !betweenReps && session.isRunning() && session.getMoveCount() == 0) {
      showStatus(getString(R.string.drill_cross_dealing));
      dealScramble(0);
    }
  }

  /**
   * A turn made before the scramble is on screen is dropped, not queued. The first one of a rep
   * takes the colours with it: from there the cross is built from what was read, not from what can
   * still be seen.
   */
  @Override
  public void onMove(CubeMove move) {
    if (!cubeReady || finished || session == null) {
      return;
    }
    // The rep is over but the cube is still theirs to turn: this is where they try the short way,
    // or find out what their own extra move did. Nothing is timed and nothing is scored.
    if (!session.isRunning()) {
      exploring = true;
      cube.addMove(move.getNotation());
      session.explore(move);
      panel.onCubeTurned(move.getNotation()); // walks the shown way when it is the next move of it
      return;
    }
    boolean first = session.getMoveCount() == 0;
    cube.addMove(move.getNotation());
    CrossDrillRep rep = session.onMove(move);
    if (first) {
      handler.removeCallbacks(planningRanOut);
    }
    if (rep != null) {
      onRepFinished(rep); // which brings the colours back, so greying first would only flicker
      return;
    }
    if (first) {
      cube.setStickering(CubeStickering.allGrey());
    }
    panel.setYourMoves(session.getFoldedMoves());
    showStatus(getString(R.string.drill_cross_moves, session.getMoveCount()));
    armStalledHint();
  }

  /** The user says they are finished. A cross that is really there ends the same way either way. */
  private void declareFinished() {
    CrossDrillRep rep = session.declareFinished();
    if (rep != null) {
      onRepFinished(rep);
    }
  }

  private void onRepFinished(CrossDrillRep rep) {
    recorder.record(rep);
    handler.removeCallbacks(planningRanOut);
    handler.removeCallbacks(stalledHint);
    betweenReps = true;
    lastRepRow.setVisibility(View.VISIBLE);
    statusPill.setVisibility(View.GONE); // the split under the count says what it used to
    showRepStrip(session.getReps().size() - 1);

    // The cross comes back and nothing else does. Every rep ends looking like the one thing the
    // drill was about, whether it was found the short way or the long one, and the four edges stay
    // lit through whatever is turned next so their own extra move can be seen undoing it.
    cube.setStickering(CubeStickering.crossAndCentres(session.getCrossEdges()));
    panel.setYourMoves(session.getFoldedMoves()); // including the move that ended the rep
    showLastRep(rep);
    // Before the ways go up rather than after: the beat reads the rep, and nothing it reads should
    // be able to change because of what putting a solution on screen happens to do.
    playRepBeat(rep);
    showBetweenReps(rep);
    btDone.setText(R.string.drill_cross_next);
  }

  /**
   * The shortest ways there were.
   *
   * <p>The ways are put in front of the user rather than behind a button: the rep is over, so
   * there is nothing left to give away, and a sequence you have to go and ask for is one you will
   * not try. Shown for a rep that found one of them too, which is not a spoiler by then and is the
   * only chance the drill has to show the other thirty-eight.
   */
  private void showBetweenReps(CrossDrillRep rep) {
    if (solutions != null) {
      panel.showSolutions(solutions.solutions, solutions.length);
    }
  }

  /**
   * Puts the scramble back as it was dealt, so the short way can be turned on it and watched. The
   * only rewind this drill has, and only between reps: mid-rep it would hand back the look the cube
   * going grey just took away.
   */
  private void backToStart() {
    exploring = true;
    session.resetToStart();
    cube.setState(CubePatternFormat.format(session.getFacelets()));
  }

  /**
   * Turns both cubes by one move of a way the user is walking. The drawn cube takes the move whole,
   * since a half turn drawn as two quarters reads as a stutter; the session takes the quarter turns
   * a cube would have reported, which is the only thing it is ever fed.
   *
   * <p>Drawn slowly, unlike every other move this screen draws: this one is being shown rather than
   * mirrored, and nobody's hands are waiting for it.
   */
  private void turnExploring(String notation) {
    exploring = true;
    cube.addMove(notation, notation.endsWith("2") ? STEP_HALF_TURN_MS : STEP_TURN_MS);
    for (String quarter : CrossSolutionPanel.quartersOf(notation)) {
      session.explore(new CubeMove(Face.valueOf(quarter.substring(0, 1)),
          quarter.endsWith("'"), System.currentTimeMillis()));
    }
  }

  /**
   * Cross on the bottom, which is the one frame this screen reads in: the panel writes both its
   * rows there, behind the very rotation returned here. A cube let go of and left square would
   * spell the same turn the other way round from the moves under it.
   *
   * <p>A D cross is already there and stands square. Only where the cube is <em>not</em> following
   * a grip: a mirror stands where the hands do, whatever frame the moves are written in.
   */
  @Override
  protected CubeOrientation restingRotation() {
    CubeRotation rotation = CubeRotation.byNotation(CrossFormatter.rotationPrefix(face));
    return rotation == null ? null : rotation.quaternion();
  }

  /** Cross on the bottom, so the face opposite it is held on top. */
  @Override
  protected char straightenUpFace() {
    return face == null ? 0 : face.opposite().name().charAt(0);
  }

  private void nextRep() {
    betweenReps = false;
    solutions = null;
    beatPlayed = null;
    exploring = false;
    DrillRepFlourish.cancel(repWash, lastRepRow, repBadge); // a beat cut short by a quick Next
    // The verdict belonged to the cross that is over. Left up, "That was not the cross" reads as a
    // verdict on the scramble about to be dealt.
    clearLastRep();
    // Taken away rather than emptied: a row holding its place under the cube for the length of a
    // rep is a band of nothing where the eye goes looking for the cube.
    lastRepRow.setVisibility(View.GONE);
    statusPill.setVisibility(View.VISIBLE);
    panel.clear();
    btDone.setText(R.string.drill_cross_done);
    if (session.isFinished()) {
      showSummary();
      return;
    }
    showStatus(getString(R.string.drill_cross_dealing));
    dealScramble(0);
  }

  /**
   * Fetches a scramble off the UI thread and puts it up, redealing one whose cross happens to be
   * there already, since that is a rep of nothing. Bounded because a scrambler handing back nothing
   * would otherwise redeal for ever.
   */
  private void dealScramble(final int attempt) {
    final int seq = ++repSeq;
    new Thread(new Runnable() {
      @Override
      public void run() {
        final String scramble = nextScramble();
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (seq != repSeq || finished || isFinishing()) {
              return;
            }
            // No scramble to be had ends the drill where it stands rather than hanging on one:
            // the reps already done are the result, which is the rule everything else here follows.
            if (scramble == null || !session.nextRep(scramble)) {
              showSummary();
              return;
            }
            if (session.isCrossBuilt() && attempt < MAX_DEAL_ATTEMPTS) {
              dealScramble(attempt + 1);
              return;
            }
            showScramble(scramble);
          }
        });
      }
    }).start();
  }

  private String nextScramble() {
    String[] scramble = ScramblerService.INSTANCE.getScramble(CubeType.THREE_BY_THREE, null);
    if (scramble == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (String move : scramble) {
      sb.append(sb.length() == 0 ? "" : " ").append(move);
    }
    return sb.toString();
  }

  /** Four edges and six centres, and nothing else readable, which is the case to be planned. */
  private void showScramble(String scramble) {
    cube.setState(CubePatternFormat.format(session.getFacelets()));
    cube.setStickering(CubeStickering.crossAndCentres(session.getCrossEdges()));
    setProgress(session.getReps().size() + 1, session.getSpec().getReps());
    showRepStrip(session.getReps().size());
    solveInBackground(scramble, repSeq);
    if (cubeReady) {
      startPlanning();
    }
  }

  /**
   * The search runs while the user looks, so the answer is waiting the moment they finish and
   * nobody is left watching a spinner having just done the work.
   */
  private void solveInBackground(final String scramble, final int seq) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        final FaceSolutions found = solvers.solveFace(face, scramble);
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (seq != repSeq || finished) {
              return; // a search that landed after its own rep was replaced, or after the drill
            }
            solutions = found;
            session.setOptimalLength(found.length);
            if (betweenReps && !session.getReps().isEmpty()) {
              // Landed after its own rep ended, so the line it belongs under is already up, and so
              // is the row it belongs in.
              recorder.setLastOptimalLength(found.length);
              List<CrossDrillRep> reps = session.getReps();
              CrossDrillRep last = reps.get(reps.size() - 1);
              showLastRep(last);
              showRepStrip(reps.size() - 1);
              playRepBeat(last); // which only has anything left to play if it turns out to be the best
              showBetweenReps(last);
            }
          }
        });
      }
    }).start();
  }

  private void startPlanning() {
    session.markCaseShown(System.currentTimeMillis());
    long limit = session.getSpec().getPlanningMs();
    handler.removeCallbacks(planningRanOut);
    if (limit > 0) {
      countdownFrom(limit);
      handler.postDelayed(planningRanOut, limit);
    } else {
      showStatus(getString(R.string.drill_cross_plan_it));
    }
  }

  /** Seconds left to look, redrawn each one, and only while nothing has been turned. */
  private void countdownFrom(final long remaining) {
    showStatus(getString(R.string.drill_cross_planning_left, (int) Math.ceil(remaining / 1000d)));
    if (remaining > 1000) {
      handler.postDelayed(new Runnable() {
        @Override
        public void run() {
          if (session.isRunning() && session.getMoveCount() == 0) {
            countdownFrom(remaining - 1000);
          }
        }
      }, 1000);
    }
  }

  /** Time is up: the colours go, exactly as a first turn would have taken them. */
  private final Runnable planningRanOut = new Runnable() {
    @Override
    public void run() {
      if (session.isRunning() && session.getMoveCount() == 0) {
        session.markPlanningExpired();
        cube.setStickering(CubeStickering.allGrey());
        showStatus(getString(R.string.drill_cross_planning_over));
      }
    }
  };

  private void armStalledHint() {
    handler.removeCallbacks(stalledHint);
    handler.postDelayed(stalledHint, STALLED_MS);
  }

  /**
   * They have stopped turning, which usually means they think they are done. Saying what the cube
   * really shows is the only thing the screen knows that they do not; it does not end the rep, since
   * announcing the finish stays theirs.
   */
  private final Runnable stalledHint = new Runnable() {
    @Override
    public void run() {
      if (session.isRunning() && session.getMoveCount() > 0 && !session.isCrossBuilt()) {
        showStatus(getString(R.string.drill_cross_not_yet, session.getMoveCount()));
      }
    }
  };

  private void showStatus(String text) {
    tvStatus.setText(text);
  }

  private static Verdict verdictOf(CrossDrillRep rep) {
    if (!rep.isBuilt()) {
      return Verdict.MISSED;
    }
    if (rep.getOptimalLength() <= 0) {
      return Verdict.UNSCORED;
    }
    return rep.getExtraMoves() == 0 ? Verdict.OPTIMAL : Verdict.LONGER;
  }

  /**
   * The beat the rep gets over the cube: the loud one for a cross that could not have been shorter,
   * the quiet one for a cross that could, and none at all for a cross that was not there.
   *
   * <p>An unscored finish takes the quiet beat and can still be celebrated later. Nothing here moves
   * on by itself, so a search landing after its own rep can pay what it turns out to owe, and the
   * quiet beat it already had is not replayed for the same finish twice. What it may not do is
   * celebrate over a cube the user has since started turning: by then the screen is showing them
   * something they asked for, and a wash over it is in the way.
   */
  private void playRepBeat(CrossDrillRep rep) {
    Verdict verdict = verdictOf(rep);
    if (verdict == Verdict.OPTIMAL && beatPlayed != Verdict.OPTIMAL && !exploring) {
      DrillRepFlourish.celebrate(repWash, lastRepRow, repBadge);
    } else if (beatPlayed == null && verdict != Verdict.MISSED) {
      DrillRepFlourish.playQuietly(repWash, lastRepRow);
    }
    beatPlayed = verdict;
  }

  /**
   * The rep that has just ended: what it cost in moves, and the verdict on that count. The two of
   * them say what the shortest way was, so it is not spelled out a third time beside them.
   */
  private void showLastRep(CrossDrillRep rep) {
    Verdict verdict = verdictOf(rep);
    if (verdict == Verdict.MISSED) {
      // Stood down rather than announced, and in the ink of a miss: there is no move count to read
      // here, only a verdict. The moves went somewhere else.
      setLastRep(null, 0, getString(R.string.drill_cross_rep_missed), R.color.danger_text, true,
          null);
      repSplit.setVisibility(View.GONE);
      return;
    }
    List<DrillRepParts.Part> parts = DrillRepParts.planning(rep.getPlanningMs(),
        rep.getExecutionMs(), StepPalette.cfop(this).colorFor(CROSS_FAMILY));
    DrillRepParts.draw(repSplit, parts);
    repSplit.setVisibility(View.VISIBLE);
    CharSequence split = DrillRepParts.words(this, parts);
    String moves = getString(R.string.drill_cross_rep_moves, rep.getMoveCount());
    if (verdict == Verdict.UNSCORED) {
      setLastRep(null, 0, moves, R.color.white, false, split);
      return;
    }
    boolean optimal = verdict == Verdict.OPTIMAL;
    setLastRep(
        optimal ? getString(R.string.drill_cross_verdict_optimal)
            : getString(R.string.drill_cross_verdict_extra, rep.getExtraMoves()),
        optimal ? R.color.green_soft : R.color.warning,
        moves, optimal ? R.color.green_soft : R.color.white, false, split);
    fitCountToRow();
  }

  /**
   * Shrinks the move count when it and its verdict would not fit on one line, which only a long
   * word for "moves" does ("6 mouvements"). Never grows it past the layout's size.
   */
  private void fitCountToRow() {
    lastRepRow.post(new Runnable() {
      @Override
      public void run() {
        TextView count = findViewById(R.id.tvDrillLastRep);
        View verdict = findViewById(R.id.tvDrillLastRepName);
        float size = getResources().getDimension(R.dimen.drill_cross_count_size);
        count.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        int room = lastRepRow.getWidth() - lastRepRow.getPaddingLeft()
            - lastRepRow.getPaddingRight();
        if (verdict.getVisibility() == View.VISIBLE) {
          verdict.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
          room -= verdict.getMeasuredWidth()
              + ((ViewGroup.MarginLayoutParams) verdict.getLayoutParams()).leftMargin;
        }
        float width = count.getPaint().measureText(count.getText().toString());
        if (room > 0 && width > room) {
          count.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * room / width);
        }
      }
    });
  }

  /** The drill as a strip: each rep by how it finished, the rep given ringed. */
  private void showRepStrip(int current) {
    List<CrossDrillRep> reps = session.getReps();
    int[] done = new int[reps.size()];
    for (int i = 0; i < done.length; i++) {
      done[i] = outcomeOf(reps.get(i));
    }
    setRepStrip(done, session.getSpec().getReps(), current);
  }

  @Override
  protected void showSummary() {
    if (finished) {
      return; // a cube unplugged on the summary screen must not re-run this over its own figures
    }
    finished = true;
    handler.removeCallbacksAndMessages(null);
    showSummaryFor(StepPalette.cfop(this).colorFor(CROSS_FAMILY));

    List<CrossDrillRep> reps = session.getReps();
    setSummaryEyebrow(finishedAfter(reps.size()));
    showRepRows(reps);

    // A rep whose cross was not there is counted apart rather than folded in: its moves went
    // somewhere else, and averaging them in would flatter a drill that kept missing.
    int built = 0;
    int optimal = 0;
    int extraTotal = 0;
    long planningTotal = 0;
    int[] outcomes = new int[reps.size()];
    for (int i = 0; i < reps.size(); i++) {
      CrossDrillRep rep = reps.get(i);
      outcomes[i] = outcomeOf(rep);
      if (!rep.isBuilt()) {
        continue;
      }
      built++;
      extraTotal += rep.getExtraMoves();
      planningTotal += rep.getPlanningMs();
      if (outcomes[i] == DrillOutcomeStrip.OPTIMAL) {
        optimal++;
      }
    }
    if (built == 0) {
      showSummaryEmpty(R.string.drill_cross_summary_none_built);
      hideSummaryCells();
      return;
    }
    setSummaryHero(String.valueOf(optimal), getString(R.string.drill_summary_cell_of, built),
        getString(R.string.drill_cross_summary_found));
    setSummaryStrip(outcomes, new CharSequence[] {getString(R.string.drill_cross_verdict_optimal),
        getString(R.string.drill_summary_cell_extra), getString(R.string.drill_cross_row_missed),
        null});
    String average = getString(R.string.drill_summary_cell_average);
    setSummaryCell(0, getString(R.string.drill_summary_cell_extra),
        String.format("%.1f", extraTotal / (double) built), average);
    setSummaryCell(1, getString(R.string.drill_summary_cell_planning),
        FormatterService.INSTANCE.formatSolveTime(planningTotal / built), average);
  }

  /** How a rep finished, for the strip and its line: a rep with no shortest to compare is unscored. */
  private static int outcomeOf(CrossDrillRep rep) {
    if (!rep.isBuilt()) {
      return DrillOutcomeStrip.MISSED;
    }
    if (rep.getOptimalLength() <= 0) {
      return DrillOutcomeStrip.UNSCORED;
    }
    return rep.getExtraMoves() == 0 ? DrillOutcomeStrip.OPTIMAL : DrillOutcomeStrip.LONGER;
  }

  /** One line per rep, in the order dealt: its moves against the shortest, and its planning. */
  private void showRepRows(List<CrossDrillRep> reps) {
    LinearLayout rows = findViewById(R.id.llDrillCrossRepRows);
    rows.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(this);
    for (int i = 0; i < reps.size(); i++) {
      CrossDrillRep rep = reps.get(i);
      View row = inflater.inflate(R.layout.drill_cross_rep_row, rows, false);
      ((TextView) row.findViewById(R.id.tvDrillCrossRepNumber)).setText(String.valueOf(i + 1));
      TextView moves = row.findViewById(R.id.tvDrillCrossRepMoves);
      TextView planning = row.findViewById(R.id.tvDrillCrossRepPlanning);
      if (rep.isBuilt()) {
        moves.setText(getString(R.string.drill_cross_rep_moves, rep.getMoveCount()));
        planning.setText(FormatterService.INSTANCE.formatSolveTime(rep.getPlanningMs()));
      } else {
        moves.setText(R.string.drill_cross_row_missed);
        moves.setTextColor(ContextCompat.getColor(this, R.color.secondary_text));
        planning.setText(R.string.drill_case_no_time);
        planning.setTextColor(ContextCompat.getColor(this, R.color.secondary_text));
      }
      TextView verdict = row.findViewById(R.id.tvDrillCrossRepVerdict);
      int outcome = outcomeOf(rep);
      if (outcome == DrillOutcomeStrip.OPTIMAL || outcome == DrillOutcomeStrip.LONGER) {
        boolean shortest = outcome == DrillOutcomeStrip.OPTIMAL;
        verdict.setText(shortest ? getString(R.string.drill_cross_verdict_optimal)
            : getString(R.string.drill_cross_verdict_extra, rep.getExtraMoves()));
        verdict.setTextColor(ContextCompat.getColor(this, R.color.on_accent));
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(11 * getResources().getDisplayMetrics().density);
        chip.setColor(ContextCompat.getColor(this,
            shortest ? R.color.green_soft : R.color.warning));
        verdict.setBackground(chip);
      } else {
        verdict.setVisibility(View.GONE);
      }
      rows.addView(row);
    }
    findViewById(R.id.llDrillCrossReps).setVisibility(reps.isEmpty() ? View.GONE : View.VISIBLE);
  }
}
