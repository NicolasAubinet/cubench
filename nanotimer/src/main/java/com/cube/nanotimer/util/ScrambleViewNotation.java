package com.cube.nanotimer.util;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.Options.ClockNotation;
import com.cube.nanotimer.vo.CubeType;

/**
 * Maps NanoTimer scrambles to the form cubing.js expects for its 2D net diagrams
 * (used by {@code ScrambleViewDialog}). The boundary stays "string in, string out":
 * a {@link CubeType} picks the renderer key, and the move array is reformatted into
 * a single notation string.
 *
 * <p>Renderer keys are either a {@code scramble-display} event id (e.g. {@code 333},
 * {@code minx}) or the literal {@code fto} (drawn by {@code twisty-player}). See
 * {@code assets/scramble/scramble.html}.</p>
 *
 * <p>Most puzzles are a near pass-through. The exceptions, derived from the
 * scramblers' output:
 * <ul>
 *   <li><b>Square-1</b> emits tuples like {@code ( 3, 2)} with no separators;
 *       cubing.js wants the WCA slash form {@code (3, 2) / (-2, 4) / ...}.</li>
 *   <li><b>Megaminx</b> elements already carry their own spacing (and the raw
 *       scramble has embedded newlines); we just collapse whitespace to one line.</li>
 *   <li><b>Clock</b> has three user-selectable notations; only the modern WCA
 *       {@code URx_DRx_DLx} form (now the app default) is parseable by cubing.js
 *       and is passed through. The two pin-based forms ({@code UUdU_x_x},
 *       {@code UUdd_ux_dx}) are a different representation cubing.js can't parse,
 *       so {@link #toCubingNotation} returns {@code null} for them and the dialog
 *       falls back to the text scramble.</li>
 * </ul>
 */
public final class ScrambleViewNotation {

  private ScrambleViewNotation() {
  }

  /**
   * @return the cubing.js renderer key for this puzzle, or {@code null} if we don't
   *         know how to draw it (caller should hide the diagram entry point).
   */
  public static String getRenderKey(CubeType cubeType) {
    if (cubeType == null) {
      return null;
    }
    switch (cubeType) {
      case TWO_BY_TWO:    return "222";
      case THREE_BY_THREE:return "333";
      case FOUR_BY_FOUR:  return "444";
      case FIVE_BY_FIVE:  return "555";
      case SIX_BY_SIX:    return "666";
      case SEVEN_BY_SEVEN:return "777";
      case MEGAMINX:      return "minx";
      case PYRAMINX:      return "pyram";
      case SKEWB:         return "skewb";
      case SQUARE1:       return "sq1";
      case CLOCK:         return "clock";
      case FTO:           return "fto";
      default:            return null;
    }
  }

  /**
   * The cubing.js <em>puzzle</em> id, for a {@code twisty-player}'s {@code puzzle} attribute.
   *
   * <p><strong>Not the same namespace as {@link #getRenderKey}</strong>, which returns a
   * {@code scramble-display} <em>event</em> id ({@code 333}, {@code minx}). A player asked for
   * {@code 333} fails with "Invalid puzzle ID". The two only coincide for FTO, which is why
   * {@code scramble.html} draws that one through a player and the rest through a display.</p>
   *
   * @return the puzzle id, or {@code null} if cubing.js has no puzzle for this cube type.
   */
  public static String getPuzzleId(CubeType cubeType) {
    if (cubeType == null) {
      return null;
    }
    switch (cubeType) {
      case TWO_BY_TWO:    return "2x2x2";
      case THREE_BY_THREE:return "3x3x3";
      case FOUR_BY_FOUR:  return "4x4x4";
      case FIVE_BY_FIVE:  return "5x5x5";
      case SIX_BY_SIX:    return "6x6x6";
      case SEVEN_BY_SEVEN:return "7x7x7";
      case MEGAMINX:      return "megaminx";
      case PYRAMINX:      return "pyraminx";
      case SKEWB:         return "skewb";
      case SQUARE1:       return "square1";
      case CLOCK:         return "clock";
      case FTO:           return "fto";
      default:            return null;
    }
  }

  /**
   * Reformat a NanoTimer scramble (one move per array element) into the single-line
   * notation string cubing.js parses for the given puzzle.
   *
   * @return the cubing.js notation, or {@code null} if this scramble can't be drawn
   *         (a Clock pin notation) — the caller should fall back to text.
   */
  public static String toCubingNotation(String[] scramble, CubeType cubeType) {
    ClockNotation clockNotation = (cubeType == CubeType.CLOCK)
        ? Options.INSTANCE.getClockNotation() : null;
    return toCubingNotation(scramble, cubeType, clockNotation);
  }

  /**
   * Pure variant taking the Clock notation explicitly (the only Options-dependent
   * input), so the conversion is unit-testable without Android.
   */
  static String toCubingNotation(String[] scramble, CubeType cubeType, ClockNotation clockNotation) {
    if (scramble == null || scramble.length == 0) {
      return "";
    }
    if (cubeType == CubeType.SQUARE1) {
      // ( 3, 2)( -2, 4) ... -> (3,2) / (-2,4) / ...  (cubing.js parses the slash form)
      StringBuilder sb = new StringBuilder();
      for (String move : scramble) {
        if (move == null || move.trim().isEmpty()) {
          continue;
        }
        if (sb.length() > 0) {
          sb.append(" / ");
        }
        sb.append(move.replaceAll("\\s+", ""));
      }
      return sb.toString();
    }
    if (cubeType == CubeType.CLOCK && clockNotation != ClockNotation.URx_DRx_DLx) {
      // The pin-based notations (UUdU_x_x, UUdd_ux_dx) aren't cubing.js notation and
      // can't be reliably converted — signal a text fallback. URx_DRx_DLx (the app
      // default) is modern WCA notation and falls through to the pass-through below.
      return null;
    }
    // Everything else: join the moves and collapse any whitespace/newlines (Megaminx
    // elements already include separators) into a single clean line.
    return joinAndCollapse(scramble);
  }

  private static String joinAndCollapse(String[] scramble) {
    StringBuilder sb = new StringBuilder();
    for (String move : scramble) {
      if (move == null) {
        continue;
      }
      sb.append(move).append(' ');
    }
    return sb.toString().replaceAll("\\s+", " ").trim();
  }
}
