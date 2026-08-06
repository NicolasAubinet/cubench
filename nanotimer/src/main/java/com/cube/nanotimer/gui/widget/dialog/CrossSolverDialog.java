package com.cube.nanotimer.gui.widget.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.Options.CrossNeutrality;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.CrossFaceSwatches;
import com.cube.nanotimer.gui.widget.NanoTimerDialogFragment;
import com.cube.nanotimer.gui.widget.SegmentedControl;
import com.cube.nanotimer.scrambler.cross.CrossFace;
import com.cube.nanotimer.scrambler.cross.CrossFormatter;
import com.cube.nanotimer.scrambler.cross.CrossSolvers;
import com.cube.nanotimer.scrambler.cross.CrossSolvers.FaceSolutions;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.util.helper.Utils;

import java.util.List;

/**
 * Shows the optimal CFOP cross solution(s) for the current 3x3 scramble. Read-only study aid; see
 * the cross solver plan. The BFS lookup is sub-millisecond but the per-face tables are built lazily
 * on a worker thread, so solving runs off the UI thread.
 */
public class CrossSolverDialog extends NanoTimerDialogFragment {

  private static final String ARG_SCRAMBLE = "scramble";

  // How many optimal solutions to show per face before the "Show all" toggle (the set can be large).
  private static final int INITIAL_SOLUTIONS_SHOWN = 6;

  private String scramble;
  private final CrossSolvers solvers = new CrossSolvers();

  private LinearLayout llFace;
  private LinearLayout llResults;
  private CrossFaceSwatches swatches;
  private SegmentedControl modeSegments;

  // Current selection (also persisted in Options); the controls are custom views, not spinners.
  private CrossNeutrality neutrality;
  private CrossFace face;

  // Increments on each solve request so stale worker results (after a quick spinner change) are ignored.
  private int requestId;

  public static CrossSolverDialog newInstance(String scramble) {
    CrossSolverDialog frag = new CrossSolverDialog();
    Bundle args = new Bundle();
    args.putString(ARG_SCRAMBLE, scramble);
    frag.setArguments(args);
    return frag;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    scramble = getArguments().getString(ARG_SCRAMBLE);

    LayoutInflater inflater = LayoutInflater.from(getActivity());
    View view = inflater.inflate(R.layout.crosssolver_dialog, null);

    llFace = view.findViewById(R.id.llCrossFace);
    llResults = view.findViewById(R.id.llCrossResults);

    neutrality = Options.INSTANCE.getCrossNeutrality();
    face = CrossFace.values()[Options.INSTANCE.getCrossFaceIndex(CrossFace.D.ordinal())];

    buildModeSegments(view);
    LinearLayout swatchRow = view.findViewById(R.id.llCrossSwatches);
    swatches = new CrossFaceSwatches(getActivity(), swatchRow, new CrossFaceSwatches.Listener() {
      @Override
      public void onFacePicked(CrossFace picked) {
        setFace(picked);
      }
    });
    refreshSwatchStyles();
    llFace.setVisibility(neutrality == CrossNeutrality.FULL ? View.GONE : View.VISIBLE);

    AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme)
        .setTitle(R.string.cross_solver)
        .setView(view)
        .setPositiveButton(R.string.close, null)
        .create();

    solve();
    return dialog;
  }

  // Segmented control (Specific / Dual / Full): one cell per neutrality mode.
  private void buildModeSegments(View view) {
    final CrossNeutrality[] modes = CrossNeutrality.values();
    String[] labels = { getString(R.string.cross_neutrality_specific),
        getString(R.string.cross_neutrality_dual), getString(R.string.cross_neutrality_full) };
    LinearLayout row = view.findViewById(R.id.llCrossModeSeg);
    modeSegments = new SegmentedControl(getActivity(), row, labels, new SegmentedControl.Listener() {
      @Override
      public void onSegmentPicked(int index) {
        setNeutrality(modes[index]);
      }
    });
    modeSegments.setSelection(neutrality.ordinal());
  }

  private void setNeutrality(CrossNeutrality m) {
    neutrality = m;
    Options.INSTANCE.setCrossNeutrality(m);
    modeSegments.setSelection(m.ordinal());
    // The face only matters for Specific/Dual.
    llFace.setVisibility(m == CrossNeutrality.FULL ? View.GONE : View.VISIBLE);
    refreshSwatchStyles();
    solve();
  }

  private void setFace(CrossFace f) {
    face = f;
    Options.INSTANCE.setCrossFaceIndex(f.ordinal());
    refreshSwatchStyles();
    solve();
  }

  // The dual mode's partner face is shown beside the picked one; the other modes have none.
  private void refreshSwatchStyles() {
    swatches.setSelection(face, neutrality == CrossNeutrality.DUAL ? face.opposite() : null);
  }

  private void solve() {
    final CrossNeutrality mode = this.neutrality;
    final CrossFace face = this.face;
    final int currentRequest = ++requestId;

    llResults.removeAllViews();
    llResults.addView(makeInfoLine(getString(R.string.cross_solving)));

    new Thread(new Runnable() {
      @Override
      public void run() {
        final List<FaceSolutions> results = solvers.solve(scramble, mode, face);
        if (getActivity() == null) {
          return;
        }
        getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (currentRequest == requestId && isAdded()) {
              render(mode, results);
            }
          }
        });
      }
    }).start();
  }

  private void render(CrossNeutrality mode, List<FaceSolutions> results) {
    llResults.removeAllViews();
    if (mode == CrossNeutrality.SPECIFIC) {
      // Single face: a card that is always expanded, no chevron.
      addSectionCard(results.get(0), false, false, true);
    } else {
      // Ranked comparison: collapsible cards, shortest first, with the shortest face(s) starred.
      // The top (best) card starts expanded so the easiest cross is visible at a glance.
      int bestLength = Integer.MAX_VALUE;
      for (FaceSolutions fs : results) {
        bestLength = Math.min(bestLength, fs.length);
      }
      for (int i = 0; i < results.size(); i++) {
        FaceSolutions fs = results.get(i);
        addSectionCard(fs, true, fs.length == bestLength, i == 0);
      }
    }
    // Drop the trailing card's bottom margin so it sits snug above the dialog's Close button.
    if (llResults.getChildCount() > 0) {
      View last = llResults.getChildAt(llResults.getChildCount() - 1);
      LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) last.getLayoutParams();
      lp.bottomMargin = 0;
      last.setLayoutParams(lp);
    }
  }

  // A section card for one face: accent header (face + move count) with a dim solution-count subtext,
  // and a body of monospace solution lines. Collapsible cards get a chevron and a tappable header.
  private void addSectionCard(final FaceSolutions fs, boolean collapsible, boolean best, boolean startExpanded) {
    LinearLayout card = new LinearLayout(getActivity());
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackgroundResource(R.drawable.cross_section_card);
    card.setPadding(dp(12), dp(10), dp(12), dp(10));
    LinearLayout.LayoutParams cardLp =
        new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    cardLp.bottomMargin = dp(8);
    card.setLayoutParams(cardLp);

    final boolean solved = fs.solutions.isEmpty() || fs.length == 0;

    LinearLayout header = new LinearLayout(getActivity());
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);

    final TextView chevron = (collapsible && !solved) ? makeChevron(startExpanded) : null;
    if (chevron != null) {
      header.addView(chevron);
    }

    header.addView(makeFaceDot(fs.face));

    LinearLayout titleBlock = new LinearLayout(getActivity());
    titleBlock.setOrientation(LinearLayout.VERTICAL);
    titleBlock.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

    TextView title = new TextView(getActivity());
    title.setText(getString(R.string.cross_section_title, fs.face.name(), fs.length));
    title.setTextColor(color(R.color.lightblue));
    GUIUtils.setWeight(title, Typeface.BOLD);
    title.setTextSize(16);
    titleBlock.addView(title);

    TextView subtext = GUIUtils.newTextView(getActivity());
    subtext.setTextColor(color(R.color.secondary_text));
    subtext.setTextSize(13);
    subtext.setText(solved ? getString(R.string.cross_subtext_solved)
        : getString(R.string.cross_solutions_count, fs.solutions.size()));
    titleBlock.addView(subtext);

    header.addView(titleBlock);

    if (best && !solved) {
      TextView star = new TextView(getActivity()); // left on the platform face: Roboto has no star
      star.setText("★");
      star.setTextColor(color(R.color.new_record));
      star.setTextSize(16);
      header.addView(star);
    }

    card.addView(header);

    if (!solved) {
      final LinearLayout body = new LinearLayout(getActivity());
      body.setOrientation(LinearLayout.VERTICAL);
      card.addView(body);

      if (startExpanded) {
        renderBody(body, fs, false);
        body.setVisibility(View.VISIBLE);
      } else {
        body.setVisibility(View.GONE);
      }

      if (collapsible) {
        header.setClickable(true);
        header.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            boolean expand = body.getVisibility() == View.GONE;
            if (expand && body.getChildCount() == 0) {
              renderBody(body, fs, false);
            }
            body.setVisibility(expand ? View.VISIBLE : View.GONE);
            if (chevron != null) {
              chevron.setText(expand ? "▾" : "▸");
            }
          }
        });
      }
    }

    llResults.addView(card);
  }

  // Fills a card body with a divider, the (capped or full) solution lines, and a "Show all/fewer"
  // toggle when there are more than INITIAL_SOLUTIONS_SHOWN. Expansion happens inline, within the
  // dialog's single scroll (no nested scrolling).
  private void renderBody(final LinearLayout body, final FaceSolutions fs, final boolean showAll) {
    body.removeAllViews();
    body.addView(makeDivider());

    int total = fs.solutions.size();
    int shown = showAll ? total : Math.min(total, INITIAL_SOLUTIONS_SHOWN);
    for (int i = 0; i < shown; i++) {
      body.addView(makeSolutionLine(CrossFormatter.toCrossOnBottom(fs.face, fs.solutions.get(i))));
    }

    if (total > INITIAL_SOLUTIONS_SHOWN) {
      TextView toggle = GUIUtils.newTextView(getActivity());
      toggle.setTextColor(color(R.color.lightblue));
      toggle.setTextSize(14);
      toggle.setPadding(0, dp(6), 0, dp(2));
      toggle.setText(showAll ? getString(R.string.cross_show_fewer)
          : getString(R.string.cross_show_all, total));
      toggle.setClickable(true);
      toggle.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          renderBody(body, fs, !showAll);
        }
      });
      body.addView(toggle);
    }
  }

  private TextView makeSolutionLine(String[] moves) {
    TextView tv = new TextView(getActivity());
    tv.setText(joinMoves(moves));
    tv.setTextColor(color(R.color.gray200));
    tv.setTextSize(14);
    tv.setTypeface(Typeface.MONOSPACE);
    tv.setPadding(dp(4), dp(3), 0, dp(3));
    return tv;
  }

  // Small color disc shown before a card title, tying the result back to the face swatch.
  private View makeFaceDot(CrossFace f) {
    View dot = new View(getActivity());
    GradientDrawable bg = new GradientDrawable();
    bg.setShape(GradientDrawable.OVAL);
    bg.setColor(color(Utils.getFaceColorRes(f.name().charAt(0))));
    bg.setStroke(dp(1), color(R.color.gray700));
    dot.setBackground(bg);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(14), dp(14));
    lp.rightMargin = dp(8);
    dot.setLayoutParams(lp);
    return dot;
  }

  private TextView makeChevron(boolean expanded) {
    TextView tv = new TextView(getActivity()); // left on the platform face: Roboto has no chevron
    tv.setText(expanded ? "▾" : "▸");
    tv.setTextColor(color(R.color.lightblue));
    tv.setTextSize(14);
    tv.setPadding(0, 0, dp(10), 0);
    return tv;
  }

  private View makeDivider() {
    View div = new View(getActivity());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
    lp.topMargin = dp(6);
    lp.bottomMargin = dp(4);
    div.setLayoutParams(lp);
    div.setBackgroundColor(color(R.color.gray700));
    return div;
  }

  private TextView makeInfoLine(String text) {
    TextView tv = GUIUtils.newTextView(getActivity());
    tv.setText(text);
    tv.setTextColor(color(R.color.secondary_text));
    tv.setTextSize(15);
    tv.setPadding(0, dp(4), 0, dp(4));
    return tv;
  }

  private static String joinMoves(String[] moves) {
    StringBuilder sb = new StringBuilder();
    for (String m : moves) {
      if (m.isEmpty()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(m);
    }
    return sb.toString();
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density);
  }

  private int color(int colorResId) {
    return ContextCompat.getColor(getActivity(), colorResId);
  }
}
