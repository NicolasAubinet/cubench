package com.cube.nanotimer.gui;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.CoachPayloadBuilder;
import com.cube.nanotimer.cube.AlgorithmFollower;
import com.cube.nanotimer.cube.CaseFlags;
import com.cube.nanotimer.cube.CubePatternFormat;
import com.cube.nanotimer.cube.CubeStickering;
import com.cube.nanotimer.cube.SolveTypeMethod;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.drill.AlgorithmTimes;
import com.cube.nanotimer.smartcube.drill.CaseTally;
import com.cube.nanotimer.smartcube.drill.DrillRep;
import com.cube.nanotimer.smartcube.drill.DrillRepOrder;
import com.cube.nanotimer.smartcube.drill.DrillSession;
import com.cube.nanotimer.smartcube.drill.LayerRotation;
import com.cube.nanotimer.smartcube.drill.PairWeights;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerCaseNames;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.gui.widget.DrillCasePips;
import com.cube.nanotimer.gui.widget.DrillCaseTable;
import com.cube.nanotimer.gui.widget.DrillLiveCells;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.StepCardBackground;
import com.cube.nanotimer.gui.widget.dialog.CaseAlgorithmsDialog;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.ScrambleFormatterService;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.gui.widget.GroupLearnedPanel;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.step.LastLayerAlgorithms;
import com.cube.nanotimer.step.OllShapes;
import com.cube.nanotimer.vo.CaseHistory;
import com.cube.nanotimer.util.view.DrillRepFlourish;
import com.cube.nanotimer.util.view.LearningAlgorithmFade;
import com.cube.nanotimer.util.view.PracticeOfferPulse;
import com.cube.nanotimer.util.view.ScrambleFollowAnimator;
import com.cube.nanotimer.util.view.DrillControlLabels;
import com.cube.nanotimer.util.view.DrillRepParts;
import com.cube.nanotimer.util.view.DrillSplitBarView;
import com.cube.nanotimer.util.view.StepPalette;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs a case drill: the case stands on the screen's cube, the smart cube is only the input, and the
 * user's own cube ends up scrambled, which is expected and needs nothing done about it.
 *
 * <p><b>There is no control that starts or stops a rep, and none may be added.</b> The cube reports
 * both ends of one: a rep begins when the case is put up and ends the moment
 * {@code CubieCube.isSolved} comes back true. Case after case with nothing to press is most of what
 * makes this feel unlike a scramble list. Neither button is an exception: one spends the rep in
 * front of the user, the other deals it again.
 *
 * <p><b>The case on screen is never named.</b> Naming it would hand over the answer, and a
 * recognition drill would be timing reading instead. A case is named once its rep is over, beside
 * that rep's time, and goes again with the case it belongs to: left standing into the next rep it
 * reads as naming the case being worked, which is the thing this screen is careful about. It
 * belongs beside the time and nowhere else, since set under the cube at headline size it read as a
 * caption for the cube, and so again as the name of the case being looked at.
 */
public class DrillActivity extends DrillScreenActivity implements DrillCaseTable.Listener {

  /** The drill to run, as its JSON text. */
  public static final String EXTRA_SPEC = "drillSpec";

  /**
   * The face to deal the last layer onto, as its letter. Not on the spec: which colour a solver
   * finishes on is theirs, and a drill prescribed from outside has no way of knowing it.
   */
  public static final String EXTRA_LAYER_FACE = "drillLayerFace";

  /** The solve type whose recent solves lean an F2L pair drill's draw. Optional. */
  public static final String EXTRA_SOLVE_TYPE = "drillSolveType";

  /**
   * How long the solved cube stays up before the next case replaces it.
   *
   * <p>The length of the green beat, so the wash lands on the case it belongs to rather than over
   * its successor. The rep's line is not on this clock: it outlives its case and only stands back
   * when the next one is dealt, because one beat is not long enough to read. The timing does not
   * care either way, since recognition runs from when the next case is <em>shown</em>, so the hold
   * is charged to nobody.
   */
  private static final long REP_HOLD_MS = DrillRepFlourish.PLAY_BEAT_MS;

  /** The family a case belongs to, which is the colour its name is written in everywhere. */
  private static final String FAMILY_OLL = "oll_";

  private DrillSession session;
  /** What this drill is called, for the summary once the bar that named it has gone. */
  private String label;
  /** The face the user finishes on, which is the one the drawn cube has to stand on. */
  private String layerFace;
  /** The one family this drill deals, whose colour the meter and the mean cell are written in. */
  private int familyHue;
  /** A case still to clear, on a coach drill's summary: a faint wash of the drill's colour. */
  private static final int UNCLEARED_ALPHA = 0x10;

  /** The whole rep line, since the beat scales it and the hold is posted to it. */
  private View lastRepRow;
  private View repWash;
  /** A coach drill's cases, its verdict on a rep, and where that rep's time went. */
  private DrillCasePips casePips;
  private TextView repVerdict;
  private DrillSplitBarView repSplit;
  /** The rep the line is about, which the case bars ring; null before one or after practice. */
  private DrillRep lastRep;
  /** The offer to practise a case just turned off its list, and the case it is for. */
  private TextView practiceOffer;
  private String offeredCase;
  private TextView practiceAlgorithm;
  /** The algorithm line with its case picture, shown and faded as one. */
  private View practiceAlgorithmRow;
  private LastLayerCaseView practiceCasePicture;
  /** Brings a learning drill's faded algorithm back for the rep up, at the cost of its run. */
  private Button showAlg;
  /** Whether the rep up had its algorithm brought back. */
  private boolean shownThisRep;
  /**
   * A learning drill's cases with no algorithm kept yet, each chosen for before the first rep. The
   * case is practised meanwhile, so whichever algorithm is tapped can be tried on the cube.
   */
  private final List<String> toChoose = new ArrayList<String>();
  private int chooseIndex;
  /** The case being chosen for, the algorithm it starts on, and the one being tried; null outside. */
  private String choosing;
  private String firstAlgorithm;
  private String tryingAlgorithm;
  /** Choosing from the drill (Alg or the offer), rather than at a learning drill's start. */
  private boolean choosingToPractise;
  private LinearLayout chooseList;
  /** Greys the practised algorithm's moves as they are turned; null where there is none to follow. */
  private AlgorithmFollower algorithmFollower;
  private ScrambleFollowAnimator algorithmAnimator;
  /** The scramble's own face: an algorithm being followed reads like a scramble being turned. */
  private Typeface algorithmFace;
  private Button skip;
  /** For a coach learning drill: the cases the solves know, those cleared here, a group to fete. */
  private final Set<String> knownCases = new HashSet<String>();
  private final Set<String> clearedHere = new HashSet<String>();
  private String groupToCelebrate;
  /** Back leaves practice for the drill rather than ending the drill. */
  private final OnBackPressedCallback practiceBack = new OnBackPressedCallback(false) {
    @Override
    public void handleOnBackPressed() {
      if (!choosingToPractise) {
        finish(); // no rep has been dealt, so there is no drill to sum up
        return;
      }
      endChoosing();
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.drill_screen);
    bindDrillScreen();

    lastRepRow = findViewById(R.id.llDrillLastRep);
    repWash = findViewById(R.id.drillRepWash);
    casePips = findViewById(R.id.vDrillCasePips);
    repVerdict = findViewById(R.id.tvDrillLastRepVerdict);
    repSplit = findViewById(R.id.vDrillRepSplit);
    casePips.setBars(true);
    practiceOffer = findViewById(R.id.tvDrillPracticeOffer);
    practiceAlgorithm = findViewById(R.id.tvDrillPracticeAlg);
    algorithmFace = Typeface.createFromAsset(getAssets(), "fonts/JetBrainsMono-Medium.ttf");
    practiceAlgorithm.setTypeface(algorithmFace);
    practiceAlgorithm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
    practiceAlgorithmRow = findViewById(R.id.llDrillPracticeAlg);
    practiceCasePicture = findViewById(R.id.cvDrillPracticeCase);
    showAlg = findViewById(R.id.btDrillShowAlg);
    showAlg.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showAlgorithmAgain();
      }
    });
    chooseList = findViewById(R.id.llDrillChoose);
    findViewById(R.id.btDrillUseThis).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        useTriedAlgorithm();
      }
    });
    Button chooseReset = findViewById(R.id.btDrillChooseReset);
    chooseReset.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        resetRep();
      }
    });
    algorithmAnimator = new ScrambleFollowAnimator(practiceAlgorithm);
    Drawable bulb = ContextCompat.getDrawable(this, R.drawable.ic_case_idea).mutate();
    bulb.setTint(ContextCompat.getColor(this, R.color.on_accent));
    int bulbSize = getResources().getDimensionPixelSize(R.dimen.drill_practice_offer_icon);
    bulb.setBounds(0, 0, bulbSize, bulbSize);
    practiceOffer.setCompoundDrawables(bulb, null, null, null);
    practiceOffer.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        enterPractice();
      }
    });
    getOnBackPressedDispatcher().addCallback(this, practiceBack);
    getSupportFragmentManager().setFragmentResultListener(CaseAlgorithmsDialog.FLAGS_CHANGED,
        this, new FragmentResultListener() {
          @Override
          public void onFragmentResult(String key, Bundle result) {
            if (choosing != null) {
              // A star or a typed algorithm in the popup is the one tried next.
              String starred = Options.INSTANCE.getCaseAlgorithm(choosing);
              if (starred != null) {
                tryingAlgorithm = starred;
                session.setPracticeAlgorithm(starred);
              }
              buildChooseRows();
            }
            showPracticeAlgorithm(); // a pick in the case popup is the algorithm practised
            if (session != null && session.getSpec().isLearning()) {
              teachAll(); // and the one a learning drill teaches
            }
          }
        });
    getSupportFragmentManager().registerFragmentLifecycleCallbacks(
        new FragmentManager.FragmentLifecycleCallbacks() {
          @Override
          public void onFragmentViewDestroyed(FragmentManager fm, Fragment f) {
            if (f instanceof CaseAlgorithmsDialog) {
              restartPractice();
            }
          }
        }, false);

    skip = findViewById(R.id.btDrillSkip);
    skip.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        DrillRep rep = session == null ? null : session.abandon();
        if (rep != null) {
          onRepFinished(rep);
        }
      }
    });
    // The three controls, each with its icon
    DrillControlLabels.apply(
        new Button[] {findViewById(R.id.btDrillAlgorithm), findViewById(R.id.btDrillReset), skip},
        new int[] {R.drawable.ic_drill_alg, R.drawable.ic_drill_reset, R.drawable.ic_drill_skip},
        new int[] {R.string.drill_case_algorithm, R.string.drill_reset, R.string.drill_skip});
    findViewById(R.id.btDrillAlgorithm).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showAlgorithms();
      }
    });
    findViewById(R.id.btDrillReset).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        resetRep();
      }
    });
    findViewById(R.id.btDrillDone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });

    String json = getIntent().getStringExtra(EXTRA_SPEC);
    DrillSpec spec;
    try {
      // Refused rather than replaced: running a different drill from the one that was sent would
      // report reps against a prescription nobody made.
      spec = DrillSpec.fromJson(json);
    } catch (RuntimeException e) {
      showUnavailable(getString(R.string.drill_spec_unreadable));
      return;
    }
    // A pair's code names no step family, so F2L's colour is asked for by name.
    boolean lookAhead = spec.getType() == DrillSpec.Type.F2L_LOOKAHEAD;
    familyHue = StepPalette.cfop(this).colorFor(spec.getType() == DrillSpec.Type.F2L_PAIR
        || lookAhead ? "f2l" : spec.getCases().isEmpty() ? null : spec.getCases().get(0));
    if (lookAhead) {
      findViewById(R.id.btDrillAlgorithm).setVisibility(View.GONE); // two pairs, no one algorithm
    }
    label = spec.getLabel() == null ? getString(R.string.drill_title) : spec.getLabel();
    setTitle(label);
    layerFace = getIntent().getStringExtra(EXTRA_LAYER_FACE);
    SolveType solveType = (SolveType) getIntent().getSerializableExtra(EXTRA_SOLVE_TYPE);
    if (spec.getType() == DrillSpec.Type.F2L_PAIR && solveType != null) {
      loadPairWeights(spec, solveType);
    } else {
      begin(spec, spec.getType() == DrillSpec.Type.F2L_PAIR
          ? PairWeights.of(spec, null) : null);
    }
  }

  /** The pairs' recent figures lean the draw, so the session waits for them. */
  private void loadPairWeights(final DrillSpec spec, SolveType solveType) {
    App.INSTANCE.getService().getMethodStatistics(solveType, SolveTypeMethod.of(solveType),
        CoachPayloadBuilder.FAMILY_WINDOW, new DataCallback<MethodStatistics>() {
          @Override
          public void onData(final MethodStatistics statistics) {
            runOnUiThread(new Runnable() {
              @Override
              public void run() {
                if (!isFinishing()) {
                  begin(spec, PairWeights.of(spec, statistics));
                }
              }
            });
          }
        });
  }

  private void begin(DrillSpec spec, Map<String, Long> weights) {
    session = new DrillSession(spec, Utils.getRandom(), weights, layerFace);
    initRecording(spec);
    hidePracticeOffer();
    if (spec.isLearning()) {
      teachAll();
      if (spec.isFromCoach()) {
        loadKnownCases();
      }
      collectCasesToChoose();
    }

    if (!session.isRunnable()) {
      showUnavailable(getString(R.string.drill_no_known_cases));
      return;
    }
    if (spec.retiresCases()) {
      useCaseProgress();
    }
    startWhenCubeConnected();
  }

  /**
   * The first case is queued before the page is up rather than waited for: the view holds it and
   * draws it as its first frame, so the drill never opens on a solved cube it then replaces.
   */
  @Override
  protected void startDrill() {
    if (!toChoose.isEmpty()) {
      chooseNext();
      return;
    }
    nextRep();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    lastRepRow.removeCallbacks(showNextCase);
    DrillRepFlourish.cancel(repWash, lastRepRow);
  }

  @Override
  protected boolean isDrillRunning() {
    return session != null;
  }

  /** A skipped rep or a practice one solves nothing the summary could report. */
  @Override
  protected boolean hasSolvedRep() {
    for (DrillRep rep : session.getReps()) {
      if (!rep.isAbandoned()) {
        return true;
      }
    }
    return false;
  }

  /** The case is on screen, which is where the first rep's recognition runs from. */
  @Override
  protected void onCaseVisible() {
    caseShown();
  }

  @Override
  protected boolean standsOnMat() {
    return true;
  }

  /**
   * The layer stands on top, the way the user is holding their own cube.
   *
   * <p>A case is dealt in the colours' own frame, where white is up whatever colour its owner
   * finishes on, so a last layer on yellow was drawn on the underside of a cube whose top face was
   * a solved white. On a cube with a gyroscope the grip answers this and the page ignores what is
   * asked here; on one without, nothing says how it is held and this is the whole of the answer.
   */
  @Override
  protected CubeOrientation restingRotation() {
    CubeRotation rotation = CubeRotation.byNotation(LayerRotation.toTop(layerFace));
    return rotation == null ? null : rotation.quaternion();
  }

  /**
   * The state the cube says it is really in. A turn it made without reporting is recovered here,
   * and the case is drawn again from where that leaves it: a QiYi drops a state change during fast
   * slices, and the drill that missed one drew a scrambled cube for the rest of the rep and never
   * saw the case solved however well it was.
   */
  @Override
  public void onState(CubeState state) {
    if (!cubeReady || finished || session == null) {
      return;
    }
    boolean between = session.getCurrentCase() == null;
    DrillSession.Correction correction = session.onState(state);
    if (between) {
      if (session.hasCarriedTurns()) {
        showNextCaseNow(); // a turn missed between practice reps starts the next one too
      }
      return;
    }
    if (!correction.isRepaired()) {
      return;
    }
    cube.setState(CubePatternFormat.format(session.getFacelets()));
    if (algorithmFollower != null && isAlgorithmUp()) {
      algorithmFollower.onMissed(correction.getMissed()); // or it reads the next turns as slips
      showAlgorithmProgress();
    }
    if (correction.getRep() != null) {
      onRepFinished(correction.getRep());
    }
  }

  /**
   * A turn made before the case is on screen is dropped, not queued. Both cubes here miss it
   * equally, so they stay in step, and the physical cube's own state never mattered; timing a rep
   * against a case the user could not yet see would.
   *
   * <p>Not in practice: the same case comes next, so a turn during the beat is the next rep
   * started. It ends the beat and goes onto that rep's case.
   */
  @Override
  public void onMove(CubeMove move) {
    if (!cubeReady || finished || session == null) {
      return;
    }
    if (session.getCurrentCase() == null) {
      if (session.isCarrying()) {
        session.onMove(move); // kept for the next practice rep
        showNextCaseNow();
      }
      return;
    }
    if (session.isFromBeforeTheCase(move)) {
      // The previous case's turn delivered late: neither the case nor its algorithm has it
      session.onMove(move);
      return;
    }
    hidePracticeOffer(); // turning the next case is the answer to the offer
    cube.addMove(move.getNotation());
    if (algorithmFollower != null && isAlgorithmUp()) {
      algorithmFollower.onMove(move);
      showAlgorithmProgress();
    }
    DrillRep rep = session.onMove(move);
    if (rep != null) {
      onRepFinished(rep);
    }
  }

  /**
   * Holds the solved cube for a beat before the next case goes up.
   *
   * <p>Not decoration. A case that was replaced the instant it was solved read as though the user
   * had done something wrong, because the only thing they saw was the cube changing under them. The
   * beat costs them nothing: recognition runs from when the next case is <em>shown</em>, so the
   * hold is charged to nobody.
   */
  private void onRepFinished(DrillRep rep) {
    recorder.record(rep);
    noteLearned(rep);
    AlgorithmTimes.Verdict timed = noteChooseTime(rep);
    showAlg.setVisibility(View.GONE); // no case is up during the beat; the next one brings it back
    if (session.getSpec().retiresCases() && !rep.isPractice()) {
      refreshCaseProgress(); // during the beat, so the dot fills beside the rep that earned it
    }
    showLastRep(rep, timed);
    offerPractice(rep);
    // No wash for a rep that was skipped: nothing was solved to dwell on. The hold stays, since
    // what the user could not place is the one thing worth reading off a skipped rep, and the line
    // goes with the case.
    if (isOverTarget(rep)) {
      DrillRepFlourish.playQuietly(repWash, lastRepRow); // green would contradict the red verdict
    } else if (!rep.isAbandoned()) {
      DrillRepFlourish.play(repWash, lastRepRow);
    }
    holdThenNext();
  }

  /** A counted coach-drill rep that missed its case's target. */
  private boolean isOverTarget(DrillRep rep) {
    DrillSpec spec = session.getSpec();
    return spec.retiresCases() && !spec.isLearning() && !rep.isPractice() && !rep.isAbandoned()
        && CaseTally.isClean(rep) && !session.getTally().isWin(rep);
  }

  private void holdThenNext() {
    lastRepRow.removeCallbacks(showNextCase);
    lastRepRow.postDelayed(showNextCase, REP_HOLD_MS);
  }

  /** Ends the beat early, for a practice rep the user has already started turning. */
  private void showNextCaseNow() {
    lastRepRow.removeCallbacks(showNextCase);
    showNextCase.run();
  }

  private final Runnable showNextCase = new Runnable() {
    @Override
    public void run() {
      if (groupToCelebrate != null) {
        celebrate(groupToCelebrate);
        return;
      }
      nextRep();
    }
  };

  /** Every case a learning drill teaches, against the algorithm the screen shows for it. */
  private void teachAll() {
    for (String code : session.getCases()) {
      session.setTaughtAlgorithm(code, learningAlgorithm(code));
    }
  }

  /** The cases the solves say are known, read once for a shape group's completion. */
  private void loadKnownCases() {
    App.INSTANCE.getService().getCaseHistory(0, new DataCallback<CaseHistory>() {
      @Override
      public void onData(final CaseHistory history) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            for (CaseKnowledge known : history.getCases()) {
              if (known.getStatus() == CaseKnowledge.Status.KNOWN) {
                knownCases.add(known.getCode());
              }
            }
          }
        });
      }
    });
  }

  /**
   * A case a learning drill has just cleared counts as learned from now on; in a coach drill, one
   * that completes its shape group is celebrated once the rep's beat is over.
   */
  private void noteLearned(DrillRep rep) {
    String code = rep.getCaseCode();
    if (!session.getSpec().isLearning() || rep.isPractice() || !session.isRetired(code)
        || !clearedHere.add(code)) {
      return;
    }
    // The learn order these feed is OLL and PLL only; a pair has no use there.
    if (!CaseAlgorithms.isPair(code)) {
      Options.INSTANCE.addLearnedCase(code);
    }
    String group = OllShapes.groupOf(code);
    if (!session.getSpec().isFromCoach() || group == null
        || Options.INSTANCE.isGroupCelebrated(group)) {
      return;
    }
    if (learnedCases().containsAll(OllShapes.casesOf(group))) {
      groupToCelebrate = group;
    }
  }

  private Set<String> learnedCases() {
    Set<String> learned = new HashSet<String>(knownCases);
    learned.addAll(Options.INSTANCE.getLearnedCases());
    return learned;
  }

  private void celebrate(String group) {
    groupToCelebrate = null;
    Options.INSTANCE.setGroupCelebrated(group);
    int learned = 0;
    Set<String> cases = learnedCases();
    for (String[] row : LastLayerAlgorithms.ORIENTATIONS) {
      if (cases.contains("oll_" + row[0])) {
        learned++;
      }
    }
    GroupLearnedPanel.show(this, group, OllShapes.casesOf(group), learned,
        LastLayerAlgorithms.ORIENTATIONS.length, new Runnable() {
          @Override
          public void run() {
            if (!finished && !isFinishing()) {
              nextRep();
            }
          }
        });
  }

  /**
   * Deals the same case again, for one botched by a slip rather than by not knowing it. Spends no
   * rep, and there is no hold: the user is waiting on the case they asked to have back.
   */
  private void resetRep() {
    if (session.getCurrentCase() == null) {
      return; // between reps, with the finished cube still up
    }
    session.resetRep();
    cube.setState(CubePatternFormat.format(session.getFacelets()));
    restartAlgorithmProgress();
    if (cubeReady) {
      caseShown();
    }
  }

  /**
   * The algorithms for the case in front of the user, which is the one thing this screen otherwise
   * refuses to say. Someone who has drawn a case they cannot place is not learning anything by
   * staring at it, and the alternative was leaving the drill to go and look it up.
   *
   * <p>They are tried on the cube in the choose step, the same one a coach's offer opens, with the
   * case popup a row away. The rep up is spent, marked as looked up, so it is never read as knowing
   * the case, and the case comes round again later; Back or "Use this one" returns to the drill on
   * a new case. A case with nothing to try gets the popup, the rep going on being timed.
   */
  private void showAlgorithms() {
    String code = session == null || choosing != null ? null : session.getCurrentCase();
    if (code == null || !cubeReady) {
      return; // not yet on screen, so not yet looked at
    }
    session.markRevealed();
    String first = firstToTry(code);
    if (first == null) {
      DialogUtils.showFragment(this, CaseAlgorithmsDialog.newInstance(code));
      return;
    }
    recorder.record(session.lookUp());
    if (session.getSpec().retiresCases()) {
      refreshCaseProgress();
    }
    hidePracticeOffer();
    choosingToPractise = true;
    beginChoosing(code, first);
  }

  /**
   * Offers to practise the case after a coach drill's rep: one turned off the case's list, as the
   * bulb in solve details lights, so never for an algorithm the user muted, picked or typed in; or
   * in a learning drill, a rep where the algorithm did not stick.
   */
  private void offerPractice(DrillRep rep) {
    hidePracticeOffer();
    if (!session.getSpec().isFromCoach() || rep.isPractice()
        || DrillSession.isLookAheadCase(rep.getCaseCode())
        || !(isTurnedOffList(rep) || didNotStick(rep))) {
      return;
    }
    offeredCase = rep.getCaseCode();
    practiceOffer.setVisibility(View.VISIBLE);
    PracticeOfferPulse.play(practiceOffer);
  }

  private boolean isTurnedOffList(DrillRep rep) {
    String code = rep.getCaseCode();
    String turned = rep.getTurned();
    if (rep.isAbandoned() || !CaseAlgorithms.isWorthPointingOut(code, turned)
        || CaseFlags.isTheirs(code, turned)) {
      return false;
    }
    // A learning drill's own algorithm is what it asked for, even when off the usual list.
    return !session.getSpec().isLearning()
        || !CaseAlgorithms.sameTurning(code, turned, learningAlgorithm(code));
  }

  /** A learning rep looked up, reset, skipped or turned with another algorithm. */
  private boolean didNotStick(DrillRep rep) {
    return session.getSpec().isLearning() && (!CaseTally.isClean(rep) || rep.isOffTaught());
  }

  /**
   * The offered case, over and over until they go back: first with its algorithms to try on the
   * cube, then against the one kept.
   */
  private void enterPractice() {
    if (session == null || offeredCase == null) {
      return;
    }
    String first = firstToTry(offeredCase);
    if (first == null) {
      return;
    }
    hidePracticeOffer();
    lastRepRow.removeCallbacks(showNextCase); // tapped during the beat
    choosingToPractise = true;
    beginChoosing(offeredCase, first);
  }

  /**
   * The cases of a learning drill whose algorithm was never kept on the cube, even with one to
   * choose from: the choose step is where the algorithm is shown and repeated. A star from the case
   * popup alone is only where it starts, since what is known is read from the solves.
   */
  private void collectCasesToChoose() {
    toChoose.clear();
    chooseIndex = 0;
    for (String code : session.getCases()) {
      if (!Options.INSTANCE.isAlgorithmKept(code)) {
        toChoose.add(code);
      }
    }
  }

  /** The next case to choose for, practised meanwhile; the drill proper once none is left. */
  private void chooseNext() {
    lastRepRow.removeCallbacks(showNextCase);
    if (chooseIndex >= toChoose.size()) {
      endChoosing();
      return;
    }
    String code = toChoose.get(chooseIndex);
    beginChoosing(code, firstToTry(code));
  }

  /**
   * The algorithm a case's choose step starts on: the starred one, else the one a learning drill
   * names (for a pair, the shortest listed, which its coach card names it by), else the one the
   * case popup recommends, else the first listed. Null when there is none.
   */
  private String firstToTry(String code) {
    String starred = Options.INSTANCE.getCaseAlgorithm(code);
    if (starred != null) {
      return starred;
    }
    boolean learning = session.getSpec().isLearning();
    if (learning && (session.getSpec().getCaseAlgorithm(code) != null
        || CaseAlgorithms.isPair(code))) {
      return learningAlgorithm(code);
    }
    String recommended = recommendedAlgorithm(code);
    if (recommended != null) {
      return recommended;
    }
    List<CaseAlgorithms.Listed> listed = CaseAlgorithms.shown(code);
    if (!listed.isEmpty()) {
      return listed.get(0).getMoves();
    }
    return learning ? learningAlgorithm(code) : null;
  }

  /** The case dealt at the first algorithm's angle, with its algorithms in the card's place. */
  private void beginChoosing(String code, String first) {
    choosing = code;
    firstAlgorithm = first;
    tryingAlgorithm = first;
    setTitle(getString(R.string.drill_choose_title, CaseAlgorithms.isPair(code)
        ? getString(R.string.case_title_f2l)
        : getString(code.startsWith("oll_") ? R.string.case_title_oll : R.string.case_title_pll,
            LastLayerCaseNames.shortName(code))));
    session.startPractice(code, first);
    setChoosing(true);
    buildChooseRows();
    drawCase();
  }

  private void clearChoosing() {
    choosing = null;
    firstAlgorithm = null;
    tryingAlgorithm = null;
    choosingToPractise = false;
    setTitle(label);
  }

  /**
   * An algorithm as practised: the case is dealt at its own angle, so a turn of the cube it starts
   * with is dropped rather than asked for. What gets starred is the algorithm as listed.
   */
  private static String fromTheFront(String algorithm) {
    return algorithm == null ? null : LastLayerScrambles.withoutLeadingY(algorithm);
  }

  /**
   * Stars the algorithm being tried, which a learning drill then teaches. Practice goes back to the
   * drill; a learning drill's start moves on to its next case.
   */
  private void useTriedAlgorithm() {
    if (choosing == null || tryingAlgorithm == null) {
      return;
    }
    Options.INSTANCE.setCaseAlgorithm(choosing, tryingAlgorithm);
    Options.INSTANCE.addAlgorithmKept(choosing);
    if (choosingToPractise) {
      if (session.getSpec().isLearning()) {
        teachAll();
      }
      endChoosing();
      return;
    }
    session.setTaughtAlgorithm(choosing, tryingAlgorithm);
    chooseIndex++;
    chooseNext();
  }

  /** The listed algorithm the case popup marks as recommended, or null where the vote is close. */
  private static String recommendedAlgorithm(String code) {
    for (CaseAlgorithms.Listed listed : CaseAlgorithms.shown(code)) {
      if (listed.isRecommended()) {
        return listed.getMoves();
      }
    }
    return null;
  }

  /** Keeps a choose-step rep's execution time against the algorithm tried, if it counts. */
  private AlgorithmTimes.Verdict noteChooseTime(DrillRep rep) {
    if (choosing == null || !rep.isPractice() || tryingAlgorithm == null) {
      return null;
    }
    AlgorithmTimes.Verdict verdict = AlgorithmTimes.judge(rep, tryingAlgorithm,
        algorithmFollower != null ? algorithmFollower.hasStrayed() : null);
    if (verdict == AlgorithmTimes.Verdict.COUNTED) {
      AlgorithmTimes times = loadTimes(choosing);
      times.add(tryingAlgorithm, rep.getExecutionMs());
      Options.INSTANCE.setCaseAlgorithmTimes(choosing, times.toString());
      buildChooseRows();
    }
    return verdict;
  }

  private static AlgorithmTimes loadTimes(String code) {
    return AlgorithmTimes.parse(code, Options.INSTANCE.getCaseAlgorithmTimes(code));
  }

  private void endChoosing() {
    lastRepRow.removeCallbacks(showNextCase); // left during a rep's beat
    clearChoosing();
    session.endPractice();
    setChoosing(false);
    nextRep();
  }

  /** Practice, with the case's algorithms and a button to keep one in place of the drill's card. */
  private void setChoosing(boolean on) {
    setPracticing(on);
    findViewById(R.id.llDrillCard).setVisibility(on ? View.GONE : View.VISIBLE);
    findViewById(R.id.llDrillButtons).setVisibility(on ? View.GONE : View.VISIBLE);
    chooseList.setVisibility(on ? View.VISIBLE : View.GONE);
    findViewById(R.id.llDrillChooseButtons).setVisibility(on ? View.VISIBLE : View.GONE);
    if (on) {
      // Sized once shown: hidden, the button has no width and its label got the smallest size
      DrillControlLabels.apply(new Button[] {findViewById(R.id.btDrillChooseReset)},
          new int[] {R.drawable.ic_drill_reset}, new int[] {R.string.drill_reset});
    }
  }

  /**
   * The algorithm it starts on, then the case's listed ones, then the user's own, and a way into
   * the full popup for anything else, where a star or a typed algorithm comes back as the one tried.
   */
  private void buildChooseRows() {
    chooseList.removeAllViews();
    List<String> shown = new ArrayList<String>();
    addIfNew(shown, firstAlgorithm);
    for (CaseAlgorithms.Listed listed : CaseAlgorithms.shown(choosing)) {
      addIfNew(shown, listed.getMoves());
    }
    addIfNew(shown, Options.INSTANCE.getOwnCaseAlgorithm(choosing));
    addIfNew(shown, Options.INSTANCE.getCaseAlgorithm(choosing));
    addIfNew(shown, tryingAlgorithm);
    AlgorithmTimes times = loadTimes(choosing);
    String fastest = times.fastest(shown);
    for (String algorithm : shown) {
      addChooseRow(algorithm, times, fastest);
    }

    View moreRow = LayoutInflater.from(this).inflate(R.layout.drill_choose_row, chooseList, false);
    moreRow.findViewById(R.id.rbChooseRow).setVisibility(View.INVISIBLE);
    moreRow.findViewById(R.id.tvChooseRowTag).setVisibility(View.GONE);
    TextView more = moreRow.findViewById(R.id.tvChooseRowAlg);
    more.setText(R.string.drill_choose_more);
    more.setTextColor(ContextCompat.getColor(this, R.color.secondary_text));
    moreRow.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        DialogUtils.showFragment(DrillActivity.this, CaseAlgorithmsDialog.newInstance(choosing));
      }
    });
    chooseList.addView(moreRow);
  }

  /** Adds an algorithm to try, unless it is one already listed or none at all. */
  private void addIfNew(List<String> shown, String algorithm) {
    if (algorithm == null) {
      return;
    }
    for (String other : shown) {
      if (other.equals(algorithm) || CaseAlgorithms.sameTurning(choosing, algorithm, other)) {
        return;
      }
    }
    shown.add(algorithm);
  }

  /** The marks the case popup gives the same algorithm, joined. */
  private String chooseTag(String algorithm) {
    List<String> marks = new ArrayList<String>();
    if (CaseAlgorithms.sameTurning(choosing, algorithm,
        Options.INSTANCE.getOwnCaseAlgorithm(choosing))) {
      marks.add(getString(R.string.drill_choose_yours));
    }
    if (CaseAlgorithms.sameTurning(choosing, algorithm, recommendedAlgorithm(choosing))) {
      marks.add(getString(R.string.case_algorithm_recommended));
    }
    return marks.isEmpty() ? null : TextUtils.join(" · ", marks);
  }

  /** One algorithm to try, with its execution average once a rep of it has counted. */
  private void addChooseRow(final String algorithm, AlgorithmTimes times, String fastest) {
    String tag = chooseTag(algorithm);
    View row = LayoutInflater.from(this).inflate(R.layout.drill_choose_row, chooseList, false);
    ((RadioButton) row.findViewById(R.id.rbChooseRow)).setChecked(algorithm.equals(tryingAlgorithm));
    TextView moves = row.findViewById(R.id.tvChooseRowAlg);
    moves.setText(fromTheFront(algorithm));
    moves.setTypeface(algorithmFace);
    TextView tagView = row.findViewById(R.id.tvChooseRowTag);
    tagView.setText(tag);
    tagView.setVisibility(tag == null ? View.GONE : View.VISIBLE);
    int reps = times.get(algorithm).size();
    if (reps > 0) {
      TextView average = row.findViewById(R.id.tvChooseRowAverage);
      average.setText(FormatterService.INSTANCE.formatSolveTime(times.mean(algorithm)));
      // Grey until a full average, green for the fastest of those
      average.setTextColor(ContextCompat.getColor(this, reps < AlgorithmTimes.KEPT
          ? R.color.secondary_text : algorithm.equals(fastest) ? R.color.green_soft
          : R.color.white));
      average.setVisibility(View.VISIBLE);
      TextView count = row.findViewById(R.id.tvChooseRowCount);
      count.setText(getResources().getQuantityString(R.plurals.drill_choose_last, reps, reps));
      count.setVisibility(View.VISIBLE);
    }
    row.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (algorithm.equals(tryingAlgorithm)) {
          return;
        }
        tryingAlgorithm = algorithm;
        session.setPracticeAlgorithm(algorithm);
        buildChooseRows();
        restartPractice(); // dealt afresh, so the new one is tried from the start
      }
    });
    chooseList.addView(row);
  }

  /** The practice case dealt afresh, since turns made behind the popup were not an attempt at it. */
  private void restartPractice() {
    if (finished || isFinishing() || session == null || session.getPracticeCase() == null) {
      return; // also reached when the screen is torn down with the popup open
    }
    lastRepRow.removeCallbacks(showNextCase);
    session.dropCarriedTurns(); // dealt afresh, not next: turns before it were not an attempt
    session.nextRep();
    drawCase();
  }

  /** A coach drill keeps the offer's room, so the cube does not jump when it shows. */
  private void hidePracticeOffer() {
    boolean coach = session != null && session.getSpec().isFromCoach();
    practiceOffer.setVisibility(coach ? View.INVISIBLE : View.GONE);
  }

  /** Practice counts no case, so the drill's progress steps aside until it is over. */
  private void setPracticing(boolean on) {
    boolean retiring = session.getSpec().retiresCases();
    casePips.setVisibility(!on && retiring ? View.VISIBLE : View.GONE);
    findViewById(R.id.tvDrillProgress).setVisibility(View.VISIBLE); // the card's heading
    findViewById(R.id.pbDrillProgress).setVisibility(!on && !retiring ? View.VISIBLE : View.GONE);
    // A learning drill's fade may still be running, and would hide the practised algorithm.
    LearningAlgorithmFade.show(practiceAlgorithmRow);
    practiceAlgorithmRow.setVisibility(on ? View.VISIBLE : View.GONE);
    showAlg.setVisibility(View.GONE);
    practiceBack.setEnabled(on);
    showPracticeAlgorithm();
  }

  /** The algorithm being tried for the case being practised, as it is dealt. */
  private void showPracticeAlgorithm() {
    String code = session == null ? null : session.getPracticeCase();
    if (code == null || !code.equals(choosing)) {
      return;
    }
    followAlgorithm(code, fromTheFront(tryingAlgorithm));
  }

  /**
   * Writes an algorithm under the cube and greys it as turned. A last layer case is drawn beside it
   * at the angle the algorithm starts from, which nothing else says.
   */
  private void followAlgorithm(String code, String algorithm) {
    LastLayerDiagram diagram = algorithm != null && code != null
        && (code.startsWith("oll_") || code.startsWith("pll_"))
        ? LastLayerDiagram.forAlgorithm(code, algorithm) : null;
    practiceCasePicture.setDiagram(diagram);
    practiceCasePicture.setVisibility(diagram != null ? View.VISIBLE : View.GONE);
    algorithmAnimator.reset();
    algorithmFollower = null;
    if (algorithm != null) {
      try {
        algorithmFollower = new AlgorithmFollower(algorithm, layerFace,
            diagram != null && session != null ? session::wouldFinish : null);
      } catch (IllegalArgumentException e) {
        algorithmFollower = null; // shown as written, just not greyed
      }
    }
    practiceAlgorithm.setText(algorithm);
    showAlgorithmProgress();
  }

  /**
   * A learning drill's case comes up with its algorithm hidden, since it was turned in the choose
   * step, and Show alg in its place. One still up from the rep before fades out first.
   */
  private void hideLearningAlgorithm() {
    if (!session.getSpec().isLearning() || session.getPracticeCase() != null) {
      return;
    }
    algorithmFollower = null;
    boolean fading = practiceAlgorithmRow.getVisibility() == View.VISIBLE;
    LearningAlgorithmFade.hide(practiceAlgorithmRow);
    LearningAlgorithmFade.appearAfter(showAlg, fading);
  }

  /**
   * The faded algorithm back for the rep up, followed from the turns already made. The rep is
   * marked as looked up, which starts the case's run over, as the Alg list does.
   */
  private void showAlgorithmAgain() {
    String code = session == null ? null : session.getCurrentCase();
    if (code == null || session.getPracticeCase() != null) {
      return;
    }
    session.markRevealed();
    shownThisRep = true;
    showAlg.setVisibility(View.GONE);
    followAlgorithm(code, learningAlgorithm(code));
    if (algorithmFollower != null) {
      for (CubeMove move : session.getMovesSoFar()) {
        algorithmFollower.onMove(move);
      }
      showAlgorithmProgress();
    }
    LearningAlgorithmFade.show(practiceAlgorithmRow);
  }

  /**
   * The user's chosen algorithm, else the one the drill names if it solves the case, else the usual:
   * for a pair the shortest listed, which its extra moves are counted against.
   */
  private String learningAlgorithm(String code) {
    String chosen = Options.INSTANCE.getCaseAlgorithm(code);
    if (chosen != null) {
      return chosen;
    }
    String named = session.getSpec().getCaseAlgorithm(code);
    if (named != null && CaseAlgorithms.solves(code, named)) {
      return named;
    }
    return CaseAlgorithms.isPair(code) ? CaseAlgorithms.shortestListed(code)
        : LastLayerAlgorithms.forCaseCode(code);
  }

  /** Whether an algorithm is on screen for the case up: practice, or one shown again. */
  private boolean isAlgorithmUp() {
    return session.getPracticeCase() != null || shownThisRep;
  }

  /**
   * The practised algorithm with its turned moves greyed, as the timer greys a scramble's, or in its
   * place, as the timer does, the turns that undo a slip, said to be a wrong angle where it is one.
   */
  private void showAlgorithmProgress() {
    if (algorithmFollower == null) {
      return;
    }
    String undo = algorithmFollower.getUndoMoves();
    if (!undo.isEmpty()) {
      algorithmAnimator.reset();
      practiceAlgorithm.setText(ScrambleFormatterService.INSTANCE.formatReverseMoves(
          getString(algorithmFollower.isWrongAngle() ? R.string.drill_wrong_angle
              : R.string.smart_cube_undo), undo));
      return;
    }
    algorithmAnimator.show(new SpannableString(TextUtils.join(" ",
        algorithmFollower.getTokens())), algorithmFollower.getDoneCount());
  }

  /** A case dealt again starts the algorithm over. */
  private void restartAlgorithmProgress() {
    if (session.getPracticeCase() != null) {
      showPracticeAlgorithm(); // a new deal is at a new angle, so the follower is built again
      return;
    }
    if (algorithmFollower != null && isAlgorithmUp()) {
      algorithmFollower.reset();
      algorithmAnimator.reset();
      showAlgorithmProgress();
    }
  }

  private void nextRep() {
    if (!session.nextRep()) {
      showSummary();
      return;
    }
    drawCase();
  }

  /** Puts the case just dealt on the screen's cube. */
  private void drawCase() {
    if (session.getShownEdges() != null) {
      cube.setStickering(
          CubeStickering.pieces(session.getShownEdges(), session.getShownCorners()));
    }
    cube.setState(CubePatternFormat.format(session.getFacelets()));
    shownThisRep = false;
    restartAlgorithmProgress();
    hideLearningAlgorithm();
    // ⚠️ The case now on screen is deliberately not named anywhere. Only the cube says what it
    // is. The line naming the rep that is over stays, since one beat is not long enough to read
    // it, but it stands back so that it cannot be taken for the name of this one.
    recedeLastRep();
    if (session.getPracticeCase() == null) {
      if (session.getSpec().retiresCases()) {
        refreshCaseProgress();
      } else {
        setProgress(session.getReps().size() + 1, session.getSpec().getReps());
      }
    }
    // Only once the case is really up: before the page has drawn, the first one is not yet in
    // front of anybody, and onCubeDrawn is what says it is.
    if (cubeReady) {
      caseShown();
    }
  }

  /**
   * The case is in front of the user, which is where the rep's looking runs from and where the
   * turns that count start.
   *
   * <p>The cube's next reported state seeds what a turn it drops is measured against. Not the state
   * read now: that runs ahead of turns still queued, which would then count twice and "repair" the
   * new case by undoing them, two turns off whenever the user was already turning.
   *
   * <p>A practice rep started during the beat already has turns, which the session has put on
   * the case; the screen's cube and the algorithm's greying get them here.
   */
  private void caseShown() {
    DrillSession.Shown shown = session.showCase(System.currentTimeMillis());
    if (shown.getTurns().isEmpty()) {
      return;
    }
    cube.setState(CubePatternFormat.format(session.getFacelets()));
    if (algorithmFollower != null && isAlgorithmUp()) {
      for (DrillSession.CarriedTurn turn : shown.getTurns()) {
        if (turn.getMove() != null) {
          algorithmFollower.onMove(turn.getMove());
        } else {
          algorithmFollower.onMissed(turn.getMissed());
        }
      }
      showAlgorithmProgress();
    }
    if (shown.getRep() != null) {
      onRepFinished(shown.getRep());
    }
  }

  /**
   * The rep that has just ended, named and timed together. Naming it is only safe here: the case is
   * over, so it can say what was done, or for a skipped one what it was the user could not place.
   */
  private void showLastRep(DrillRep rep, AlgorithmTimes.Verdict timed) {
    String name = Utils.toSmartCubeCaseHeadline(this, rep.getCaseCode());
    int colour = familyColour(rep.getCaseCode());
    boolean retiring = session.getSpec().retiresCases();
    repVerdict.setVisibility(View.GONE);
    lastRep = rep.isPractice() ? null : rep;
    DrillLiveCells.show(this, session.getReps(), session.getSpec().getType());
    if (retiring) {
      refreshCaseProgress(); // rings the case this line is about
    }
    if (rep.isAbandoned()) {
      setLastRep(name, colour, getString(R.string.drill_rep_skipped), true, null);
      repSplit.setVisibility(View.INVISIBLE); // keeps its room, so the cube does not move
      return;
    }
    if (timed != null) {
      showChooseRep(rep, name, colour, timed);
      return;
    }
    // A look-ahead rep is read by its pause, so the line leads with that rather than its name.
    DrillRepOrder.Key key = DrillRepOrder.readBy(session.getSpec().getType());
    boolean pause = key == DrillRepOrder.Key.PAUSE;
    List<DrillRepParts.Part> parts =
        DrillRepParts.of(this, rep, ContextCompat.getColor(this, colour));
    setLastRep(pause ? getString(R.string.drill_lookahead_pause) : name,
        pause ? R.color.drill_pause : colour,
        FormatterService.INSTANCE.formatSolveTime(DrillRepOrder.timeMs(rep, key)), false,
        DrillRepParts.words(this, parts));
    DrillRepParts.draw(repSplit, parts);
    repSplit.setVisibility(View.VISIBLE);
    if (retiring && !rep.isPractice()) {
      showVerdict(rep);
    }
  }

  /** A choose-step rep: its execution alone, which is what the algorithms are compared on. */
  private void showChooseRep(DrillRep rep, String name, int colour, AlgorithmTimes.Verdict timed) {
    setLastRep(name, colour, FormatterService.INSTANCE.formatSolveTime(rep.getExecutionMs()), false,
        getString(R.string.drill_summary_column_execution));
    repSplit.setVisibility(View.INVISIBLE);
    if (timed == AlgorithmTimes.Verdict.WRONG_MOVES) {
      setVerdict(getString(R.string.drill_choose_not_counted_moves), R.color.secondary_text);
    } else if (timed == AlgorithmTimes.Verdict.RESET) {
      setVerdict(getString(R.string.drill_choose_not_counted_reset), R.color.secondary_text);
    }
  }

  private static int familyColour(String code) {
    if (CaseAlgorithms.isPair(code) || DrillSession.isLookAheadCase(code)) {
      return R.color.step_f2l;
    }
    return code != null && code.startsWith(FAMILY_OLL) ? R.color.step_oll : R.color.step_pll;
  }

  /** A coach drill counts cases, not reps: a bar per case takes the rep count's meter's place. */
  private void useCaseProgress() {
    findViewById(R.id.pbDrillProgress).setVisibility(View.GONE);
    casePips.setVisibility(View.VISIBLE);
    refreshCaseProgress();
  }

  private void refreshCaseProgress() {
    List<String> cases = shownCases(true);
    CaseTally tally = session.getTally();
    boolean won = lastRep != null && CaseTally.isClean(lastRep) && tally.isWin(lastRep);
    casePips.show(cases, tally, session.getSpec().getWinsToRetire(),
        lastRep == null ? null : lastRep.getCaseCode(), won);
    if (session.getPracticeCase() == null) {
      int cleared = 0;
      for (String code : cases) {
        cleared += tally.isCleared(code) ? 1 : 0;
      }
      ((TextView) findViewById(R.id.tvDrillProgress))
          .setText(getString(R.string.drill_cases_cleared, cleared, cases.size()));
    }
  }

  /**
   * The cases to count progress over. A drill with a working set may hold far more cases than it
   * reaches, so only those dealt so far are shown, plus while it runs those in the set.
   */
  private List<String> shownCases(boolean running) {
    if (session.getSpec().getWorkingSet() == 0) {
      return session.getCases();
    }
    List<String> active = running ? session.getActiveCases() : Collections.<String>emptyList();
    List<String> shown = new ArrayList<String>();
    for (String code : session.getCases()) {
      if (active.contains(code) || wasDealt(code)) {
        shown.add(code);
      }
    }
    return shown;
  }

  private boolean wasDealt(String code) {
    for (DrillRep rep : session.getReps()) {
      if (code.equals(rep.getCaseCode())) {
        return true;
      }
    }
    return false;
  }

  /** How the rep did against its case's target, said only once the rep is over. */
  private void showVerdict(DrillRep rep) {
    long target = session.getSpec().getTargetMs(rep.getCaseCode());
    String text;
    int colourRes;
    if (rep.isOffTaught()) {
      text = getString(R.string.drill_rep_other_alg);
      colourRes = R.color.danger_text;
    } else if (session.getSpec().isLearning() && CaseTally.isClean(rep)) {
      text = getString(R.string.drill_rep_unaided);
      colourRes = R.color.green_soft;
    } else if (!CaseTally.isClean(rep)) {
      text = getString(R.string.drill_rep_not_counted);
      colourRes = R.color.secondary_text;
    } else if (session.getTally().isWin(rep)) {
      text = getString(R.string.drill_rep_under,
          FormatterService.INSTANCE.formatSolveTime(target - rep.getTotalMs()));
      colourRes = R.color.green_soft;
    } else {
      text = getString(R.string.drill_rep_over,
          FormatterService.INSTANCE.formatSolveTime(rep.getTotalMs() - target));
      colourRes = R.color.danger_text;
    }
    setVerdict(text, colourRes);
  }

  private void setVerdict(String text, int colourRes) {
    int colour = ContextCompat.getColor(this, colourRes);
    repVerdict.setText(text); // dark ink on a chip filled with the verdict's colour
    repVerdict.setBackgroundTintList(ColorStateList.valueOf(colour));
    repVerdict.setVisibility(View.VISIBLE);
  }

  @Override
  protected void showSummary() {
    if (finished) {
      return; // a cube unplugged on the summary screen must not re-run this over its own figures
    }
    finished = true;
    practiceBack.setEnabled(false);
    practiceOffer.setVisibility(View.GONE);
    showSummaryFor(familyHue);

    // Before the figures give up: a drill where every case was skipped still has something to
    // report, which is which cases they were. It is also what prunes a rep, hence counted().
    DrillCaseTable table =
        new DrillCaseTable(this, session.getReps(), session.getSpec().getType(), this);
    showFigures(table.counted());
  }

  /**
   * A rep thrown out on the summary, or put back. It leaves the stored drill as well as the screen:
   * a rep the user rejected must not go on being averaged into the case history behind their back.
   */
  @Override
  public void onRepsPruned(int position, boolean deleted, List<DrillRep> counted) {
    recorder.setCaseRepDeleted(position, deleted);
    showFigures(counted);
  }

  /** The card over the table, from the reps that count. Redrawn whenever that stops being all. */
  private void showFigures(List<DrillRep> reps) {
    DrillSpec spec = session.getSpec();
    boolean pairs = spec.getType() == DrillSpec.Type.F2L_PAIR;
    if (spec.retiresCases()) {
      showTargets(reps);
    } else {
      setSummaryCell(0, getString(pairs ? R.string.drill_summary_pairs
              : R.string.drill_summary_cell_reps), String.valueOf(reps.size()),
          getString(R.string.drill_summary_cell_of, spec.getReps()));
    }

    // Skipped reps are counted apart rather than folded into a mean, the same rule the case stats
    // follow: a case you gave up on is not a slow time.
    DrillRepOrder.Key key = DrillRepOrder.readBy(spec.getType());
    boolean pause = key == DrillRepOrder.Key.PAUSE;
    List<List<DrillRepParts.Part>> timedParts = new ArrayList<List<DrillRepParts.Part>>();
    long keyTotal = 0;
    DrillRep best = null;
    for (DrillRep rep : reps) {
      if (rep.isAbandoned()) {
        continue;
      }
      timedParts.add(DrillRepParts.of(this, rep, familyHue));
      keyTotal += DrillRepOrder.timeMs(rep, key);
      if (best == null || DrillRepOrder.timeMs(rep, key) < DrillRepOrder.timeMs(best, key)) {
        best = rep;
      }
    }
    if (timedParts.isEmpty()) {
      showSummaryEmpty(R.string.drill_summary_nothing_timed);
      return;
    }
    showSummaryFigures(); // a drill pruned to nothing and then put back has these to get back
    long mean = keyTotal / timedParts.size();
    if (spec.retiresCases()) {
      // The hero is the cases cleared, so the mean goes under the bar between its two halves.
      setSummarySplit(DrillRepParts.mean(timedParts), mean);
      return;
    }
    setSummaryHero(FormatterService.INSTANCE.formatSolveTime(mean), null,
        getString(pause ? R.string.drill_summary_mean_pause : R.string.drill_summary_cell_average));
    setSummarySplit(DrillRepParts.mean(timedParts), -1);
    // The best rep says which it was: a time with no case attached could not be acted on. A pair
    // has no picture and no name of its own, so it is named by where it fell in the drill.
    setSummaryCell(1, getString(pause ? R.string.drill_summary_cell_best_pause
            : R.string.drill_summary_cell_best),
        FormatterService.INSTANCE.formatSolveTime(DrillRepOrder.timeMs(best, key)), null);
    setSummaryCellColor(1, ContextCompat.getColor(this, R.color.session_best));
    if (pairs || pause) {
      setSummaryCellCase(null, getString(R.string.drill_summary_rep_number,
          session.getReps().indexOf(best) + 1));
    } else {
      setSummaryCellCase(best.getCaseCode(),
          Utils.toSmartCubeCaseHeadline(this, best.getCaseCode()));
    }
  }

  /**
   * A coach drill's card and its case list: how many cases cleared, what stopped it, and each case
   * against its target. Read off the reps that count, so throwing a rep out below can take a
   * clearing with it.
   */
  private void showTargets(List<DrillRep> reps) {
    DrillSpec spec = session.getSpec();
    List<String> cases = shownCases(false);
    CaseTally tally = CaseTally.of(spec, reps);
    List<String> over = new ArrayList<String>();
    LinearLayout rows = findViewById(R.id.llDrillTargetRows);
    rows.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(this);
    for (String code : cases) {
      String name = Utils.toSmartCubeCaseHeadline(this, code);
      if (!tally.isCleared(code)) {
        over.add(name);
      }
      rows.addView(targetRow(inflater, rows, code, name, reps, tally));
    }
    findViewById(R.id.llDrillTargets).setVisibility(View.VISIBLE);

    hideSummaryCells();
    setSummaryEyebrow(finishedAfter(reps.size()));
    setSummaryHero(String.valueOf(cases.size() - over.size()),
        getString(R.string.drill_summary_cell_of, cases.size()),
        getString(R.string.drill_summary_cases_cleared));

    StringBuilder note = new StringBuilder();
    if (session.getReps().size() >= spec.getReps()) {
      note.append(getString(R.string.drill_summary_ceiling, spec.getReps()));
    }
    // Pairs all carry the same name, so naming those still over says nothing the rows do not.
    if (!over.isEmpty() && spec.getType() != DrillSpec.Type.F2L_PAIR) {
      note.append(note.length() == 0 ? "" : " ").append(getString(spec.isLearning()
          ? R.string.drill_summary_still_learning : R.string.drill_summary_over_target,
          joined(over)));
    }
    setSummaryNote(note);
  }

  /** One case: its chart, how often it was dealt, its wins, its target, and its mean against it. */
  private View targetRow(LayoutInflater inflater, LinearLayout parent, final String code,
      String name, List<DrillRep> reps, CaseTally tally) {
    int dealt = 0;
    int timed = 0;
    long total = 0;
    for (DrillRep rep : reps) {
      if (code.equals(rep.getCaseCode())) {
        dealt++;
        if (!rep.isAbandoned()) {
          timed++;
          total += rep.getTotalMs();
        }
      }
    }
    long target = session.getSpec().getTargetMs(code);
    View row = inflater.inflate(R.layout.drill_target_row, parent, false);
    row.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        DialogUtils.showFragment(DrillActivity.this, CaseAlgorithmsDialog.newInstance(code));
      }
    });
    LastLayerCaseView chart = row.findViewById(R.id.vDrillTargetChart);
    if (CaseAlgorithms.isPair(code)) {
      chart.setVisibility(View.GONE);
    } else {
      chart.setDiagram(LastLayerDiagram.forCase(code));
    }
    if (!tally.isCleared(code)) {
      tintUncleared(row);
    }
    ((TextView) row.findViewById(R.id.tvDrillTargetName)).setText(name);
    ((TextView) row.findViewById(R.id.tvDrillTargetReps)).setText(String.valueOf(dealt));
    DrillCasePips wins = row.findViewById(R.id.vDrillTargetWins);
    wins.setBars(true);
    wins.show(Collections.singletonList(code), tally, session.getSpec().getWinsToRetire());
    // A learning drill has no time to beat, only reps without looking.
    boolean learning = session.getSpec().isLearning();
    ((TextView) row.findViewById(R.id.tvDrillTargetTarget)).setText(learning
        ? getString(R.string.drill_case_no_time) : FormatterService.INSTANCE.formatSolveTime(target));
    TextView tvMean = row.findViewById(R.id.tvDrillTargetMean);
    if (learning && timed > 0) {
      tvMean.setText(FormatterService.INSTANCE.formatSolveTime(total / timed));
      tvMean.setTextColor(ContextCompat.getColor(this, R.color.white));
    } else if (timed == 0) {
      tvMean.setText(R.string.drill_case_no_time);
      tvMean.setTextColor(ContextCompat.getColor(this, R.color.secondary_text));
    } else {
      long mean = total / timed;
      tvMean.setText(FormatterService.INSTANCE.formatSolveTime(mean));
      tvMean.setTextColor(ContextCompat.getColor(this,
          mean <= target ? R.color.green_soft : R.color.danger_text));
    }
    return row;
  }

  /** A case still to clear stands out in a faint wash of the drill's colour. */
  private void tintUncleared(View row) {
    float density = getResources().getDisplayMetrics().density;
    GradientDrawable wash = new GradientDrawable();
    wash.setCornerRadius(10 * density);
    wash.setColor(StepCardBackground.withAlpha(familyHue, UNCLEARED_ALPHA));
    LayerDrawable background = new LayerDrawable(new Drawable[] {row.getBackground(), wash});
    background.setLayerInset(1, 0, Math.round(3 * density), 0, Math.round(2 * density));
    row.setBackground(background);
  }

  private static String joined(List<String> names) {
    StringBuilder sb = new StringBuilder();
    for (String name : names) {
      sb.append(sb.length() == 0 ? "" : ", ").append(name);
    }
    return sb.toString();
  }
}
