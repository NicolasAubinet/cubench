package com.cube.nanotimer.gui.widget.cases;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cases.CaseGroup;
import com.cube.nanotimer.cases.CaseSet;
import com.cube.nanotimer.cases.CaseSets;
import com.cube.nanotimer.cube.CaseExecutions;
import com.cube.nanotimer.cube.CaseFlags;
import com.cube.nanotimer.cube.CubePatternFormat;
import com.cube.nanotimer.cube.CubeStickering;
import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.cube.VirtualCube;
import com.cube.nanotimer.gui.DrillActivity;
import com.cube.nanotimer.gui.DrillCaseStatsActivity;
import com.cube.nanotimer.gui.DrillScreenActivity;
import com.cube.nanotimer.gui.DrillStatsWindow;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.PairCaseView;
import com.cube.nanotimer.gui.widget.NanoTimerBottomSheetFragment;
import com.cube.nanotimer.gui.widget.SmartCubeConnectDialog;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.step.AlgorithmExecution;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms.Listed;
import com.cube.nanotimer.smartcube.step.F2LCaseAlgorithms;
import com.cube.nanotimer.smartcube.step.F2LSlotAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.usage.UsageShares;
import com.cube.nanotimer.util.view.DrillSplitBarView;
import com.cube.nanotimer.util.view.SmartCubeMarkLabel;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.vo.CaseHistory;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.StepStats;
import com.cube.nanotimer.vo.drill.DrillCaseStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One case, as every screen opens it: its picture and name, the solver's own times in solves and in
 * drills, and the algorithms it is solved with, the one this solver uses marked. A last layer case,
 * or the case an F2L pair was handed.
 *
 * <p><b>An F2L pair's case is never named.</b> Its numbering is one website's rather than an
 * official one, so the sheet is titled for the pair and the case is shown on the 3D cube instead,
 * held cross down with only the cross and the pair in colour.
 *
 * <p><b>Times from solves and from drills are two cards, never one mean.</b> A drill hands the case
 * over with nothing to recognise and no look-ahead into it, so the two do not measure the same
 * thing. Both cards always show, one with nothing in it as dashes, so the sheet never changes
 * shape. A drill card with reps opens the case's drill attempts, where a bad rep can be thrown out.
 *
 * <p><b>The list is in most-used order and nothing moves.</b> Tapping a row stars it; the order
 * stays the recommended one whatever is picked, and an algorithm of the solver's own sits under the
 * list rather than in it. Only the algorithms holding most of the votes are listed; the rest open in
 * place behind "Show N more", since the long tail is part of the answer too.
 *
 * <p><b>The star and "the one you use" are two marks.</b> The star is a thing the solver said; "the
 * one you use" is read out of their own recent solves, a thing they did. An execution is never
 * written to preferences. Every execution turned more than once gets its row, with how many of their
 * answers it was where there is more than one. No execution time on a row: the In solves card has
 * it, and the drill's choose step compares algorithms. A lone execution waits for its second before
 * it is called theirs: once is how a misread case put right looks.
 *
 * <p>Opened from one solve, it shows that solve's execution instead, marked "this solve".
 *
 * <p><b>With no smart cube behind it</b> (no solve or drill ever read the case) the sheet is the
 * reference: the star reads as "Your pick", and the setup moves for the picked algorithm put the
 * case on a real cube. <b>Practice this case</b> opens the case's drill on a connected cube, or the
 * connect sheet, from any screen but a drill.
 */
public class CaseSheet extends NanoTimerBottomSheetFragment {

  /** Sent to the fragment manager whenever what is pointed out for a case may have changed. */
  public static final String FLAGS_CHANGED = "case_flags_changed";

  private static final String ARG_CASE = "case";
  private static final String ARG_USED = "used";

  /** The cube's corner view, the drills' own: the top face and two sides, front a little ahead. */
  private static final double VIEW_LATITUDE = 26;
  private static final double VIEW_LONGITUDE = 30;
  /** The scramble dialog's distance, measured for a well of about this size. */
  private static final double CAMERA_DISTANCE = 5.2;

  /** The cross on U, the pair going into up-front-left; a z2 turns it cross down, slot front right. */
  private static final int[] CROSS_UP_EDGES = {0, 1, 2, 3};
  /** Where a z2 puts each slot's pair, in {@link F2LSlotAlgorithms#SLOTS} order. */
  private static final int[] CROSS_UP_CORNERS = {1, 0, 2, 3};
  private static final int[] CROSS_UP_SLOT_EDGES = {9, 8, 10, 11};

  /** How many of the case's solves are replayed for the moves: the cost of opening the sheet. */
  private static final int SOLVES_READ = 10;
  /** How many of the case's latest solves the In solves card is the mean of. */
  private static final int SOLVE_FIGURES = 50;
  /** An empty card's figures. */
  private static final String NONE = "–";

  private String caseCode;
  /** The moves turned for the case in the solve the sheet was opened from, or null. */
  private String used;
  private String chosen;
  private String own;
  /** A starred algorithm no other row is, kept while the sheet is open so an unstar can be undone. */
  private String starredRow;
  /** Everything they turn for the case, most turned first, empty until the solves have been read. */
  private List<Turned> turned = new ArrayList<Turned>();
  private boolean moreOpen;
  /** Whether a solve or a drill ever timed the case; null until both have been asked. */
  private Boolean measured;
  private int answersRead;

  private View root;
  private LinearLayout rows;
  private LinearLayout more;
  private TextView showMore;
  private LinearLayout yours;
  private View yoursLabel;
  private Button practice;
  private VirtualCube cube;

  public static CaseSheet newInstance(String caseCode) {
    return newInstance(caseCode, null);
  }

  /**
   * @param used the moves one solve turned for the case: shown and marked in place of what the
   *     solver's recent solves say they usually turn
   */
  public static CaseSheet newInstance(String caseCode, String used) {
    CaseSheet sheet = new CaseSheet();
    Bundle args = new Bundle();
    args.putString(ARG_CASE, caseCode);
    args.putString(ARG_USED, used);
    sheet.setArguments(args);
    return sheet;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    caseCode = getArguments().getString(ARG_CASE);
    used = getArguments().getString(ARG_USED);
    chosen = Options.INSTANCE.getCaseAlgorithm(caseCode);
    own = Options.INSTANCE.getOwnCaseAlgorithm(caseCode);
    root = inflater.inflate(R.layout.case_sheet, container, false);
    showCase();

    rows = root.findViewById(R.id.llCaseAlgorithms);
    more = root.findViewById(R.id.llCaseMore);
    showMore = root.findViewById(R.id.tvCaseShowMore);
    yours = root.findViewById(R.id.llCaseYours);
    yoursLabel = root.findViewById(R.id.tvCaseYoursLabel);
    ((TextView) root.findViewById(R.id.tvCaseAlgorithmsOrder)).setText(
        CaseAlgorithms.hasUsage(caseCode) ? R.string.case_algorithms_order_usage
            : R.string.case_algorithms_order);
    root.findViewById(R.id.tvCaseAddAlgorithm).setOnClickListener(v -> askForAlgorithm());
    showMore.setOnClickListener(v -> {
      moreOpen = !moreOpen;
      refresh();
    });
    root.findViewById(R.id.buCaseClose).setOnClickListener(v -> dismiss());
    practice = root.findViewById(R.id.buCasePractice);
    practice.setText(SmartCubeMarkLabel.of(getActivity(),
        getString(R.string.case_algorithms_practice), 20));
    practice.setOnClickListener(v -> practice());
    ((TextView) root.findViewById(R.id.tvCaseSetupMoves))
        .setTypeface(AlgorithmRow.face(getActivity()));

    if (used != null) {
      turned.add(new Turned(CaseAlgorithms.asAlgorithm(caseCode, used),
          CaseAlgorithms.read(caseCode, used), 1, 1));
    } else {
      readExecution();
    }
    emptyCard(root.findViewById(R.id.icCaseSolves), R.string.case_sheet_in_solves);
    emptyCard(root.findViewById(R.id.icCaseDrills), R.string.case_sheet_in_drills);
    refresh();
    loadFigures();
    loadStanding();
    return root;
  }

  /** The picture, the title and the group the case is learnt in. */
  private void showCase() {
    TextView title = root.findViewById(R.id.tvCaseTitle);
    CaseSet set = CaseSets.ofCase(caseCode);
    if (CaseAlgorithms.isPair(caseCode)) {
      root.findViewById(R.id.vCaseChart).setVisibility(View.GONE);
      String picture = CaseAlgorithms.pairPicture(caseCode);
      if (picture != null) {
        PairCaseView pair = root.findViewById(R.id.vCasePair);
        pair.setVisibility(View.VISIBLE);
        pair.setPicture(picture);
      } else {
        root.findViewById(R.id.flCaseCube).setVisibility(View.VISIBLE);
        showPairCase((WebView) root.findViewById(R.id.wvCaseCube));
      }
      title.setText(Utils.toSmartCubeCaseHeadline(getActivity(), caseCode));
      CaseGroup group = set == null ? null : set.groupOf(caseCode);
      if (group != null) {
        addChip(group.getName(getActivity()), false);
      }
      return;
    }
    ((LastLayerCaseView) root.findViewById(R.id.vCaseChart))
        .setDiagram(LastLayerDiagram.forCase(caseCode));
    title.setText(set == null ? Utils.toSmartCubeCaseHeadline(getActivity(), caseCode)
        : getString(set == CaseSets.OLL ? R.string.case_title_oll : R.string.case_title_pll,
            set.name(caseCode)));
    if (set != null) {
      String shape = set.shape(caseCode);
      CaseGroup group = set.groupOf(caseCode);
      String word = shape != null ? shape : group == null ? null : group.getName(getActivity());
      if (word != null) {
        addChip(word, false);
      }
    }
  }

  private void addChip(String text, boolean accent) {
    ViewGroup chips = root.findViewById(R.id.flCaseChips);
    TextView chip = new TextView(getActivity(), null, 0,
        accent ? R.style.RowChipAccent : R.style.RowChip);
    chip.setText(text);
    chip.setTextSize(12);
    chips.addView(chip);
  }

  /** Not inside a drill, where the drill's own Alg is the way, and two drills would share a cube. */
  private boolean offersPractice() {
    return !(getActivity() instanceof DrillScreenActivity);
  }

  private void showPracticeButton() {
    practice.setVisibility(offersPractice() && DrillActivity.hasAlgorithmToTry(caseCode)
        ? View.VISIBLE : View.GONE);
  }

  private void practice() {
    if (SmartCubeManager.INSTANCE.isConnected()) {
      // A starred algorithm is known to be theirs: drill the case, recorded; else choose one first.
      startActivity(Options.INSTANCE.getCaseAlgorithm(caseCode) != null
          ? DrillActivity.drillFor(getActivity(), caseCode)
          : DrillActivity.chooseFor(getActivity(), caseCode));
    } else {
      DialogUtils.showFragment(getActivity(), new SmartCubeConnectDialog());
    }
  }

  /** The case on the 3D cube, standing still: a picture of the case, not a mirror of the cube. */
  private void showPairCase(WebView webView) {
    String slot = CaseAlgorithms.slotOf(caseCode);
    String facelets = slot == null
        ? F2LCaseAlgorithms.crossUpFacelets(CaseAlgorithms.pairCase(caseCode))
        : F2LSlotAlgorithms.crossUpFacelets(CaseAlgorithms.pairCase(caseCode), slot);
    if (facelets == null) {
      return;
    }
    cube = new VirtualCube(webView, null, () -> { });
    cube.setGyroFollowing(false);
    cube.setHold(CubeRotation.byNotation("z2").quaternion());
    // Looked at from the side of the pair's slot: behind the cube for a back one.
    cube.setView(VIEW_LATITUDE, slot == null || F2LSlotAlgorithms.FRONT_RIGHT.equals(slot)
        ? VIEW_LONGITUDE : F2LSlotAlgorithms.FRONT_LEFT.equals(slot) ? -VIEW_LONGITUDE
        : F2LSlotAlgorithms.BACK_RIGHT.equals(slot) ? VIEW_LONGITUDE + 180
        : 180 - VIEW_LONGITUDE);
    cube.setCameraDistance(CAMERA_DISTANCE);
    cube.setNudge(0);
    cube.setState(CubePatternFormat.format(facelets));
    int at = Math.max(0, F2LSlotAlgorithms.SLOTS.indexOf(slot)); // front right for a plain pair
    cube.setStickering(CubeStickering.crossAndPair(CROSS_UP_EDGES, CROSS_UP_CORNERS[at],
        CROSS_UP_SLOT_EDGES[at]));
  }

  @Override
  public void onResume() {
    super.onResume();
    if (cube != null) {
      cube.onResume();
    }
    // Back from practice, where an algorithm may have been kept
    String nowChosen = Options.INSTANCE.getCaseAlgorithm(caseCode);
    String nowOwn = Options.INSTANCE.getOwnCaseAlgorithm(caseCode);
    if (!Objects.equals(nowChosen, chosen) || !Objects.equals(nowOwn, own)) {
      chosen = nowChosen;
      own = nowOwn;
      refresh();
      flagsChanged();
    }
  }

  @Override
  public void onPause() {
    if (cube != null) {
      cube.onPause();
    }
    super.onPause();
  }

  @Override
  public void onDestroyView() {
    if (cube != null) {
      cube.destroy(); // a WebGL context outlives the sheet unless it is told not to
      cube = null;
    }
    super.onDestroyView();
  }

  /** Reads the case out of the handful of solves it came up in; the marks arrive when it is done. */
  private void readExecution() {
    App.INSTANCE.getService().getCaseSolves(CaseExecutions.codesFor(caseCode), SOLVES_READ,
        new DataCallback<List<SolveTime>>() {
          @Override
          public void onData(List<SolveTime> solves) {
            final List<Turned> read = read(CaseExecutions.spreadFrom(solves, caseCode));
            onUi(() -> {
              turned = read;
              refresh();
            });
          }
        });
  }

  /** Each execution named and read against the vote table, off the main thread. */
  private List<Turned> read(CaseExecutions.Spread spread) {
    List<Turned> read = new ArrayList<Turned>();
    for (CaseExecutions.Shown one : CaseExecutions.shownFrom(caseCode, spread)) {
      read.add(new Turned(one.getMoves(), CaseAlgorithms.read(caseCode, one.getExecuted()),
          one.getTimes(), one.getOf()));
    }
    return read;
  }

  /** The two figures cards. Both are asked before deciding the sheet is a timer-only one. */
  private void loadFigures() {
    final boolean slotted = CaseAlgorithms.slotOf(caseCode) != null;
    App.INSTANCE.getService().getDrillCaseStats(0, new DataCallback<List<DrillCaseStats>>() {
      @Override
      public void onData(List<DrillCaseStats> all) {
        DrillCaseStats found = null;
        for (DrillCaseStats one : all) {
          if (caseCode.equals(one.getCaseCode())) {
            found = one;
          }
        }
        final DrillCaseStats drills = found;
        if (slotted) { // a pair dealt into a slot is a drill's case, never met in a solve
          onUi(() -> showFigures(null, drills));
          return;
        }
        App.INSTANCE.getService().getCaseSolveStats(caseCode, SOLVE_FIGURES,
            new DataCallback<StepStats>() {
              @Override
              public void onData(final StepStats solves) {
                onUi(() -> showFigures(solves, drills));
              }
            });
      }
    });
  }

  /** Both cards always, an empty one in dashes, so the sheet keeps one layout whatever was timed. */
  private void showFigures(StepStats solves, DrillCaseStats drills) {
    measured = solves != null || drills != null;
    View solveCard = root.findViewById(R.id.icCaseSolves);
    View drillCard = root.findViewById(R.id.icCaseDrills);
    if (solves != null) {
      fillCard(solveCard, R.string.case_sheet_in_solves, solves.getCount(), solves.getMeanMs(),
          solves.getMeanRecognitionMs(), solves.getMeanExecutionMs(), solves.getBestMs());
    } else {
      emptyCard(solveCard, R.string.case_sheet_in_solves);
    }
    if (drills != null) {
      fillCard(drillCard, R.string.case_sheet_in_drills, drills.getCount(), drills.getMeanMs(),
          drills.getMeanRecognitionMs(), drills.getMeanExecutionMs(), drills.getBestMs());
      drillCard.setOnClickListener(v -> openAttempts());
    } else {
      emptyCard(drillCard, R.string.case_sheet_in_drills);
    }
    refresh();
  }

  private void fillCard(View card, int source, int count, long meanMs, long recognitionMs,
      long executionMs, long bestMs) {
    FormatterService format = FormatterService.INSTANCE;
    int hue = StepPalette.cfop(getActivity()).colorForCase(caseCode);
    ((TextView) card.findViewById(R.id.tvCaseFiguresSource)).setText(source);
    ((TextView) card.findViewById(R.id.tvCaseFiguresCount)).setText(String.valueOf(count));
    ((TextView) card.findViewById(R.id.tvCaseFiguresMean)).setText(format.formatSolveTime(meanMs));
    TextView recognition = card.findViewById(R.id.tvCaseFiguresRecognition);
    recognition.setText(format.formatSolveTime(recognitionMs));
    recognition.setTextColor(StepPalette.dim(hue));
    TextView execution = card.findViewById(R.id.tvCaseFiguresExecution);
    execution.setText(format.formatSolveTime(executionMs));
    execution.setTextColor(hue);
    ((DrillSplitBarView) card.findViewById(R.id.vCaseFiguresBar))
        .setSplit(recognitionMs, executionMs, hue);
    ((TextView) card.findViewById(R.id.tvCaseFiguresBest))
        .setText(getString(R.string.case_sheet_best, format.formatSolveTime(bestMs)));
  }

  private void emptyCard(View card, int source) {
    ((TextView) card.findViewById(R.id.tvCaseFiguresSource)).setText(source);
    ((TextView) card.findViewById(R.id.tvCaseFiguresCount)).setText("0");
    ((TextView) card.findViewById(R.id.tvCaseFiguresMean)).setText(NONE);
    TextView recognition = card.findViewById(R.id.tvCaseFiguresRecognition);
    recognition.setText(NONE);
    recognition.setTextColor(ContextCompat.getColor(getActivity(), R.color.secondary_text));
    TextView execution = card.findViewById(R.id.tvCaseFiguresExecution);
    execution.setText(NONE);
    execution.setTextColor(ContextCompat.getColor(getActivity(), R.color.secondary_text));
    ((DrillSplitBarView) card.findViewById(R.id.vCaseFiguresBar)).setSegments(new long[] {1},
        new int[] {ContextCompat.getColor(getActivity(), R.color.case_status_unseen)});
    ((TextView) card.findViewById(R.id.tvCaseFiguresBest))
        .setText(getString(R.string.case_sheet_best, NONE));
  }

  /** The case's drill attempts over every drill, where a rep can be thrown out. */
  private void openAttempts() {
    Intent intent = new Intent(getActivity(), DrillCaseStatsActivity.class);
    intent.putExtra(DrillCaseStatsActivity.EXTRA_CASE, caseCode);
    intent.putExtra(DrillCaseStatsActivity.EXTRA_WINDOW, DrillStatsWindow.ALL.name());
    startActivity(intent);
  }

  /** Where the case stands, as a chip beside the group. */
  private void loadStanding() {
    App.INSTANCE.getService().getCaseHistory(0, new DataCallback<CaseHistory>() {
      @Override
      public void onData(CaseHistory history) {
        CaseKnowledge.Status status = null;
        for (CaseKnowledge known : history.getCases()) {
          if (caseCode.equals(known.getCode())) {
            status = known.getStatus();
          }
        }
        final CaseKnowledge.Status standing = status;
        onUi(() -> {
          if (standing == CaseKnowledge.Status.KNOWN) {
            addChip(getString(R.string.known_algorithms_status_known), true);
          } else if (standing == CaseKnowledge.Status.NEEDS_REVIEW) {
            addChip(getString(R.string.known_algorithms_status_review), false);
          } else if (standing == CaseKnowledge.Status.TO_LEARN) {
            addChip(getString(R.string.known_algorithms_status_to_learn), false);
          }
        });
      }
    });
  }

  /** Runs on the main thread while the sheet is still up. */
  private void onUi(final Runnable work) {
    Activity activity = getActivity();
    if (activity == null) {
      return;
    }
    activity.runOnUiThread(() -> {
      if (isAdded() && root != null) {
        work.run();
      }
    });
  }

  /** No smart cube has ever timed the case, so the sheet is a reference card. */
  private boolean isTimerOnly() {
    return Boolean.FALSE.equals(measured) && turned.isEmpty()
        && CaseAlgorithms.slotOf(caseCode) == null;
  }

  /**
   * The listed algorithms in their own order; under them, when opened, the rest of the votes; and
   * under that what is theirs and not on the list: an algorithm they typed in, every execution of
   * theirs that is none of the listed ones, and the starred one when it is none of those either.
   * Nothing here depends on what is chosen or turned: those mark a row, they do not move it.
   */
  private void refresh() {
    rows.removeAllViews();
    more.removeAllViews();
    yours.removeAllViews();
    List<Listed> listed = CaseAlgorithms.shown(caseCode);
    List<String> shown = new ArrayList<String>();
    for (Listed algorithm : listed) {
      // Their own spelling of a listed algorithm stands in its place
      String moves = CaseAlgorithms.sameTurning(caseCode, algorithm.getMoves(), own) ? own
          : algorithm.getMoves();
      rows.addView(row(rows, moves, algorithm.isRecommended(), algorithm.getEmptySlot(),
          algorithm.getShare()));
      shown.add(moves);
    }
    List<String> theirs = new ArrayList<String>();
    if (own != null && !listedAlready(shown, own)) {
      theirs.add(own);
    }
    for (Turned one : turned) {
      if (!listedAlready(shown, one.moves) && !listedAlready(theirs, one.moves)) {
        theirs.add(one.moves);
      }
    }
    // A star on none of these (an execution gone from the recent solves) still gets its row
    if (chosen != null && !listedAlready(shown, chosen)) {
      starredRow = chosen;
    }
    if (starredRow != null && !listedAlready(shown, starredRow)
        && !listedAlready(theirs, starredRow)) {
      theirs.add(starredRow);
    }
    List<Listed> rest = new ArrayList<Listed>();
    for (Listed extra : CaseAlgorithms.more(caseCode)) {
      if (!listedAlready(theirs, extra.getMoves())) {
        rest.add(extra);
      }
    }
    for (String moves : theirs) {
      yours.addView(row(yours, moves, false, CaseAlgorithms.emptySlotOf(caseCode, moves), -1));
    }
    if (moreOpen) {
      for (Listed extra : rest) {
        more.addView(row(more, extra.getMoves(), false, extra.getEmptySlot(), -1));
      }
    }
    more.setVisibility(moreOpen && !rest.isEmpty() ? View.VISIBLE : View.GONE);
    showMore.setVisibility(rest.isEmpty() ? View.GONE : View.VISIBLE);
    showMore.setText(moreOpen ? getString(R.string.case_sheet_show_fewer)
        : getResources().getQuantityString(R.plurals.case_sheet_show_more, rest.size(),
            rest.size()));
    boolean any = yours.getChildCount() > 0;
    yoursLabel.setVisibility(any ? View.VISIBLE : View.GONE);
    yours.setVisibility(any ? View.VISIBLE : View.GONE);
    showSetup(listed);
    showPracticeButton();
  }

  /** The moves that put the picked algorithm's case on a solved cube, for a sheet with no cube. */
  private void showSetup(List<Listed> listed) {
    View setup = root.findViewById(R.id.llCaseSetup);
    String from = chosen != null ? chosen : own != null ? own
        : listed.isEmpty() ? null : listed.get(0).getMoves();
    String moves = isTimerOnly() && from != null ? LastLayerScrambles.forAlgorithm(from) : null;
    setup.setVisibility(moves == null ? View.GONE : View.VISIBLE);
    if (moves != null) {
      ((TextView) root.findViewById(R.id.tvCaseSetupMoves))
          .setText(AlgorithmRow.notation(moves));
    }
  }

  /** Whether a row is already up, asked of the turning rather than of the text. */
  private boolean listedAlready(List<String> shown, String moves) {
    for (String already : shown) {
      if (CaseAlgorithms.sameTurning(caseCode, already, moves)) {
        return true;
      }
    }
    return false;
  }

  /** What the solver turns for this row, by the turning, or null for none of their executions. */
  private Turned turnedFor(String moves) {
    for (Turned one : turned) {
      if (CaseAlgorithms.sameTurning(caseCode, one.moves, moves)) {
        return one;
      }
    }
    return null;
  }

  /**
   * One algorithm, as every list of a case's algorithms draws it.
   *
   * @param emptySlot the slot the algorithm needs empty ("fl", "br"), or null
   * @param share the percentage of sharers who turn it, or negative for no figure
   */
  private View row(ViewGroup parent, final String moves, boolean recommended, String emptySlot,
      float share) {
    boolean declared = CaseAlgorithms.sameTurning(caseCode, moves, chosen);
    Turned one = turnedFor(moves);
    // The outline follows the star; with none, the algorithm their solves turn
    boolean mine = chosen != null ? declared : one != null && one == turned.get(0);

    AlgorithmRow row = AlgorithmRow.inflate(LayoutInflater.from(getActivity()), parent)
        .moves(moves)
        .share(share >= 0 ? UsageShares.label(getActivity(), share) : null)
        .starred(declared)
        .mine(mine)
        .onClick(v -> choose(moves));
    // All marks that apply: a choice does not stop an algorithm being the recommended one.
    // "The one you use" comes from the execution, and only shows where it differs from the star.
    if (one != null && one == turned.get(0) && (used != null || !declared)) {
      row.mark(used != null ? R.string.case_algorithm_used : R.string.case_algorithm_mine, true);
    }
    if (declared && isTimerOnly()) {
      row.mark(R.string.case_algorithm_your_pick, true);
    }
    if (one != null && one.execution.isUnusual()) {
      row.mark(R.string.case_algorithm_unusual, false);
    }
    if (recommended) {
      row.mark(R.string.case_algorithm_recommended, false);
    }
    if (one != null && turned.size() > 1) {
      row.note(getString(R.string.case_algorithm_of_last, one.times, one.of));
    }
    if (one != null && one.execution.isUnusual() && one.execution.isLonger()) {
      row.note(getString(R.string.case_algorithm_longer, one.execution.getMoves(),
          one.execution.getUsualMoves()));
    }
    if (one != null && one.execution.isUnusual()) {
      muteToggle(row, one.moves);
    }
    if (emptySlot != null) {
      row.note(getString("fl".equals(emptySlot) ? R.string.case_algorithm_empty_fl
          : R.string.case_algorithm_empty_br));
    }
    return row.view();
  }

  /** Stops an algorithm off the list being pointed out, or starts it again. */
  private void muteToggle(AlgorithmRow row, final String moves) {
    final boolean muted = CaseFlags.isMuted(caseCode, moves);
    TextView toggle = row.note(getString(muted ? R.string.case_algorithm_unmute
        : R.string.case_algorithm_mute));
    toggle.setTextColor(ContextCompat.getColor(getActivity(), R.color.color_accent));
    toggle.setOnClickListener(v -> {
      CaseFlags.setMuted(caseCode, moves, !muted);
      flagsChanged();
      refresh();
    });
  }

  private void flagsChanged() {
    getParentFragmentManager().setFragmentResult(FLAGS_CHANGED, new Bundle());
  }

  /** Tapping the one already kept lets it go, so a wrong tap is undone the same way it was made. */
  private void choose(String moves) {
    chosen = CaseAlgorithms.sameTurning(caseCode, moves, chosen) ? null : moves;
    Options.INSTANCE.setCaseAlgorithm(caseCode, chosen);
    flagsChanged();
    refresh();
  }

  /**
   * An algorithm of the user's own, checked before it is kept: it has to solve the case it is filed
   * under, or a typo would come back weeks later as the algorithm they were told they use.
   */
  private void askForAlgorithm() {
    final EditText field = new EditText(getActivity());
    field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    field.setSingleLine();
    field.setTextColor(ContextCompat.getColor(getActivity(), R.color.white));
    int pad = dp(20);
    field.setPadding(pad, dp(8), pad, dp(8));

    final AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme)
        .setTitle(R.string.case_algorithm_add)
        .setView(field)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.save, null)
        .create();
    dialog.show();
    // Bound after showing, so a rejected algorithm leaves the dialog up with what was typed.
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      String typed = field.getText().toString().trim().replaceAll("\\s+", " ");
      if (!CaseAlgorithms.solves(caseCode, typed)) {
        DialogUtils.showInfoMessage(getActivity(), R.string.case_algorithm_wrong);
        return;
      }
      keepOwn(typed);
      dialog.dismiss();
    });
  }

  /** Keeps what was typed and starts using it, in the spelling it was typed in. */
  private void keepOwn(String typed) {
    chosen = typed;
    own = typed;
    Options.INSTANCE.setCaseAlgorithm(caseCode, chosen);
    Options.INSTANCE.setOwnCaseAlgorithm(caseCode, own);
    flagsChanged();
    refresh();
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density);
  }

  /** One execution of the case, named the way it is shown and read against the vote table. */
  private static final class Turned {

    private final String moves;
    private final AlgorithmExecution execution;
    private final int times;
    private final int of;

    Turned(String moves, AlgorithmExecution execution, int times, int of) {
      this.moves = moves;
      this.execution = execution;
      this.times = times;
      this.of = of;
    }
  }
}
