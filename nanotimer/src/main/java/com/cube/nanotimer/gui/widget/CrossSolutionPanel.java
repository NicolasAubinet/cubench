package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.scrambler.cross.CrossFace;
import com.cube.nanotimer.scrambler.cross.CrossFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * What a cross drill says once the rep is over: the moves the user turned, the shortest way there
 * was, and a way to walk that way on the drawn cube.
 *
 * <p><b>The way is walked here rather than read somewhere else.</b> The other ways of the same
 * length used to be a sheet over the cube, which is the one place they are no use: a sequence is
 * learned by turning it and watching what it does, and a sheet covers the only thing worth
 * watching. So the ways are switched in place and the moves are stepped onto the cube standing
 * above them, one at a time and back again.
 *
 * <p><b>Nothing is turned until it is asked for.</b> A way opens <em>unwalked</em>: the cube keeps
 * standing at whatever the rep ended on, which is the cross the user just built and the one thing
 * they want to look at. Only the rewind is live in that state, so the button that puts the scramble
 * back is also the only thing to press, and stepping begins where every way begins.
 *
 * <p><b>Turning your own cube walks it too</b>, once a way is being walked. A turn that is the next
 * one of it advances it exactly as the button would have. A turn that is not gives up on following
 * rather than guessing: the strip drops its colouring instead of claiming a position it cannot know,
 * and the rewind is the way back in.
 *
 * <p><b>Both strips are written in one frame:</b> cross-on-bottom, which is how a solver reads a
 * cross and how they hold one. The user's own turns are relabeled into it too, and both strips open
 * with the rotation that gets there, as the cross solver's own solutions do. Two rows an inch apart
 * spelling the same physical turn two ways is the one thing a panel like this must not do. The
 * rotation is not a move and cannot be turned, so it is drawn as what it is and the arrows step
 * over it.
 *
 * <p>What is <em>applied</em> stays in the frame the cube reports its own turns in, whatever is
 * displayed. The two differ by a whole-cube rotation for every face but D, and a rotation is
 * exactly what a cube never reports.
 */
public class CrossSolutionPanel extends LinearLayout {

  /** Where a stepped move goes: the drawn cube, and whatever else is following it. */
  public interface Listener {
    /** Turn the drawn cube by one move of the shown way, in the cube's own frame. */
    void onSolutionMove(String notation);

    /** Put the drawn cube back to the scramble as it was dealt. */
    void onRewind();
  }

  /** Nothing is being followed: the user turned something that is not in the shown way. */
  private static final int OFF_TRACK = -1;

  private static final float DISABLED_ALPHA = 0.3f;

  private Listener listener;

  private View yoursRow;
  private HorizontalScrollView yoursScroll;
  private TextView tvYours;
  private View bestCell;
  private TextView tvBestLabel;
  private View altGroup;
  private TextView tvAltIndex;
  private HorizontalScrollView bestScroll;
  private TextView tvBestMoves;
  private ImageButton btStepStart;
  private ImageButton btStepBack;
  private ImageButton btStepNext;

  /** Every way of the shortest length, in the frame the cube reports its turns in. */
  private List<String[]> solutions;
  /** The face the cross goes on, which is what the reading convention rotates to the bottom. */
  private CrossFace face;
  /** Which of them is shown. */
  private int shown;
  /** The shown way as it is read: relabeled cross-on-bottom, behind the rotation that gets there. */
  private String[] display;
  /** How many display tokens are not moves: the rotation the reading convention puts in front. */
  private int displayOffset;
  /** How many of the way's moves stand applied to the cube, or {@link #OFF_TRACK}. */
  private int step;
  /** The first half of a half turn, seen and waiting for its other half. */
  private String pendingHalf;

  public CrossSolutionPanel(Context context, AttributeSet attrs) {
    super(context, attrs);
    setOrientation(VERTICAL);
    LayoutInflater.from(context).inflate(R.layout.crossdrill_solution, this, true);

    yoursRow = findViewById(R.id.llCrossYours);
    yoursScroll = findViewById(R.id.svCrossYours);
    tvYours = findViewById(R.id.tvCrossYours);
    bestCell = findViewById(R.id.llCrossBest);
    tvBestLabel = findViewById(R.id.tvCrossBestLabel);
    altGroup = findViewById(R.id.llCrossAlts);
    tvAltIndex = findViewById(R.id.tvCrossAltIndex);
    bestScroll = findViewById(R.id.svCrossBest);
    tvBestMoves = findViewById(R.id.tvCrossBestMoves);
    btStepStart = findViewById(R.id.btCrossStepStart);
    btStepBack = findViewById(R.id.btCrossStepBack);
    btStepNext = findViewById(R.id.btCrossStepNext);

    findViewById(R.id.btCrossAltPrev).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        showWay(shown - 1);
      }
    });
    findViewById(R.id.btCrossAltNext).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        showWay(shown + 1);
      }
    });
    btStepStart.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        rewind();
        render();
      }
    });
    btStepBack.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        stepBack();
      }
    });
    btStepNext.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        stepForward();
      }
    });
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  /**
   * The face the drill builds its cross on, which is the frame everything here is written in. The
   * drill's, not a given solution's, so it is set once and before any rep: the user's own moves are
   * written in it from the first turn, long before there is a solution to show.
   */
  public void setCrossFace(CrossFace face) {
    this.face = face;
  }

  /**
   * The moves the user has turned this rep, already folded into the metric they are counted in.
   * Empty hides the strip: a rep with nothing turned has nothing to show, and an empty line under
   * the cube would only look like something failed to load.
   *
   * <p>It opens with the same rotation the way below it does, greyed the same way: the two rows are
   * one frame, so what puts you in that frame is said once per row rather than once per panel.
   */
  public void setYourMoves(List<String> moves) {
    if (moves == null || moves.isEmpty()) {
      yoursRow.setVisibility(GONE);
      return;
    }
    yoursRow.setVisibility(VISIBLE);
    SpannableStringBuilder text = new SpannableStringBuilder();
    String rotation = CrossFormatter.rotationPrefix(face);
    if (!rotation.isEmpty()) {
      text.append(rotation);
      text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(getContext(), R.color.gray600)),
          0, text.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    }
    for (String move : moves) {
      text.append(text.length() == 0 ? "" : "  ").append(CrossFormatter.moveOnBottom(face, move));
    }
    tvYours.setText(text);
    // The last move turned is the one worth seeing, so a strip too long for the screen shows its
    // end rather than its beginning.
    yoursScroll.post(new Runnable() {
      @Override
      public void run() {
        yoursScroll.fullScroll(View.FOCUS_RIGHT);
      }
    });
  }

  /**
   * The shortest ways there were, once the search has found them. The rep is over by then, so
   * there is nothing left to give away.
   *
   * @param ways every way of the shortest length, in the cube's own frame
   * @param length what those ways cost, which is the figure the rep is scored against
   */
  public void showSolutions(List<String[]> ways, int length) {
    if (ways == null || ways.isEmpty()) {
      hideSolutions();
      return;
    }
    solutions = ways;
    // Unwalked, and the cube untouched: the rep has just ended on the cross the user built, and
    // putting the scramble back under them to show them an answer takes away the answer they made.
    step = OFF_TRACK;
    pendingHalf = null;
    bestCell.setVisibility(VISIBLE);
    tvBestLabel.setText(getResources().getString(R.string.drill_cross_panel_shortest, length));
    altGroup.setVisibility(ways.size() > 1 ? VISIBLE : GONE);
    showWay(0);
  }

  /** Between reps is the only time there is an answer to show. */
  public void hideSolutions() {
    solutions = null;
    display = null;
    bestCell.setVisibility(GONE);
  }

  /** Nothing to say: a new scramble is about to go up. */
  public void clear() {
    hideSolutions();
    setYourMoves(null);
  }

  /**
   * A turn the user made on their own cube, between reps. It walks the shown way when it is the
   * next move of it, and gives up following when it is not: a position claimed from a turn that was
   * not in the way would put the highlight somewhere the cube is not.
   */
  public void onCubeTurned(String notation) {
    if (display == null || step == OFF_TRACK) {
      return;
    }
    String[] way = solutions.get(shown);
    if (pendingHalf != null) {
      if (pendingHalf.equals(notation)) {
        pendingHalf = null;
        step++;
      } else {
        step = OFF_TRACK;
      }
    } else if (step < way.length && way[step].equals(notation)) {
      step++;
    } else if (step < way.length && isHalfTurnOf(way[step], notation)) {
      pendingHalf = notation;
    } else if (step > 0 && way[step - 1].equals(inverse(notation))) {
      step--; // taken back, which is the one turn that is not a departure from the way
    } else {
      step = OFF_TRACK;
    }
    render();
  }

  private void showWay(int index) {
    if (solutions == null || solutions.isEmpty()) {
      return;
    }
    shown = (index + solutions.size()) % solutions.size();
    display = CrossFormatter.toCrossOnBottom(face, solutions.get(shown));
    displayOffset = display.length - solutions.get(shown).length;
    tvAltIndex.setText(getResources().getString(R.string.drill_cross_way_of,
        shown + 1, solutions.size()));
    // Another way is read from wherever the last one was left: while one is being walked its moves
    // are on the cube, and stepping into another from there would build nothing. While none is,
    // there is nothing on the cube to undo and nothing to take away from the user.
    if (step != OFF_TRACK) {
      rewind();
    }
    render();
  }

  private void rewind() {
    step = 0;
    pendingHalf = null;
    if (listener != null) {
      listener.onRewind();
    }
  }

  private void stepForward() {
    if (display == null) {
      return;
    }
    String[] way = solutions.get(shown);
    if (step == OFF_TRACK || step >= way.length) {
      return; // nothing is being walked: the rewind is the way in, and it is the only live button
    }
    if (listener != null) {
      listener.onSolutionMove(way[step]);
    }
    step++;
    pendingHalf = null;
    render();
  }

  private void stepBack() {
    if (display == null || step <= 0) {
      return;
    }
    String[] way = solutions.get(shown);
    if (listener != null) {
      listener.onSolutionMove(inverse(way[step - 1]));
    }
    step--;
    pendingHalf = null;
    render();
  }

  /** The way as it stands: what is turned already, what is next, and what is still to come. */
  private void render() {
    if (display == null) {
      return;
    }
    int done = ContextCompat.getColor(getContext(), R.color.gray600);
    int next = ContextCompat.getColor(getContext(), R.color.cube_yellow);
    int rest = ContextCompat.getColor(getContext(), R.color.white);
    SpannableStringBuilder text = new SpannableStringBuilder();
    int currentStart = -1;
    for (int i = 0; i < display.length; i++) {
      if (text.length() > 0) {
        text.append("  ");
      }
      int start = text.length();
      text.append(display[i]);
      int color;
      if (i < displayOffset) {
        color = done; // the rotation the reading convention opens with, which is not a move
      } else if (step == OFF_TRACK) {
        color = rest;
      } else if (i - displayOffset < step) {
        color = done;
      } else if (i - displayOffset == step) {
        color = next;
        currentStart = start;
      } else {
        color = rest;
      }
      text.setSpan(new ForegroundColorSpan(color), start, text.length(),
          Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    }
    tvBestMoves.setText(text);
    keepInView(currentStart);

    String[] way = solutions.get(shown);
    enable(btStepStart, step != 0);
    enable(btStepBack, step > 0);
    enable(btStepNext, step != OFF_TRACK && step < way.length);
  }

  /** Scrolls the strip so the move you are on is on the screen, for a way too long to fit. */
  private void keepInView(final int offset) {
    if (offset < 0) {
      return;
    }
    tvBestMoves.post(new Runnable() {
      @Override
      public void run() {
        if (tvBestMoves.getLayout() == null) {
          return;
        }
        int x = (int) tvBestMoves.getLayout().getPrimaryHorizontal(offset);
        int margin = (int) (48 * getResources().getDisplayMetrics().density);
        if (x < bestScroll.getScrollX() + margin) {
          bestScroll.smoothScrollTo(Math.max(0, x - margin), 0);
        } else if (x > bestScroll.getScrollX() + bestScroll.getWidth() - margin) {
          bestScroll.smoothScrollTo(x - bestScroll.getWidth() + margin, 0);
        }
      }
    });
  }

  private static void enable(ImageButton button, boolean enabled) {
    button.setEnabled(enabled);
    button.setAlpha(enabled ? 1f : DISABLED_ALPHA);
  }

  /** Whether a quarter turn is one of the two a half turn is made of. */
  private static boolean isHalfTurnOf(String token, String quarter) {
    return token.endsWith("2") && token.charAt(0) == quarter.charAt(0);
  }

  /** The move that undoes one. A half turn undoes itself. */
  public static String inverse(String move) {
    if (move.endsWith("2")) {
      return move;
    }
    return move.endsWith("'") ? move.substring(0, move.length() - 1) : move + "'";
  }

  /** The quarter turns a move is made of, which is how a cube would have reported it. */
  public static List<String> quartersOf(String move) {
    List<String> quarters = new ArrayList<String>();
    if (move.endsWith("2")) {
      String quarter = move.substring(0, 1);
      quarters.add(quarter);
      quarters.add(quarter);
    } else {
      quarters.add(move);
    }
    return quarters;
  }
}
