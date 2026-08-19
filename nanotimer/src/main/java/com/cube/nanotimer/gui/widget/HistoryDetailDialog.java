package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentManager;
import android.util.TypedValue;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SolveBreakdown;
import com.cube.nanotimer.cube.SolveMovesFormat;
import com.cube.nanotimer.cube.SolveSolution;
import com.cube.nanotimer.cube.SolveTypeMethod;
import com.cube.nanotimer.cube.StoredSolveReplay;
import com.cube.nanotimer.gui.widget.dialog.CommentSolveDialog;
import com.cube.nanotimer.gui.widget.dialog.CrossSolverDialog;
import com.cube.nanotimer.gui.widget.dialog.ScrambleViewDialog;
import com.cube.nanotimer.gui.widget.dialog.SolveReplayDialog;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.smartcube.step.BlindResidual;
import com.cube.nanotimer.smartcube.step.LostReading;
import com.cube.nanotimer.smartcube.step.ParityCheck;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.ScrambleFormatterService;
import com.cube.nanotimer.util.ScrambleViewNotation;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.view.CancelledMoveSpan;
import com.cube.nanotimer.util.view.FontFitTextView;
import com.cube.nanotimer.util.view.SolveStepBarView;
import com.cube.nanotimer.util.view.SolveStepBars;
import com.cube.nanotimer.util.view.SwipeSwitchLayout;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.PieceMark;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.SolveTimeAverages;
import com.cube.nanotimer.vo.SolveTypeStep;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class HistoryDetailDialog extends NanoTimerBottomSheetFragment {

  private static final String ARG_SOLVETIME = "solvetime";
  private static final String ARG_CUBETYPE = "cubetype";

  private TimeChangedHandler handler;
  private SolveNavigator navigator;

  private SolveTime solveTime; // the solve on show, which a swipe moves along the list
  private CubeType cubeType;
  private View view;
  private BottomSheetDialog dialog;

  public static HistoryDetailDialog newInstance(SolveTime solveTime, CubeType cubeType, TimeChangedHandler handler) {
    return newInstance(solveTime, cubeType, handler, null);
  }

  /** With a navigator the sheet can be swiped along the list the solve was opened from. */
  public static HistoryDetailDialog newInstance(SolveTime solveTime, CubeType cubeType,
      TimeChangedHandler handler, SolveNavigator navigator) {
    HistoryDetailDialog hd = new HistoryDetailDialog();
    hd.handler = handler;
    hd.navigator = navigator;

    Bundle bundle = new Bundle();
    bundle.putSerializable(ARG_SOLVETIME, solveTime);
    bundle.putSerializable(ARG_CUBETYPE, cubeType);
    hd.setArguments(bundle);
    return hd;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    view = getActivity().getLayoutInflater().inflate(R.layout.historydetail_dialog, null);

    Bundle args = getArguments();
    solveTime = (SolveTime) args.getSerializable(ARG_SOLVETIME);
    cubeType = (CubeType) args.getSerializable(ARG_CUBETYPE);

    dialog = new BottomSheetDialog(getActivity(), getTheme());
    dialog.setContentView(view);
    dialog.setCanceledOnTouchOutside(true);

    setUpActions(view);
    setUpSwipe(view);
    bindSolve(view);
    return dialog;
  }

  /**
   * Puts the solve on show. Everything it touches is put back to how the layout has it first, since
   * the same views are handed the next solve when the sheet is swiped along the list.
   */
  private void bindSolve(final View v) {
    if (getActivity() == null) {
      return; // dismissed while a swipe was still animating the next solve in
    }
    resetBinding(v);
    final SolveTime solveTime = this.solveTime;
    final CubeType cubeType = this.cubeType;

    final boolean stepped = solveTime.hasSteps();
    final boolean blind = solveTime.getSolveType().isBlind();
    if (stepped) {
      v.findViewById(R.id.averagesTable).setVisibility(View.GONE); // the breakdown below says it better
    } else if (blind) {
      v.findViewById(R.id.averagesTable).setVisibility(View.GONE);
      v.findViewById(R.id.trMeanOfThree).setVisibility(View.VISIBLE);
    }
    // Read for every solve rather than only for the ones showing an averages row: the verdict under
    // the time is taken from the same record, and a stepped solve has one too.
    App.INSTANCE.getService().getSolveTimeAverages(solveTime, new DataCallback<SolveTimeAverages>() {
      @Override
      public void onData(final SolveTimeAverages data) {
        Activity activity = getActivity();
        if (activity == null || data == null) {
          return;
        }
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (solveTime != HistoryDetailDialog.this.solveTime) {
              return; // swiped on while this was being read: it belongs to a solve no longer shown
            }
            if (!stepped) { // a stepped solve shows its splits instead, and they are already set
              if (blind) {
                ((TextView) v.findViewById(R.id.tvMeanOfThree)).setText(FormatterService.INSTANCE.formatSolveTime(data.getAvgOf5())); // avg5 contains mean of 3 for blind type (same DB column)
              } else {
                ((TextView) v.findViewById(R.id.tvAvgOfFive)).setText(FormatterService.INSTANCE.formatSolveTime(data.getAvgOf5(), "-"));
                ((TextView) v.findViewById(R.id.tvAvgOfTwelve)).setText(FormatterService.INSTANCE.formatSolveTime(data.getAvgOf12(), "-"));
                ((TextView) v.findViewById(R.id.tvAvgOfFifty)).setText(FormatterService.INSTANCE.formatSolveTime(data.getAvgOf50(), "-"));
                ((TextView) v.findViewById(R.id.tvAvgOfHundred)).setText(FormatterService.INSTANCE.formatSolveTime(data.getAvgOf100(), "-"));
              }
            }
            showVerdict(v, data, blind);
          }
        });
      }
    });

    // The sheet shows one breakdown, measured against the turning time rather than the recorded one,
    // so a DNF still has a breakdown and a turn rate. On a solve type with its own steps the user's
    // split is the one it is read through; the method's steps are still recorded either way.
    long durationMs = SolveBreakdown.solvingDurationMs(solveTime);
    if (!buildManualSteps(v, solveTime, durationMs)) {
      // Read the breakdown off the scramble and the moves rather than off what was stored beside
      // them, so a solve type whose method changed shows its whole history under the method it now
      // reads as. Falls back to what was recorded whenever the solve cannot be read again.
      StoredSolveReplay.Result reread = StoredSolveReplay.reinterpret(solveTime.getScramble(),
          solveTime.getSmartcubeMoves(), SolveTypeMethod.of(solveTime.getSolveType()));
      // A solve that was read and fitted nothing keeps what was recorded here, the same as one that
      // could not be read at all: emptying it is the re-reading pass's to do, once and on the store,
      // rather than something the sheet does silently every time it opens.
      boolean fresh = reread != null && reread.getMethod() != null;
      CubeMethod method = fresh ? reread.getMethod() : solveTime.getSmartcubeMethod();
      List<SolveStep> read = fresh ? reread.getSteps() : solveTime.getSmartcubeSteps();
      Integer stoppedStep =
          fresh ? reread.getStoppedStep() : solveTime.getSmartcubeStoppedStep();
      // The tail is derived rather than stored, so it is added back here, before anything reads the
      // breakdown: the solution splits its moves by the same step windows the bar draws.
      List<SolveStep> steps = SolveBreakdown.withTail(read, stoppedStep, durationMs,
          solveTime.getSmartcubeMoves(), method);
      buildBreakdown(v, steps, SolveSolution.from(solveTime.getSmartcubeMoves(), steps),
          getString(R.string.breakdown), null, method);
      showResidual(v, fresh ? reread.getResidual() : null);
      showParityCheck(v, fresh ? reread.getParityCheck() : null);
      showLostReading(v, fresh ? reread.getLostReading() : null);
    }

    TextView tvTime = (TextView) v.findViewById(R.id.tvTime);
    FontFitTextView tvScramble = (FontFitTextView) v.findViewById(R.id.tvScramble);

    v.findViewById(R.id.buPlusTwo).setEnabled(!solveTime.isDNF());
    ((Button) v.findViewById(R.id.buDNF))
        .setText(solveTime.canUndoDNF() ? R.string.undo_dnf : R.string.DNF);
    v.findViewById(R.id.imgPb).setVisibility(solveTime.isPb() ? View.VISIBLE : View.GONE);

    final View scrambleCard = v.findViewById(R.id.scrambleCard);
    if (solveTime.getScramble() != null) {
      tvScramble.setText(ScrambleFormatterService.INSTANCE.formatToColoredScramble(solveTime.getScramble(), cubeType));
      scrambleCard.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View view) {
          String scramble = ScrambleFormatterService.INSTANCE.formatScrambleForExport(solveTime.getScramble(), cubeType);
          DialogUtils.copyScrambleToClipboard(getActivity(), scramble);
        }
      });
    } else {
      tvScramble.setText(R.string.no_scramble);
      scrambleCard.setClickable(false);
      scrambleCard.setForeground(null);
    }
    setUpScrambleTools(v, solveTime, cubeType);
    setUpReplay(v, solveTime, cubeType);
    ((TextView) v.findViewById(R.id.tvDate))
        .setText(FormatterService.INSTANCE.formatDateTime(solveTime.getTimestamp()));
    tvTime.setText(FormatterService.INSTANCE.formatMarkedSolveTime(solveTime));
    tvTime.setTextColor(color(solveTime.isDNF() ? R.color.dnf_time : R.color.white));
  }

  /**
   * Puts every view a binding touches back to its layout default, so nothing of the solve leaving
   * the sheet is left showing under the one arriving.
   */
  private void resetBinding(View v) {
    v.findViewById(R.id.detailScroll).scrollTo(0, 0); // a solve arrives read from the top
    v.findViewById(R.id.averagesTable).setVisibility(View.VISIBLE);
    v.findViewById(R.id.trMeanOfThree).setVisibility(View.GONE);
    v.findViewById(R.id.tvVerdict).setVisibility(View.GONE);
    v.findViewById(R.id.scrambleHeader).setVisibility(View.VISIBLE);
    v.findViewById(R.id.breakdownSection).setVisibility(View.GONE);
    v.findViewById(R.id.breakdownCard).setVisibility(View.VISIBLE);
    v.findViewById(R.id.breakdownTotals).setVisibility(View.GONE);
    v.findViewById(R.id.breakdownResidual).setVisibility(View.GONE);
    v.findViewById(R.id.breakdownParity).setVisibility(View.GONE);
    v.findViewById(R.id.breakdownLost).setVisibility(View.GONE);
    v.findViewById(R.id.movesSwitchLabel).setVisibility(View.VISIBLE);
    SwitchCompat moves = (SwitchCompat) v.findViewById(R.id.swMoves);
    moves.setVisibility(View.VISIBLE);
    moves.setOnCheckedChangeListener(null); // the next binding sets it, and would trip this one
    v.findViewById(R.id.buReplay).setVisibility(View.GONE);
    ((TableLayout) v.findViewById(R.id.breakdownTable)).removeAllViews();
    ((SolveStepBarView) v.findViewById(R.id.breakdownBar)).setHighlightedStep(-1);
    breakdownRows.clear();
    breakdownSteps = null;
    pickedStep = -1;

    View scrambleCard = v.findViewById(R.id.scrambleCard);
    scrambleCard.setClickable(true);
    TypedValue background = new TypedValue();
    getActivity().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
    scrambleCard.setForeground(ContextCompat.getDrawable(getActivity(), background.resourceId));
  }

  /** The actions on the solve, wired once: each reads whichever solve the sheet is showing. */
  private void setUpActions(View v) {
    v.findViewById(R.id.buPlusTwo).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        if (!solveTime.isDNF()) {
          solveTime.setPlusTwo(!solveTime.isPlusTwo(), true);
          saveTime(solveTime);
        }
        dialog.dismiss();
      }
    });

    v.findViewById(R.id.buDNF).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        if (solveTime.canUndoDNF()) {
          solveTime.undoDNF();
          saveTime(solveTime);
        } else if (!solveTime.isDNF()) {
          solveTime.setDNF();
          saveTime(solveTime);
        }
        dialog.dismiss();
      }
    });

    v.findViewById(R.id.buDelete).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        final SolveTime deleted = solveTime;
        App.INSTANCE.getService().deleteTime(deleted, new DataCallback<SolveAverages>() {
          public void onData(SolveAverages data) {
            handler.onTimeDeleted(deleted); // once deleted, so a handler may safely re-read the averages
          }
        });
        dialog.dismiss();
      }
    });

    v.findViewById(R.id.buShareTime).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        DialogUtils.shareTime(getActivity(), solveTime, cubeType);
      }
    });

    v.findViewById(R.id.buComment).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        DialogUtils.showFragment(getActivity(), CommentSolveDialog.newInstance(solveTime, handler));
      }
    });
  }

  /**
   * Hands the sheet along the list it was opened from. A swipe with nothing beyond it springs back,
   * which is how the ends of the list are felt rather than announced.
   */
  private void setUpSwipe(View v) {
    if (navigator == null) { // opened from somewhere with no list to walk, the timer among them
      return;
    }
    ((SwipeSwitchLayout) v).setOnSwitch(new SwipeSwitchLayout.OnSwitch() {
      @Override
      public boolean onSwitch(int direction) {
        if (getActivity() == null) {
          return false; // the throw outlived the sheet, so there is nothing to hand anything to
        }
        SolveTime neighbour = navigator.getNeighbourSolve(solveTime, direction);
        if (neighbour == null) {
          return false;
        }
        solveTime = neighbour;
        bindSolve(view);
        return true;
      }
    });
  }

  /** A rank is worth naming when it is in the all-time top ten, and in the top quarter of them. */
  private static final int TOP_RANK = 10;
  private static final int TOP_PART = 4;
  /** The fraction of the average a solve has to be off by before it is worth a word: a twentieth. */
  private static final int NOTABLE_PART = 20;

  /**
   * The one line that says what the solve was worth, in the order the reasons outrank each other: a
   * record first, then a place in the all-time top ten, and failing both, the solve against the form
   * you were in. Whichever it lands on, the chip takes that reason's colour.
   *
   * <p>Nothing is shown when there is nothing true to say. A DNF has no standing, and a young solve
   * type has no ranking worth quoting, so the chip stays away rather than reaching for filler.
   */
  private void showVerdict(View v, SolveTimeAverages data, boolean blind) {
    CharSequence text = null;
    int color = 0;
    if (data.getRank() == 1) {
      // A tie for the record leaves no margin to quote, so the record is left to say it alone.
      long margin = data.getRunnerUp() == null ? 0 : data.getRunnerUp() - data.getTime();
      text = margin > 0 ? getString(R.string.verdict_best_ever_by, formatTime(margin))
          : getString(R.string.verdict_best_ever);
      color = color(R.color.new_record);
    } else if (data.getRank() > 1 && data.getRank() <= TOP_RANK
        && data.getRank() * TOP_PART <= data.getRankedCount()) {
      text = getResources().getStringArray(R.array.verdict_ranks)[data.getRank() - 2];
      color = color(R.color.lightblue);
    } else if (data.getRank() > 0) {
      Form form = verdictForm(data, blind);
      long diff = form == null ? 0 : form.average - data.getTime();
      if (form != null && Math.abs(diff) * NOTABLE_PART >= form.average) {
        boolean faster = diff > 0;
        text = getString(faster ? R.string.verdict_faster : R.string.verdict_slower,
            formatTime(Math.abs(diff)), getString(form.label));
        // Only the good half is coloured: being off your average is the ordinary case, and a red
        // chip under half of all solves would say nothing except that solves vary.
        color = faster ? color(R.color.green) : color(R.color.secondary_text);
      }
    }
    if (text == null) {
      return;
    }
    TextView chip = (TextView) v.findViewById(R.id.tvVerdict);
    chip.setText(text);
    chip.setTextColor(color);
    chip.setBackground(verdictChipBackground(color));
    chip.setVisibility(View.VISIBLE);
  }

  // What a blind solve was left in, which is the one thing its solver could not see for themselves.
  private void showResidual(View v, BlindResidual residual) {
    if (residual == null || residual.getShape() == BlindResidual.Shape.SOLVED) {
      return;
    }
    String pieces = residual.getPieces();
    String text;
    switch (residual.getShape()) {
      case EDGE_CYCLE: text = getString(R.string.blind_left_edge_cycle, pieces); break;
      case CORNER_CYCLE: text = getString(R.string.blind_left_corner_cycle, pieces); break;
      case PARITY: text = getString(R.string.blind_left_parity, pieces); break;
      case FLIPPED: text = getString(R.string.blind_left_flipped, pieces); break;
      case TWISTED: text = getString(R.string.blind_left_twisted, pieces); break;
      case TURNED: text = getString(R.string.blind_left_turned, pieces); break;
      case MIXED: text = getString(R.string.blind_left_pieces, pieces); break;
      default: text = getString(R.string.blind_left_scattered, residual.getCount()); break;
    }
    // A piece turned where it stands is a different mistake from one in a foreign slot, so it is
    // said after the shape rather than counted into it.
    if (!residual.getTurned().isEmpty()) {
      text += "\n" + getString(R.string.blind_left_also_turned, residual.getTurned());
    }
    TextView line = (TextView) v.findViewById(R.id.breakdownResidual);
    line.setText(text);
    line.setVisibility(View.VISIBLE);
  }

  // The parity is the one mistake the marks cannot point at: skipping it breaks no algorithm.
  private void showParityCheck(View v, ParityCheck check) {
    if (check == null) {
      return;
    }
    TextView line = (TextView) v.findViewById(R.id.breakdownParity);
    line.setText(check == ParityCheck.SKIPPED
        ? R.string.blind_parity_skipped : R.string.blind_parity_needless);
    line.setVisibility(View.VISIBLE);
  }

  // Where the reading stopped, under the verdict: the table simply ends there, and a table that
  // ends early otherwise reads as a solve that ended early.
  private void showLostReading(View v, LostReading lost) {
    if (lost == null) {
      return;
    }
    TextView line = (TextView) v.findViewById(R.id.breakdownLost);
    line.setText(getString(R.string.blind_reading_lost,
        Utils.toSmartCubeStepLocalizedName(getActivity(), lost.getAfter(), 0), lost.getMoves()));
    line.setVisibility(View.VISIBLE);
  }

  /** The form a solve is held against: an average, and the name to quote it by. */
  private static final class Form {
    private final long average;
    private final int label;

    private Form(long average, int label) {
      this.average = average;
      this.label = label;
    }
  }

  /**
   * The form the solve is held against: the Ao12 around it, and the mean of 3 for a blind solve, the
   * only average its screen quotes. A window a DNF took out is skipped along with one that never
   * filled, which is why the Ao5 stands in. Null when even that is missing.
   *
   * <p>The rank above is lifetime, this is deliberately not. A wider window is steadier but it is
   * also older, and a year of getting faster leaves it saying an ordinary solve beat your average.
   */
  private Form verdictForm(SolveTimeAverages data, boolean blind) {
    if (blind) {
      return isSet(data.getAvgOf5()) ? new Form(data.getAvgOf5(), R.string.mo3_label) : null;
    }
    if (isSet(data.getAvgOf12())) {
      return new Form(data.getAvgOf12(), R.string.ao12_label);
    }
    return isSet(data.getAvgOf5()) ? new Form(data.getAvgOf5(), R.string.ao5_label) : null;
  }

  /** An average is there to be compared against only when it is a time: -1 is a DNF, null unfilled. */
  private boolean isSet(Long average) {
    return average != null && average > 0;
  }

  /** The chip's own colour, washed down to a background the text can still be read against. */
  private Drawable verdictChipBackground(int color) {
    GradientDrawable background = new GradientDrawable();
    background.setCornerRadius(dp(20));
    background.setColor(ColorUtils.setAlphaComponent(color, 0x26));
    return background;
  }

  /**
   * The scramble-derived study tools shown in the scramble header: a visual scramble diagram, and
   * (3x3 full scrambles only) the optimal-cross solver. Each button is hidden when it doesn't apply,
   * and the whole header goes with the card when there is no scramble to work from.
   */
  private void setUpScrambleTools(View v, final SolveTime solveTime, final CubeType cubeType) {
    ImageButton buScrambleView = (ImageButton) v.findViewById(R.id.buScrambleView);
    ImageButton buCrossSolver = (ImageButton) v.findViewById(R.id.buCrossSolver);

    if (solveTime.getScramble() == null) {
      v.findViewById(R.id.scrambleHeader).setVisibility(View.GONE);
      return;
    }
    // The scramble is stored as one string; the tools below want it split back into moves.
    final String[] scramble = ScrambleFormatterService.INSTANCE
        .parseStringScrambleToArray(solveTime.getScramble(), cubeType);

    boolean scrambleViewAvailable = ScrambleViewNotation.getRenderKey(cubeType) != null;
    buScrambleView.setVisibility(scrambleViewAvailable ? View.VISIBLE : View.GONE);
    if (scrambleViewAvailable) {
      buScrambleView.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View view) {
          openScrambleView(cubeType, scramble);
        }
      });
    }

    ScrambleType scrambleType = solveTime.getSolveType().getScrambleType();
    boolean crossSolverAvailable = cubeType == CubeType.THREE_BY_THREE
        && (scrambleType == null || scrambleType.isDefault()); // null scramble type means the default full scramble
    buCrossSolver.setVisibility(crossSolverAvailable ? View.VISIBLE : View.GONE);
    if (crossSolverAvailable) {
      buCrossSolver.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View view) {
          String single = ScrambleFormatterService.INSTANCE.formatScrambleAsSingleLine(scramble, cubeType);
          DialogUtils.showFragment(getActivity(), CrossSolverDialog.newInstance(single));
        }
      });
    }
  }

  /**
   * The replay button, on the breakdown line. Called after the breakdown is built, because a solve
   * can carry a move stream the analyzers could not read into steps — an early DNF, a method that
   * matched nothing. {@code buildBreakdown} leaves the whole section hidden for those, and they are
   * the solves a replay explains best, so the section is opened for the button alone and the table
   * card stays hidden. Without this the button would be silently unreachable exactly there.
   */
  private void setUpReplay(View v, final SolveTime solveTime, final CubeType cubeType) {
    ImageButton buReplay = (ImageButton) v.findViewById(R.id.buReplay);
    View section = v.findViewById(R.id.breakdownSection);
    final String puzzleId = ScrambleViewNotation.getPuzzleId(cubeType);
    final String cubingScramble = solveTime.getScramble() == null ? null
        : ScrambleViewNotation.toCubingNotation(ScrambleFormatterService.INSTANCE
            .parseStringScrambleToArray(solveTime.getScramble(), cubeType), cubeType);

    if (!solveTime.hasSmartcubeMoves() || puzzleId == null
        || cubingScramble == null || cubingScramble.isEmpty()) {
      buReplay.setVisibility(View.GONE);
      return;
    }
    buReplay.setVisibility(View.VISIBLE);
    if (section.getVisibility() != View.VISIBLE) {
      // Opened for the button alone: there is no table, and buildMovesSwitch never ran, so the
      // switch it would have hidden is still showing its layout default with nothing to toggle.
      section.setVisibility(View.VISIBLE);
      v.findViewById(R.id.breakdownCard).setVisibility(View.GONE);
      v.findViewById(R.id.movesSwitchLabel).setVisibility(View.GONE);
      v.findViewById(R.id.swMoves).setVisibility(View.GONE);
      ((TextView) v.findViewById(R.id.breakdownLabel)).setText(R.string.breakdown);
    }
    buReplay.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        // The same length the breakdown steps were measured against, so the bar the replay
        // scrubs and the replay itself cannot disagree about how long the solve was.
        DialogUtils.showFragment(getActivity(), SolveReplayDialog.newInstance(
            puzzleId, cubingScramble, solveTime.getSmartcubeMoves(),
            SolveBreakdown.solvingDurationMs(solveTime), breakdownSteps, solveTime.getId()));
      }
    });
  }

  private void openScrambleView(CubeType cubeType, String[] scramble) {
    String key = ScrambleViewNotation.getRenderKey(cubeType);
    String moves = ScrambleViewNotation.toCubingNotation(scramble, cubeType);
    String readable = ScrambleFormatterService.INSTANCE.formatScrambleAsSingleLine(scramble, cubeType);
    // When the diagram can't be drawn (a Clock pin notation), show the text and nudge toward the
    // notation that does render — mirrors TimerActivity.openScrambleView.
    String fallback = (moves == null && cubeType == CubeType.CLOCK)
        ? getString(R.string.scramble_view_clock_notation_hint) + "\n\n" + readable
        : readable;
    DialogUtils.showFragment(getActivity(), ScrambleViewDialog.newInstance(key, moves, fallback,
        ScrambleViewNotation.get3DPuzzleId(cubeType)));
  }

  /**
   * The breakdown drawn from the user's own steps, with the recorded moves split at the taps that
   * ended them. A solve no cube saw is drawn just the same, without the moves: the bar and the table
   * say the shape of it, which a line of times separated by slashes never did.
   *
   * <p>The split is approximate where the method breakdown is exact — a tap lands a moment after the
   * move it follows, and is timed on the phone's clock rather than the cube's. That is also why these
   * steps have no thinking/turning split: a tap says when a step ended, nothing more.
   *
   * @return true when these steps take the section over, so the method's breakdown is not drawn
   */
  private boolean buildManualSteps(View v, SolveTime solveTime, long durationMs) {
    if (!solveTime.hasSteps()) {
      return false;
    }
    List<SolveStep> steps = SolveBreakdown.fromStepTimes(solveTime.getStepsTimes());
    if (steps.isEmpty()) {
      return false;
    }
    SolveSolution solution = SolveSolution.from(solveTime.getSmartcubeMoves(), steps);
    SolveTypeStep[] names = solveTime.getSolveType().hasSteps()
        ? solveTime.getSolveType().getSteps() : new SolveTypeStep[0];
    buildBreakdown(v, steps, solution, getString(R.string.steps), names, null);
    return true;
  }

  /**
   * The step bar the timer showed, with the numbers behind it: a row per step, and its parts on rows
   * of their own, folded away until the step is tapped. The moves each row was spent on sit under it,
   * shown or hidden as a whole by the switch in the section header.
   *
   * @param userSteps the user's step names when their own steps are shown, null for the method's own
   *                  — the user's carry no thinking/turning split, so those columns are left out
   */
  private void buildBreakdown(View v, List<SolveStep> steps, SolveSolution solution,
      String label, SolveTypeStep[] userSteps, CubeMethod method) {
    if (steps == null || steps.isEmpty()) {
      return;
    }
    boolean split = userSteps == null;
    boolean moves = !solution.isEmpty(); // a solve no cube saw has a column's worth of nothing to say
    breakdownSteps = new ArrayList<SolveStep>(steps);
    int[] colors = getStepColors();
    // The rows stand in the order the solve was executed, names repeating where it came back to a
    // step; the colour is what says two of them are the same work.
    int[] slots = SolveStepBars.colorSlots(steps);
    ((SolveStepBarView) v.findViewById(R.id.breakdownBar)).setSteps(steps, colors);

    TableLayout table = (TableLayout) v.findViewById(R.id.breakdownTable);
    table.addView(headerRow(split, moves));
    for (int i = 0; i < steps.size(); i++) {
      SolveStep step = steps.get(i);
      TextView name = cell(R.style.BreakdownStepName, stepName(step, i, userSteps));
      name.setTextColor(Utils.isTailSegment(step.getName())
          ? ContextCompat.getColor(getActivity(), R.color.gray600)
          : colors[slots[i] % colors.length]);
      TableRow row = stepRow(step, name, moves ? moveCountOf(solution, i) : null, split);
      table.addView(row);

      StepRows stepRows = new StepRows(name);
      stepRows.row = row;
      stepRows.color = colors[slots[i] % colors.length];
      stepRows.moves = movesRow(table, R.style.BreakdownMoves, dim(groupsOf(solution, i)));
      List<SolveStep> parts = step.getSubSteps();
      for (int j = 0; j < parts.size(); j++) {
        TableRow partRow = subStepRow(parts.get(j), j, partMoveCountOf(solution, i, j));
        table.addView(partRow);
        stepRows.partRows.add(partRow);
        stepRows.partMoves.add(
            movesRow(table, R.style.BreakdownSubMoves, dim(partGroupOf(solution, i, j))));
      }
      breakdownRows.add(stepRows);
      makePickable(v, row, i);
      if (!stepRows.partRows.isEmpty()) {
        setChevron(name, stepRows.expanded);
      }
    }
    addTotalRow(table, steps, solution, split, moves);
    setUpBarPicking(v);
    buildMovesSwitch(v, solution, label, method);
    applyRowVisibility();
    v.findViewById(R.id.breakdownSection).setVisibility(View.VISIBLE);
  }

  /**
   * The columns added up under a rule at the foot of the table, so what the solve cost in thinking
   * against what it cost in turning is read rather than summed by eye.
   *
   * <p>A blind solve's memorisation is left out of it: it is recognition of the whole solve, it
   * dwarfs every step after it, and the split the row is here for is the split of the turning.
   */
  private void addTotalRow(TableLayout table, List<SolveStep> steps, SolveSolution solution,
      boolean split, boolean moves) {
    long recognitionMs = 0, executionMs = 0, totalMs = 0;
    int moveCount = 0, counted = 0;
    for (int i = 0; i < steps.size(); i++) {
      SolveStep step = steps.get(i);
      if (Utils.isMemoStep(step.getName())) {
        continue;
      }
      recognitionMs += step.getRecognitionMs();
      executionMs += step.getExecutionMs();
      totalMs += step.getTotalMs();
      moveCount += moveCountAt(solution, i);
      counted++;
    }
    if (counted < 2) {
      return; // a row that would only repeat the single one above it
    }
    table.addView(totalDivider());
    TableRow row = new TableRow(getActivity());
    row.addView(cell(R.style.BreakdownTotalName, getString(R.string.breakdown_total)));
    if (split) {
      row.addView(cell(R.style.BreakdownTotalRecognitionCell, formatTime(recognitionMs)));
      row.addView(cell(R.style.BreakdownTotalCell, formatTime(executionMs)));
    }
    row.addView(cell(R.style.BreakdownTotalCell, formatTime(totalMs)));
    if (moves) {
      row.addView(cell(R.style.BreakdownTotalRecognitionCell, String.valueOf(moveCount)));
    }
    table.addView(row);
  }

  /** The rule the total sits under, added to the table itself so it runs the full width. */
  private View totalDivider() {
    View line = new View(getActivity());
    TableLayout.LayoutParams params =
        new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
    params.topMargin = dp(7);
    line.setLayoutParams(params);
    line.setBackgroundColor(color(R.color.dialog_divider));
    return line;
  }

  /** The step the bar has been asked about, or -1 while it is describing the whole solve. */
  private int pickedStep = -1;

  /**
   * The bar as a way into the table rather than a picture beside it: touching a segment picks that
   * step out, dragging along the bar walks the pick with the finger, and tapping the picked segment
   * again hands the solve back whole.
   */
  private void setUpBarPicking(final View v) {
    final SolveStepBarView bar = (SolveStepBarView) v.findViewById(R.id.breakdownBar);
    bar.setOnSeekListener(new SolveStepBarView.OnSeekListener() {
      @Override
      public void onSeek(float fraction) {
        pickStep(v, bar.stepAt(fraction), false);
      }

      @Override
      public void onUnpick() {
        pickStep(v, pickedStep, true);
      }
    });
  }

  /** A row is the other end of the same handle, and the one that can put the pick back. */
  private void makePickable(final View v, TableRow row, final int index) {
    row.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View clicked) {
        pickStep(v, index, true);
      }
    });
  }

  /**
   * Picks one step out of the solve: the bar keeps that segment lit and fades the rest, the step's
   * row is washed in its own colour and is the only one showing its parts, and the table is scrolled
   * far enough to hold it. Picking the same step from the table again puts the whole solve back.
   *
   * @param toggle true when the pick came from the row it would pick, which is how it is undone
   */
  private void pickStep(View v, int index, boolean toggle) {
    if (index < 0 || index >= breakdownRows.size()) {
      return;
    }
    int picked = toggle && index == pickedStep ? -1 : index;
    if (picked == pickedStep) {
      return;
    }
    pickedStep = picked;
    ((SolveStepBarView) v.findViewById(R.id.breakdownBar)).setHighlightedStep(picked);
    for (int i = 0; i < breakdownRows.size(); i++) {
      StepRows step = breakdownRows.get(i);
      step.expanded = picked < 0 || i == picked;
      paintRowBackground(step.row, i == picked ? step.color : 0);
      if (!step.partRows.isEmpty()) {
        setChevron(step.name, step.expanded);
      }
    }
    applyRowVisibility();
    if (picked >= 0) {
      scrollRowIntoView(v, breakdownRows.get(picked).row);
    }
  }

  /** A wash of the step's own colour while it is picked, and the row's own ripple the rest of the
   * time: every row is a target now, and has to keep saying so. */
  private void paintRowBackground(TableRow row, int color) {
    if (color != 0) {
      row.setBackgroundColor(ColorUtils.setAlphaComponent(color, 0x24));
      return;
    }
    TypedValue background = new TypedValue();
    getActivity().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
    row.setBackgroundResource(background.resourceId);
  }

  /** Posted rather than run: the parts opening and closing move the row this is measuring. */
  private void scrollRowIntoView(View v, final View row) {
    final NestedScrollView scroll = (NestedScrollView) v.findViewById(R.id.detailScroll);
    scroll.post(new Runnable() {
      @Override
      public void run() {
        int[] rowAt = new int[2];
        int[] scrollAt = new int[2];
        row.getLocationInWindow(rowAt);
        scroll.getLocationInWindow(scrollAt);
        int top = rowAt[1] - scrollAt[1];
        int bottom = top + row.getHeight();
        if (top < 0) {
          scroll.smoothScrollBy(0, top - dp(8));
        } else if (bottom > scroll.getHeight()) {
          scroll.smoothScrollBy(0, bottom - scroll.getHeight() + dp(8));
        }
      }
    });
  }

  /** A user step goes by the name it was given, or by its position when the steps changed since. */
  private CharSequence stepName(SolveStep step, int index, SolveTypeStep[] userSteps) {
    if (userSteps == null) {
      return withCaseColor(step, index);
    }
    if (index < userSteps.length && userSteps[index].getName() != null) {
      return userSteps[index].getName();
    }
    return getString(R.string.breakdown_step) + " " + (index + 1);
  }

  /**
   * The rows of one step, so a change of switch or of fold can be applied to all of them at once.
   * A step's own moves stand in for its parts' while it is folded, and step it aside when it opens.
   */
  private static final class StepRows {
    private final TextView name;
    private final List<TableRow> partRows = new ArrayList<TableRow>();
    private final List<TextView> partMoves = new ArrayList<TextView>();
    private TableRow row;
    private int color;
    private TextView moves;
    private boolean expanded = true; // folding is for skimming; a solve opens fully told

    private StepRows(TextView name) {
      this.name = name;
    }
  }

  private final List<StepRows> breakdownRows = new ArrayList<StepRows>();
  private ArrayList<SolveStep> breakdownSteps; // what the bar in the sheet draws, and the replay scrubs
  private boolean showMoves;

  /** Shows the solve's move count and turn rate, and turns every moves row on or off at once. */
  private void buildMovesSwitch(View v, SolveSolution solution, String label, CubeMethod method) {
    ((TextView) v.findViewById(R.id.breakdownLabel)).setText(label);
    TextView movesLabel = (TextView) v.findViewById(R.id.movesSwitchLabel);
    SwitchCompat sw = (SwitchCompat) v.findViewById(R.id.swMoves);
    if (solution.isEmpty()) { // nothing was recorded to show: the switch would toggle empty rows
      movesLabel.setVisibility(View.GONE);
      sw.setVisibility(View.GONE);
      return;
    }
    TextView totals = (TextView) v.findViewById(R.id.breakdownTotals);
    StringBuilder text = new StringBuilder()
        .append(getString(R.string.breakdown_moves_count, solution.getMoveCount())).append(" · ")
        .append(getString(R.string.breakdown_tps,
            FormatterService.INSTANCE.formatTps(solution.getTps())));
    // A blind solve is read by how many algorithms it took; a sighted method's parts are fixed.
    if (method == CubeMethod.BLIND && solution.getPartCount() > 0) {
      text.append(" · ").append(getString(R.string.breakdown_algs, solution.getPartCount()));
    }
    totals.setText(text);
    totals.setVisibility(View.VISIBLE);
    showMoves = Options.INSTANCE.isBreakdownShowMoves();
    sw.setChecked(showMoves);
    sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton button, boolean checked) {
        showMoves = checked;
        Options.INSTANCE.setBreakdownShowMoves(checked);
        applyRowVisibility();
      }
    });
  }

  /**
   * A part's rows follow its step's fold; the moves rows follow the switch on top of that. A folded
   * step shows the moves of the whole step, an open one leaves them to its parts.
   */
  private void applyRowVisibility() {
    for (StepRows step : breakdownRows) {
      boolean hasParts = !step.partRows.isEmpty();
      setVisible(step.moves, showMoves && !(hasParts && step.expanded));
      for (int i = 0; i < step.partRows.size(); i++) {
        setVisible(step.partRows.get(i), step.expanded);
        setVisible(step.partMoves.get(i), step.expanded && showMoves);
      }
    }
  }

  private void setVisible(View view, boolean visible) {
    if (view != null) {
      view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
  }

  /**
   * The moves go straight into the table rather than into a row of it, so they run its whole width
   * instead of being squeezed into the name column. A step that turned nothing has no row at all.
   */
  private TextView movesRow(TableLayout table, int style, CharSequence moves) {
    if (moves.length() == 0) {
      return null;
    }
    TextView view = cell(style, moves);
    table.addView(view);
    return view;
  }

  /**
   * Greys the whole-cube rotations so the turns stand out from them. They are not moves and are
   * not counted, and setting them apart also makes a habit visible at a glance — more than one
   * rotation inside a single F2L pair, say.
   *
   * <p>Moves that undid each other are greyed too and struck through on top of it: they were
   * turned and they cost time, so they are shown, but the alg is what is left standing. A pair is
   * struck only where this row holds both halves of it, so what is crossed out here always reads as
   * one run — see {@link SolveSolution#cancelledIn}.
   */
  private CharSequence dim(List<List<SolveSolution.Token>> groups) {
    SpannableStringBuilder text = new SpannableStringBuilder();
    int color = ContextCompat.getColor(getActivity(), R.color.gray600);
    Set<SolveSolution.Token> cancelled = SolveSolution.cancelledIn(groups);
    for (List<SolveSolution.Token> group : groups) {
      if (group.isEmpty()) { // a part built with no move of its own would show as a stray separator
        continue;
      }
      if (text.length() > 0) {
        text.append(SolveSolution.GROUP_SEPARATOR);
      }
      for (int i = 0; i < group.size(); i++) {
        SolveSolution.Token token = group.get(i);
        if (i > 0) {
          text.append(' ');
        }
        int start = text.length();
        text.append(token.getNotation());
        boolean struck = cancelled.contains(token);
        if (struck) {
          text.setSpan(new CancelledMoveSpan(color), start, text.length(),
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (SolveMovesFormat.isRotation(token.getNotation())) {
          text.setSpan(new ForegroundColorSpan(color), start, text.length(),
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
    }
    return text;
  }

  private List<List<SolveSolution.Token>> groupsOf(SolveSolution solution, int stepIndex) {
    return stepIndex < solution.getSteps().size()
        ? solution.getSteps().get(stepIndex).getGroups()
        : Collections.<List<SolveSolution.Token>>emptyList();
  }

  /** One part on its own, in the shape {@link #dim} takes, so both rows are built the one way. */
  private List<List<SolveSolution.Token>> partGroupOf(SolveSolution solution, int stepIndex,
      int part) {
    List<List<SolveSolution.Token>> groups = groupsOf(solution, stepIndex);
    return part < groups.size()
        ? Collections.singletonList(groups.get(part))
        : Collections.<List<SolveSolution.Token>>emptyList();
  }

  private String moveCountOf(SolveSolution solution, int stepIndex) {
    return stepIndex < solution.getSteps().size()
        ? String.valueOf(solution.getSteps().get(stepIndex).getMoveCount()) : "";
  }

  private int moveCountAt(SolveSolution solution, int stepIndex) {
    return stepIndex < solution.getSteps().size()
        ? solution.getSteps().get(stepIndex).getMoveCount() : 0;
  }

  private String partMoveCountOf(SolveSolution solution, int stepIndex, int part) {
    return stepIndex < solution.getSteps().size()
        ? String.valueOf(solution.getSteps().get(stepIndex).getPartMoveCount(part)) : "";
  }

  private void setChevron(TextView name, boolean expanded) {
    name.setCompoundDrawablesWithIntrinsicBounds(0, 0,
        expanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down, 0);
  }

  private int[] getStepColors() {
    TypedArray stepColors = getResources().obtainTypedArray(R.array.solve_step_colors);
    int[] colors = new int[stepColors.length()];
    for (int i = 0; i < colors.length; i++) {
      colors[i] = stepColors.getColor(i, 0);
    }
    stepColors.recycle();
    return colors;
  }

  private TableRow headerRow(boolean split, boolean moves) {
    TableRow row = new TableRow(getActivity());
    row.addView(cell(R.style.BreakdownHeaderName, getString(R.string.breakdown_step)));
    if (split) {
      row.addView(cell(R.style.BreakdownHeaderCell, getString(R.string.breakdown_recognition)));
      row.addView(cell(R.style.BreakdownHeaderCell, getString(R.string.breakdown_execution)));
    }
    row.addView(cell(R.style.BreakdownHeaderCell, getString(R.string.breakdown_total)));
    if (moves) {
      row.addView(cell(R.style.BreakdownHeaderCell, getString(R.string.breakdown_moves)));
    }
    return row;
  }

  /** @param moveCount null on a solve with no moves, which drops the column rather than empty it */
  private TableRow stepRow(SolveStep step, TextView name, String moveCount, boolean split) {
    TableRow row = new TableRow(getActivity());
    row.addView(name);
    if (split) {
      row.addView(cell(R.style.BreakdownRecognitionCell, formatTime(step.getRecognitionMs())));
      row.addView(cell(R.style.BreakdownCell, formatTime(step.getExecutionMs())));
    }
    row.addView(cell(R.style.BreakdownCell, formatTime(step.getTotalMs())));
    if (moveCount != null) {
      row.addView(cell(R.style.BreakdownRecognitionCell, moveCount));
    }
    return row;
  }

  private TableRow subStepRow(SolveStep part, int position, String moveCount) {
    TableRow row = new TableRow(getActivity());
    row.addView(cell(R.style.BreakdownSubName, withPieceMarks(part, withPairColors(part.getName(),
        Utils.toSmartCubeStepLocalizedName(getActivity(), part.getName(), position)))));
    row.addView(cell(R.style.BreakdownSubCell, formatTime(part.getRecognitionMs())));
    row.addView(cell(R.style.BreakdownSubCell, formatTime(part.getExecutionMs())));
    row.addView(cell(R.style.BreakdownSubCell, formatTime(part.getTotalMs())));
    row.addView(cell(R.style.BreakdownSubCell, moveCount));
    return row;
  }

  /**
   * A last layer step is shown with the case it was left with, in the colour the table gives its
   * lesser figures rather than the step's own: which case it was is a detail of the step, and the
   * name is what the eye is running down the column for.
   */
  private CharSequence withCaseColor(SolveStep step, int index) {
    String name = Utils.toSmartCubeStepDisplayName(getActivity(), step, index);
    String caseLabel = Utils.toSmartCubeCaseLabel(getActivity(), step.getName());
    int at = caseLabel == null ? -1 : name.indexOf(caseLabel);
    if (at < 0) {
      return name;
    }
    SpannableStringBuilder text = new SpannableStringBuilder(name);
    text.setSpan(new ForegroundColorSpan(color(R.color.secondary_text)), at,
        at + caseLabel.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return text;
  }

  /**
   * Greens each piece a blind algorithm put home, which is what tells its shape apart at a glance: a
   * commutator lands both the targets it was shot at, one that breaks into a new cycle lands only
   * one of them, and a misfire lands none.
   *
   * <p><b>Red is the other half of it</b>: the solve was left with exactly the pieces this algorithm
   * named, still out, which is what an algorithm shot to the wrong sticker leaves behind. Anything
   * looser reddens algorithms that did nothing wrong, so a cycle left open and a parity never done
   * carry no red at all and are the verdict line's to explain.
   *
   * <p><b>The buffer of a solve with a parity is never green</b> — it holds a foreign piece until the
   * parity puts it right. Correct, and it reads as a bug the first time.
   *
   * <p>The pieces are found in the label rather than spelled into it: a translation wraps its own
   * words around the code's own pieces, so walking the label forward marks them wherever they fell.
   */
  private CharSequence withPieceMarks(SolveStep part, CharSequence label) {
    List<PieceMark> marks = part.getPieceMarks();
    if (marks.isEmpty()) {
      return label;
    }
    String[] pieces = Utils.getSmartCubeNamedPieces(part.getName());
    SpannableStringBuilder text = new SpannableStringBuilder(label);
    String plain = label.toString();
    int from = 0;
    for (int i = 0; i < pieces.length && i < marks.size(); i++) {
      int at = plain.indexOf(pieces[i], from);
      if (at < 0) {
        break; // the label does not spell the name the code does: nothing to mark it on
      }
      Integer mark = markColor(marks.get(i));
      if (mark != null) {
        text.setSpan(new ForegroundColorSpan(color(mark)), at, at + pieces[i].length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      from = at + pieces[i].length();
    }
    return text;
  }

  // Null for a piece the algorithm only moved through, which is the plain colour of the row.
  private static Integer markColor(PieceMark mark) {
    switch (mark) {
      case HOME: return R.color.piece_home;
      case WRONG: return R.color.piece_wrong;
      default: return null;
    }
  }

  /**
   * An F2L pair is labelled by the order it was built, so the two colours of its slot are what says
   * <em>which</em> pair it was. Solves recorded before the slot was stored simply keep the label.
   */
  private CharSequence withPairColors(String code, String label) {
    char[] faces = Utils.getSmartCubePairFaces(code);
    if (faces == null) {
      return label;
    }
    SpannableStringBuilder text = new SpannableStringBuilder(" " + label);
    text.setSpan(new ImageSpan(pairSwatch(faces), ImageSpan.ALIGN_BASELINE),
        0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return text;
  }

  /** The slot's two colours as one rectangle split down the middle, with the gap to the label built
   * into the drawable so it stays a fixed size rather than a space character's. */
  private Drawable pairSwatch(char[] faces) {
    int height = dp(9);
    int gap = dp(7);
    LayerDrawable swatch = new LayerDrawable(new Drawable[] {
        new ColorDrawable(color(Utils.getFaceColorRes(faces[0]))),
        new ColorDrawable(color(Utils.getFaceColorRes(faces[1]))),
    });
    swatch.setLayerInset(0, 0, 0, height + gap, 0);
    swatch.setLayerInset(1, height, 0, gap, 0);
    swatch.setBounds(0, 0, height * 2 + gap, height);
    return swatch;
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density);
  }

  private int color(int colorResId) {
    return ContextCompat.getColor(getActivity(), colorResId);
  }

  private TextView cell(int style, CharSequence text) {
    TextView cell = new TextView(getActivity(), null, 0, style);
    // The face has to be put on by hand here, and the weight is the style's to give.
    GUIUtils.setWeight(cell, cell.getTypeface() == null ? Typeface.NORMAL : cell.getTypeface().getStyle());
    cell.setText(text);
    return cell;
  }

  private String formatTime(long timeMs) {
    return FormatterService.INSTANCE.formatSolveTime(timeMs);
  }

  private void saveTime(final SolveTime solveTime) {
    App.INSTANCE.getService().saveTime(solveTime, new DataCallback<SolveAverages>() {
      @Override
      public void onData(SolveAverages data) {
        handler.onTimeChanged(solveTime);
      }
    });
  }

  @Override
  public void show(FragmentManager manager, String tag) {
    if (manager.findFragmentByTag(tag) == null) {
      super.show(manager, tag);
    }
  }

}
