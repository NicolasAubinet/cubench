package com.cube.nanotimer.gui.widget.cases;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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
import com.cube.nanotimer.util.view.ShareRingView;

import java.util.ArrayList;
import java.util.List;

/**
 * One algorithm of a case, the same wherever a case's algorithms are listed: its place in the list,
 * plain notation in the app's algorithm face, the share of solvers who turn it as a ring, and a star
 * on the one the solver uses. Marks ("Recommended", "Unusual") and the share's figure make one line
 * under the moves, notes ("12 of your last 20") the lines under that, so the notation always has the
 * row's width and two rows line up move for move.
 */
public final class AlgorithmRow {

  private static final float SPACE_SCALE = 0.55f;
  /** A prime takes a whole cell too, which reads as a gap inside the move it belongs to. */
  private static final float PRIME_SCALE = 0.5f;

  private static final String SEPARATOR = " · ";

  private static Typeface algorithmFace;

  private final View view;
  private final TextView rank;
  private final TextView moves;
  private final TextView meta;
  private final ShareRingView ring;
  private final ImageView star;
  private final LinearLayout notes;
  /** The marks in the order given, each with whether it is said in the accent. */
  private final List<CharSequence> marks = new ArrayList<CharSequence>();
  private final List<Boolean> accents = new ArrayList<Boolean>();
  private CharSequence shareLabel;
  private float sharePercent;
  private int shareColor;
  private int place;
  private boolean mine;

  private AlgorithmRow(View view) {
    this.view = view;
    rank = view.findViewById(R.id.tvAlgorithmRowRank);
    moves = view.findViewById(R.id.tvAlgorithmRowMoves);
    meta = view.findViewById(R.id.tvAlgorithmRowMeta);
    ring = view.findViewById(R.id.vAlgorithmRowShare);
    star = view.findViewById(R.id.ivAlgorithmRowStar);
    notes = view.findViewById(R.id.llAlgorithmRowNotes);
    moves.setTypeface(face(view.getContext()));
    showRank();
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

  /** A monospace space and prime are a whole cell wide; narrowed, the moves read as moves. */
  public static CharSequence notation(String moves) {
    if (moves == null) {
      return null;
    }
    SpannableString spaced = new SpannableString(moves);
    for (int i = 0; i < moves.length(); i++) {
      char c = moves.charAt(i);
      if (c == ' ' || c == '\'') {
        spaced.setSpan(new ScaleXSpan(c == ' ' ? SPACE_SCALE : PRIME_SCALE), i, i + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    return spaced;
  }

  /** Its place in the list, from 1, or 0 for a row the list does not rank (one of their own). */
  public AlgorithmRow rank(int place) {
    this.place = place;
    showRank();
    return this;
  }

  /**
   * The usage share, or a null label where the case has none.
   *
   * @param label the share as it is written, "61%"
   * @param percent the share, 0 to 100, for the ring
   * @param color the ring's colour, the case's step's own
   */
  public AlgorithmRow share(CharSequence label, float percent, int color) {
    shareLabel = label == null ? null
        : view.getContext().getString(R.string.case_algorithm_share_users, label);
    sharePercent = percent;
    shareColor = color;
    ring.setVisibility(label == null ? View.GONE : View.VISIBLE);
    showShare();
    showMeta();
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

  /** Its place filled in the accent: the row is the solver's own, said or turned. */
  public AlgorithmRow mine(boolean mine) {
    this.mine = mine;
    showRank();
    showShare();
    return this;
  }

  public AlgorithmRow mark(int textResId, boolean accent) {
    marks.add(view.getContext().getString(textResId));
    accents.add(accent);
    showMeta();
    return this;
  }

  private void showRank() {
    rank.setText(place > 0 ? String.valueOf(place) : null);
    rank.setBackgroundResource(mine ? R.drawable.case_alg_rank_mine
        : place > 0 ? R.drawable.case_alg_rank : R.drawable.case_alg_rank_off);
    rank.setTextColor(color(mine ? R.color.case_alg_rank_mine_text : R.color.case_alg_rank_text));
  }

  private void showShare() {
    ring.setShare(sharePercent, mine ? color(R.color.lightblue) : shareColor);
  }

  /** The marks, then the share's figure, on one line under the moves. */
  private void showMeta() {
    SpannableStringBuilder line = new SpannableStringBuilder();
    for (int i = 0; i < marks.size(); i++) {
      append(line, marks.get(i), accents.get(i));
    }
    if (shareLabel != null) {
      append(line, shareLabel, false);
    }
    meta.setText(line);
    meta.setVisibility(line.length() == 0 ? View.GONE : View.VISIBLE);
  }

  private void append(SpannableStringBuilder line, CharSequence text, boolean accent) {
    if (line.length() > 0) {
      line.append(SEPARATOR);
    }
    int start = line.length();
    line.append(text);
    if (accent) {
      line.setSpan(new ForegroundColorSpan(color(R.color.lightblue)), start, line.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }

  private int color(int resId) {
    return ContextCompat.getColor(view.getContext(), resId);
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
