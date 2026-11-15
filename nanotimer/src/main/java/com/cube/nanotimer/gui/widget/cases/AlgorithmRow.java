package com.cube.nanotimer.gui.widget.cases;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ScaleXSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.helper.GUIUtils;

/**
 * One algorithm of a case, the same wherever a case's algorithms are listed: plain notation in the
 * app's algorithm face, the share of solvers who turn it, and a star on the one the solver uses.
 * Marks ("Recommended", "Unusual") and notes ("12 of your last 20") sit under the moves, so the
 * notation always has the full width and two rows line up move for move.
 */
public final class AlgorithmRow {

  private static final float SPACE_SCALE = 0.55f;

  private static Typeface algorithmFace;

  private final View view;
  private final TextView moves;
  private final TextView share;
  private final ImageView star;
  private final ViewGroup marks;
  private final LinearLayout notes;

  private AlgorithmRow(View view) {
    this.view = view;
    moves = view.findViewById(R.id.tvAlgorithmRowMoves);
    share = view.findViewById(R.id.tvAlgorithmRowShare);
    star = view.findViewById(R.id.ivAlgorithmRowStar);
    marks = view.findViewById(R.id.flAlgorithmRowMarks);
    notes = view.findViewById(R.id.llAlgorithmRowNotes);
    moves.setTypeface(face(view.getContext()));
  }

  public static AlgorithmRow inflate(LayoutInflater inflater, ViewGroup parent) {
    return new AlgorithmRow(inflater.inflate(R.layout.algorithm_row, parent, false));
  }

  /** The face the drill writes an algorithm in, loaded once. */
  public static Typeface face(Context context) {
    if (algorithmFace == null) {
      algorithmFace = Typeface.createFromAsset(context.getApplicationContext().getAssets(),
          "fonts/JetBrainsMono-Medium.ttf");
    }
    return algorithmFace;
  }

  public View view() {
    return view;
  }

  public AlgorithmRow moves(String notation) {
    moves.setText(notation(notation));
    return this;
  }

  /** A monospace space is a whole cell wide; narrowed, the moves read as moves, not letters. */
  public static CharSequence notation(String moves) {
    if (moves == null) {
      return null;
    }
    SpannableString spaced = new SpannableString(moves);
    for (int i = moves.indexOf(' '); i >= 0; i = moves.indexOf(' ', i + 1)) {
      spaced.setSpan(new ScaleXSpan(SPACE_SCALE), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return spaced;
  }

  /** The usage share as it is labelled, or null where the case has none. */
  public AlgorithmRow share(CharSequence label) {
    share.setText(label);
    share.setVisibility(label == null ? View.GONE : View.VISIBLE);
    return this;
  }

  /** Filled on the solver's own algorithm, an outline on the others: a tap stars the row. */
  public AlgorithmRow starred(boolean starred) {
    star.setImageResource(starred ? R.drawable.ic_case_mine : R.drawable.ic_case_star_off);
    return this;
  }

  /** Hides the star, for a list where a row cannot be made the solver's own. */
  public AlgorithmRow noStar() {
    star.setVisibility(View.GONE);
    return this;
  }

  /** Outlined in the accent: the row is the solver's own, said or turned. */
  public AlgorithmRow mine(boolean mine) {
    view.setBackgroundResource(mine ? R.drawable.case_alg_mine : R.drawable.case_alg);
    return this;
  }

  public AlgorithmRow mark(int textResId, boolean accent) {
    TextView chip = new TextView(view.getContext(), null, 0,
        accent ? R.style.RowChipAccent : R.style.RowChip);
    chip.setText(textResId);
    chip.setTextSize(11);
    marks.addView(chip);
    marks.setVisibility(View.VISIBLE);
    return this;
  }

  /** A line under the moves, for what a mark is too short to say. Returned to be styled further. */
  public TextView note(CharSequence text) {
    TextView note = GUIUtils.newTextView(view.getContext());
    note.setText(text);
    note.setTextSize(12);
    note.setTextColor(ContextCompat.getColor(view.getContext(), R.color.secondary_text));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.topMargin = (int) (4 * view.getResources().getDisplayMetrics().density);
    note.setLayoutParams(params);
    notes.addView(note);
    notes.setVisibility(View.VISIBLE);
    return note;
  }

  public AlgorithmRow onClick(View.OnClickListener listener) {
    view.setOnClickListener(listener);
    return this;
  }
}
