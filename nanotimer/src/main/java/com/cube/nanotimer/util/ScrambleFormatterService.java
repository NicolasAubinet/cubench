package com.cube.nanotimer.util;

import android.content.res.Configuration;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.Options.BigCubesNotation;
import com.cube.nanotimer.Options.ClockNotation;
import com.cube.nanotimer.R;
import com.cube.nanotimer.vo.CubeType;

import java.util.regex.Pattern;

public enum ScrambleFormatterService {
  INSTANCE;

  /**
   * Format the scramble to add enough spaces to align the moves vertically,
   * to split it among multiple lines and to color it.
   * @param scramble the scramble containing one move in each array element
   * @param cubeType the cube type for which to format the scramble
   * @return the formatted scramble
   */
  public Spannable formatToColoredScramble(String[] scramble, CubeType cubeType, int orientation) {
    String s = formatScramble(scramble, cubeType, orientation);
    return colorFormattedScramble(s, cubeType);
  }

  /**
   * Format the scramble to add enough spaces to align the moves vertically,
   * to split it among multiple lines and to color it.
   * @param scramble the scramble with space-separated moves.
   * @param cubeType the cube type for which to format the scramble
   * @return the formatted scramble
   */
  public Spannable formatToColoredScramble(String scramble, CubeType cubeType) {
    if (!scramble.contains("\n")) {
      String[] splitted = parseStringScrambleToArray(scramble, cubeType);
      if (getMovesPerLine(cubeType, splitted) > 0) {
        scramble = formatScramble(splitted, cubeType, null);
      }
    }
    return colorFormattedScramble(scramble, cubeType);
  }

  public String[] parseStringScrambleToArray(String scramble, CubeType cubeType) {
    String delimiter = String.valueOf(getScrambleDelimiter(cubeType));
    String totalDelimiter = delimiter;
    if (!" ".equals(delimiter)) {
      totalDelimiter += " ";
    }
    String[] splitted = scramble.split(Pattern.quote(totalDelimiter));
    if (!" ".equals(delimiter)) {
      for (int i = 0; i < splitted.length; i++) {
        splitted[i] += delimiter;
      }
    }
    return splitted;
  }

  /**
   * Color a scramble by smart-cube follow progress: already-executed moves fade to gray, the
   * expected next move is highlighted in yellow, and the remaining moves keep their original
   * column color. 3x3 only (space/newline-delimited single-char faces).
   * @param doneCount number of scramble moves correctly executed so far
   */
  public Spannable formatScrambleWithProgress(String[] scramble, CubeType cubeType, int orientation,
                                              int doneCount) {
    String s = formatScramble(scramble, cubeType, orientation);
    Spannable base = colorFormattedScramble(s, cubeType); // source of the pending column colors
    Spannable span = new SpannableString(s);
    int doneColor = getColor(R.color.gray600);
    int currentColor = getColor(R.color.cube_yellow);
    int pendingColor = getColor(R.color.white);

    int tokenIndex = 0;
    int i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == ' ' || c == '\n') {
        i++;
        continue;
      }
      int start = i;
      while (i < s.length() && s.charAt(i) != ' ' && s.charAt(i) != '\n') {
        i++;
      }
      int color;
      boolean bold = false;
      if (tokenIndex < doneCount) {
        color = doneColor;
      } else if (tokenIndex == doneCount) {
        color = currentColor;
        bold = true;
      } else {
        ForegroundColorSpan[] columnColor = base.getSpans(start, start + 1, ForegroundColorSpan.class);
        color = columnColor.length > 0 ? columnColor[0].getForegroundColor() : pendingColor;
      }
      span.setSpan(new ForegroundColorSpan(color), start, i, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
      if (bold) {
        span.setSpan(new StyleSpan(Typeface.BOLD), start, i, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
      }
      tokenIndex++;
    }
    return span;
  }

  private static final int MAX_REVERSE_MOVES_SHOWN = 7;

  /** Build the "undo your wrong moves" prompt shown when the follow goes off the scramble. */
  public Spannable formatReverseMoves(String header, String reverseMoves) {
    reverseMoves = capReverseMoves(reverseMoves);
    String text = header + "\n" + reverseMoves;
    Spannable span = new SpannableString(text);
    int movesStart = header.length() + 1;
    span.setSpan(new ForegroundColorSpan(getColor(R.color.gray400)), 0, header.length(),
        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    span.setSpan(new RelativeSizeSpan(0.85f), 0, header.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    span.setSpan(new ForegroundColorSpan(getColor(R.color.red)), movesStart, text.length(),
        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    span.setSpan(new StyleSpan(Typeface.BOLD), movesStart, text.length(),
        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    return span;
  }

  private static String capReverseMoves(String reverseMoves) {
    String[] moves = reverseMoves.split(" ");
    if (moves.length <= MAX_REVERSE_MOVES_SHOWN) {
      return reverseMoves;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < MAX_REVERSE_MOVES_SHOWN; i++) {
      sb.append(moves[i]).append(' ');
    }
    return sb.append('…').toString();
  }

  private int getColor(int resId) {
    return App.INSTANCE.getContext().getResources().getColor(resId);
  }

  /**
   * Color a scramble that is already formatted, based on spaces.
   * @param scramble the formatted scramble
   * @param cubeType the cube type for which to color the scramble
   * @return the colored scramble
   */
  private Spannable colorFormattedScramble(String scramble, CubeType cubeType) {
    Spannable span = new SpannableString(scramble);
    final char delimiter = getScrambleDelimiter(cubeType);
    int defaultColor = App.INSTANCE.getContext().getResources().getColor(R.color.white);
    int alternateColor = App.INSTANCE.getContext().getResources().getColor(R.color.lightblue);
    int prevLinesCharCount = 0;
    String[] lines = scramble.split("\n");
    String totalDelimiter = Pattern.quote(String.valueOf(delimiter)) + "\\s*";

    boolean twoElementsPerLine = true;
    for (String line : lines) {
      if (line.trim().split(totalDelimiter).length > 2) {
        twoElementsPerLine = false;
        break;
      }
    }

    int prevLineLength = 0;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      int curLineLength = line.trim().split(totalDelimiter).length;
      if (twoElementsPerLine) { // color one line out of two
        span.setSpan(new ForegroundColorSpan((i % 2 == 0) ? defaultColor : alternateColor),
            prevLinesCharCount, prevLinesCharCount + line.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
      } else { // color based on moves
        int prevInd = prevLinesCharCount;
        int colorInd = 0;
        char prevChar = ')'; // initialized with a parenthesis for square-1 to color starting from 2nd column (and not from the 1st one)
        if (curLineLength < prevLineLength) { // used when the last line is smaller, to adapt the first move's color
          colorInd = ((prevLineLength - curLineLength) / 2) % 2;
        }
        for (int j = 0; j < line.length(); j++) {
          char c = line.charAt(j);
          if (c == delimiter && prevChar != delimiter || j == line.length() - 1) {
            span.setSpan(new ForegroundColorSpan((colorInd % 2 == 0) ? defaultColor : alternateColor),
                prevInd, prevLinesCharCount + j + 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            prevInd = prevLinesCharCount + j + 1;
            colorInd++;
          }
          prevChar = c;
        }
      }
      prevLineLength = curLineLength;
      prevLinesCharCount += line.length() + 1;
    }
    return span;
  }

  /**
   * Format the scramble to add enough spaces to align the moves vertically
   * and to split it among multiple lines.
   * @param scramble the scramble containing one move in each array element
   * @param cubeType the cube type for which to format the scramble
   * @return the formatted scramble
   */
  public String formatScrambleForExport(String scramble, CubeType cubeType) {
    if (cubeType == CubeType.MEGAMINX && !scramble.contains("\n")) { // only megaminx should be formatted when exporting
      String[] splitted = parseStringScrambleToArray(scramble, cubeType);
      if (getMovesPerLine(cubeType, splitted) > 0) {
        scramble = formatScramble(splitted, cubeType, null);
      }
    }
    return scramble;
  }

  /**
   * Format the scramble to add enough spaces to align the moves vertically
   * and to split it among multiple lines.
   * @param scramble the scramble containing one move in each array element
   * @param cubeType the cube type for which to format the scramble
   * @param orientation optional parameter to indicate the screen orientation to adjust the scramble
   * @return the formatted scramble
   */
  private String formatScramble(String[] scramble, CubeType cubeType, Integer orientation) {
    int maxMoveLength = 1;
    for (String m : scramble) {
      if (m.length() > maxMoveLength) {
        maxMoveLength = m.length();
      }
    }
    int movesPerLine = getMovesPerLine(cubeType, scramble, orientation);
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < scramble.length; i++) {
      s.append(scramble[i]);
      if (movesPerLine > 0) {
        for (int j = scramble[i].length(); j < maxMoveLength; j++) {
          s.append(" ");
        }
        if (i < scramble.length - 1) {
          if ((i + 1) % movesPerLine == 0 && i > 0) {
            s.append("\n");
          } else {
            s.append(" ");
          }
        }
      }
    }
    return s.toString();
  }

  public String formatScrambleAsSingleLine(String[] scramble, CubeType cubeType) {
    StringBuilder sb = new StringBuilder();
    boolean addSpace = getMovesPerLine(cubeType, scramble) > 0;
    if (scramble != null) {
      for (String s : scramble) {
        sb.append(s);
        if (addSpace) {
          sb.append(" ");
        }
      }
    }
    return sb.toString();
  }

  private int getMovesPerLine(CubeType cubeType, String[] scramble) {
    return getMovesPerLine(cubeType, scramble, null);
  }

  private int getMovesPerLine(CubeType cubeType, String[] scramble, Integer orientation) {
    int movesPerLine;
    if (orientation == null) {
      orientation = Configuration.ORIENTATION_PORTRAIT;
    }
    switch (cubeType) {
      case THREE_BY_THREE:
        movesPerLine = getThreeByThreeMovesPerLine(scramble.length);
        break;
      case PYRAMINX:
      case SKEWB:
        movesPerLine = 5;
        break;
      case FTO:
        movesPerLine = 8;
        break;
      case MEGAMINX:
        // don't add line breaks for this, as this a special kind of scramble that is already formatted
        movesPerLine = 0;
        break;
      case SQUARE1:
        movesPerLine = 4;
        break;
      case CLOCK:
        if (Options.INSTANCE.getClockNotation() == ClockNotation.URx_DRx_DLx) {
          movesPerLine = 5;
        } else {
          movesPerLine = (orientation == Configuration.ORIENTATION_PORTRAIT) ? 3 : 2;
        }
        break;
      case FOUR_BY_FOUR:
        if (Options.INSTANCE.getBigCubesNotation() == BigCubesNotation.RUF && orientation == Configuration.ORIENTATION_PORTRAIT) {
          movesPerLine = 10;
        } else {
          movesPerLine = 8;
        }
        break;
      case SIX_BY_SIX:
        if (Options.INSTANCE.getBigCubesNotation() == BigCubesNotation.RUF) {
          movesPerLine = 10;
        } else {
          movesPerLine = 8;
        }
        break;
      case TWO_BY_TWO:
        movesPerLine = getTwoByTwoMovesPerLine(scramble.length);
        break;
      default:
        movesPerLine = 10;
        break;
    }
    return movesPerLine;
  }

  private int getThreeByThreeMovesPerLine(int scrambleLength) {
    switch (scrambleLength) {
      case 14:
      case 15:
        return 5;
      case 16:
      case 17:
      case 18:
        return 6;
      case 19:
      case 20:
      case 21:
        return 7;
      case 22:
      case 23:
      case 24:
        return 8;
      default:
        return 5;
    }
  }

  private int getTwoByTwoMovesPerLine(int scrambleLength) {
    if (scrambleLength < 10) {
      return scrambleLength;
    } else {
      return (scrambleLength + 1) / 2;
    }
  }

  private char getScrambleDelimiter(CubeType cubeType) {
    char delimiter;
    if (cubeType == CubeType.SQUARE1) {
      delimiter = ')';
    } else if (cubeType == CubeType.CLOCK) {
      ClockNotation clockNotation = Options.INSTANCE.getClockNotation();
      if (clockNotation == ClockNotation.UUdU_x_x || clockNotation == ClockNotation.UUdd_ux_dx) {
        delimiter = ')';
      } else {
        delimiter = ' ';
      }
    } else {
      delimiter = ' ';
    }
    return delimiter;
  }

}
