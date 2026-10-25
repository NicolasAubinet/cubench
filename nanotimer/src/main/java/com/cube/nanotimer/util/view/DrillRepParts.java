package com.cube.nanotimer.util.view;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.smartcube.drill.DrillRep;
import com.cube.nanotimer.util.FormatterService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a rep's time is made of, in the order it was spent: the looking then the turning for a case
 * rep, or for a look-ahead rep the look, the first pair, the pause and the second pair.
 *
 * <p>The rep line, the summary card and the summary table all draw a rep from these parts, as a
 * {@link DrillSplitBarView} and as words, so every drill reads the same way on every screen.
 */
public final class DrillRepParts {

  /** One part: what it is called, how long it took, and the colour it is drawn in. */
  public static final class Part {

    private final int nameRes;
    private final long ms;
    private final int color;
    /** Said in its own colour, for the part the drill is about. */
    private final boolean lit;

    Part(int nameRes, long ms, int color, boolean lit) {
      this.nameRes = nameRes;
      this.ms = Math.max(0, ms);
      this.color = color;
      this.lit = lit;
    }

    public long getMs() {
      return ms;
    }
  }

  private static final String SPACE = "   ";

  private DrillRepParts() {
  }

  /** A rep's parts, read off the times it carries. */
  public static List<Part> of(Context context, DrillRep rep, int hue) {
    if (rep.getPauseMs() != null) {
      return lookAhead(context, rep.getRecognitionMs(), rep.getPairOneMs(), rep.getPauseMs(),
          rep.getPairTwoMs(), hue);
    }
    return split(rep.getRecognitionMs(), rep.getExecutionMs(), hue);
  }

  /**
   * The looking as a wash of the family's colour and the turning at full strength. With nothing
   * turned the looking is drawn at full strength, since a lone wash reads as disabled.
   */
  public static List<Part> split(long recognitionMs, long executionMs, int hue) {
    List<Part> parts = new ArrayList<Part>();
    parts.add(new Part(R.string.drill_summary_column_recognition, recognitionMs,
        executionMs > 0 ? StepPalette.wash(hue) : hue, false));
    parts.add(new Part(R.string.drill_summary_column_execution, executionMs, hue, false));
    return parts;
  }

  /** A look-ahead rep: the pairs in F2L's colour, the look as a wash of it, the pause lit. */
  public static List<Part> lookAhead(Context context, long beforeMs, long pairOneMs, long pauseMs,
      long pairTwoMs, int hue) {
    List<Part> parts = new ArrayList<Part>();
    parts.add(new Part(R.string.drill_lookahead_before, beforeMs, StepPalette.wash(hue), false));
    parts.add(new Part(R.string.drill_lookahead_pair, pairOneMs, hue, false));
    parts.add(new Part(R.string.drill_lookahead_pause, pauseMs,
        ContextCompat.getColor(context, R.color.drill_pause), true));
    parts.add(new Part(R.string.drill_lookahead_pair, pairTwoMs, hue, false));
    return parts;
  }

  /**
   * The mean of several reps' parts, part by part. The reps are of one drill, so their parts line
   * up; empty for no reps.
   */
  public static List<Part> mean(List<List<Part>> reps) {
    if (reps.isEmpty()) {
      return Collections.emptyList();
    }
    List<Part> mean = new ArrayList<Part>();
    for (int i = 0; i < reps.get(0).size(); i++) {
      long total = 0;
      for (List<Part> rep : reps) {
        total += rep.get(i).ms;
      }
      Part first = reps.get(0).get(i);
      mean.add(new Part(first.nameRes, total / reps.size(), first.color, first.lit));
    }
    return mean;
  }

  public static void draw(DrillSplitBarView bar, List<Part> parts) {
    long[] times = new long[parts.size()];
    int[] colors = new int[parts.size()];
    for (int i = 0; i < parts.size(); i++) {
      times[i] = parts.get(i).ms;
      colors[i] = parts.get(i).color;
    }
    bar.setSegments(times, colors);
  }

  /** The parts in words: each name quiet and its figure in white, a lit part wholly in its colour. */
  public static CharSequence words(Context context, List<Part> parts) {
    SpannableStringBuilder words = new SpannableStringBuilder();
    int white = ContextCompat.getColor(context, R.color.white);
    for (Part part : parts) {
      if (words.length() > 0) {
        words.append(SPACE);
      }
      int start = words.length();
      words.append(context.getString(part.nameRes)).append(' ');
      int figure = words.length();
      words.append(FormatterService.INSTANCE.formatSolveTime(part.ms));
      words.setSpan(new ForegroundColorSpan(part.lit ? part.color : white),
          part.lit ? start : figure, words.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return words;
  }
}
