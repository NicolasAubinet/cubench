package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.drill.LayerRotation;
import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.step.AlgorithmForm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * How far into an algorithm the cube has got, for greying its moves as they are turned the way the
 * timer greys a scramble's.
 *
 * <p>A case is dealt with a random turn of its layer before it, which the algorithm does not carry,
 * and the solver may run it from any side. So {@link ScrambleFollower} runs once for each of the
 * four layer turns before it and each of the four sides it can be turned from. Only the ones that
 * finish the case as dealt count: the one furthest along is what is shown, and a side turn that
 * only fits another angle is a wrong angle, which the screen says. A turn of the layer fits every
 * angle, so it is never one. A rotation in the algorithm turns nothing the cube sees, so it greys
 * with the move before it.
 *
 * <p>The cube reports turns on its own faces, so the algorithm, written with the layer on U, is
 * moved onto the face the drill deals the layer on: with yellow on top, the solver's R U arrives as
 * L D.
 */
public class AlgorithmFollower {

  private static final String[] AUF = {null, "", "2", "'"};
  private static final int MIN_WRONG_ANGLE_TURNS = 2;

  private final String[] tokens;
  /** For each token, how many tokens that turn something end at or before it. */
  private final int[] turningUpTo;
  private final List<ScrambleFollower> followers = new ArrayList<>();
  private final List<Boolean> prefixed = new ArrayList<>();
  /** Per follower: whether its angle finishes the case. */
  private final List<Boolean> rightAngle = new ArrayList<>();
  /** Per follower: the quarter turns about the layer it is held at, which name its turns. */
  private final List<Integer> heldAt = new ArrayList<>();
  private final String face;
  /** The tokens as turned, without brackets, and the end of the last one that turns something. */
  private final List<String> turnTokens;
  private final int lastTurnEnd;
  /** Whether the turns left the algorithm at any point since it started. */
  private boolean strayed;
  /** Turns seen, and per follower the turn since which it has followed them unbroken. */
  private int turnsSeen;
  private final List<Integer> followingSince = new ArrayList<>();

  /** For an algorithm turned with the layer on U, from any angle. */
  public AlgorithmFollower(String algorithm) {
    this(algorithm, "U", null);
  }

  /**
   * @param layerFace the face the drill deals the last layer on, as its letter; null for U
   * @param finishes whether a path of outer turns finishes the case as dealt; null takes any angle,
   *     and so does an algorithm no angle finishes, which is greyed as written rather than refused
   * @throws IllegalArgumentException if a token is not a turn
   */
  public AlgorithmFollower(String algorithm, String layerFace, Predicate<List<String>> finishes) {
    face = layerFace == null ? "U" : layerFace;
    tokens = algorithm.trim().split("\\s+");
    String[] cleaned = new String[tokens.length];
    for (int i = 0; i < tokens.length; i++) {
      cleaned[i] = tokens[i].replace("(", "").replace(")", "");
    }
    List<List<String>> turns = AlgorithmForm.perToken(cleaned);
    turnTokens = Arrays.asList(cleaned);
    turningUpTo = new int[tokens.length];
    int turning = 0;
    for (int i = 0; i < tokens.length; i++) {
      if (!turns.get(i).isEmpty()) {
        turning++;
      }
      turningUpTo[i] = turning;
    }
    int end = 0;
    for (int i = 0; i < tokens.length; i++) {
      end = turns.get(i).isEmpty() ? end : i + 1;
    }
    lastTurnEnd = end;
    boolean anyRight = false;
    for (String auf : AUF) {
      for (int sides = 0; sides < 4; sides++) {
        List<List<String>> path = new ArrayList<>();
        if (auf != null) {
          path.add(Collections.singletonList(LayerRotation.piece("U", 0, face) + auf));
        }
        for (List<String> token : turns) {
          path.add(turnedBy(token, sides, face));
        }
        followers.add(ScrambleFollower.ofTurns(path));
        prefixed.add(auf != null);
        heldAt.add(sides);
        followingSince.add(0);
        boolean right = finishes == null || finishes.test(flattened(path));
        rightAngle.add(right);
        anyRight |= right;
      }
    }
    if (!anyRight) {
      Collections.fill(rightAngle, true);
    }
  }

  /** The algorithm's tokens, as written, for the screen to colour one by one. */
  public String[] getTokens() {
    return tokens.clone();
  }

  public void onMove(CubeMove move) {
    turnsSeen++;
    for (int i = 0; i < followers.size(); i++) {
      boolean wasWrong = followers.get(i).isWrong();
      followers.get(i).onMove(move);
      if (wasWrong && !followers.get(i).isWrong()) {
        followingSince.set(i, turnsSeen); // back on it, perhaps only by landing on its states
      }
    }
    strayed |= isOffTrack();
  }

  /**
   * Turns the cube made but never reported, found from a state it sent later. Fast turning drops
   * them; unfollowed, every turn after one reads as a slip. A single turn goes in as that turn.
   */
  public void onMissed(CubieCube missed) {
    for (Face face : Face.values()) {
      CubieCube quarter = new CubieCube();
      for (int quarters = 1; quarters <= 3; quarters++) {
        quarter.applyMove(face, false);
        if (quarter.equals(missed)) {
          for (int i = 0; i < (quarters == 2 ? 2 : 1); i++) {
            onMove(new CubeMove(face, quarters == 3, 0)); // a half turn as two quarters
          }
          return;
        }
      }
    }
    for (ScrambleFollower follower : followers) {
      follower.onMissed(missed); // more than one: placed by state alone
    }
    strayed |= isOffTrack();
  }

  public void reset() {
    for (ScrambleFollower follower : followers) {
      follower.reset();
    }
    Collections.fill(followingSince, 0);
    turnsSeen = 0;
    strayed = false;
  }

  /** Whether {@link #getUndoMoves} had anything to undo at any point, even once put right. */
  public boolean hasStrayed() {
    return strayed;
  }

  private boolean isOffTrack() {
    boolean any = false;
    for (int i = 0; i < followers.size(); i++) {
      if (rightAngle.get(i)) {
        if (!followers.get(i).isWrong()) {
          return false;
        }
        any = true;
      }
    }
    return any;
  }

  /** How many of the algorithm's tokens are done, a rotation counting with the move before it. */
  public int getDoneCount() {
    int turned = furthest(true);
    if (turned == 0) {
      return 0;
    }
    int done = 0;
    while (done < tokens.length && turningUpTo[done] <= turned) {
      done++;
    }
    return done;
  }

  /**
   * Whether the turns so far fit the algorithm from an angle that does not finish the case. One turn
   * is no sign of one: any side turn is an opening R from some angle, so it reads as a plain slip.
   */
  public boolean isWrongAngle() {
    int wrong = furthest(false);
    return wrong >= MIN_WRONG_ANGLE_TURNS && wrong > furthest(true);
  }

  /**
   * The turns that take the cube back onto the algorithm, as the timer offers for a scramble: each
   * turn off it undone, newest first. Empty while it is on the algorithm from a right angle.
   *
   * <p>The cube reports turns on its own faces, so they are named as the solver holds it: from the
   * wrong angle they are turning from, or else from the right one they were on, which before any turn
   * is the angle the case's picture shows.
   */
  public String getUndoMoves() {
    int undoing = -1;
    for (int i = 0; i < followers.size(); i++) {
      if (!rightAngle.get(i)) {
        continue;
      }
      if (!followers.get(i).isWrong()) {
        return "";
      }
      if (undoing < 0 || closer(i, undoing)) {
        undoing = i;
      }
    }
    if (undoing < 0) {
      return "";
    }
    int grip = isWrongAngle() ? furthestIndex(false) : undoing;
    int held = heldAt.get(grip);
    // Named from where the solver's hands are: the algorithm's rotations, wides and slices so far
    List<String> turned = turnTokens.subList(0, tokensDone(Math.max(0, done(grip))));
    StringBuilder named = new StringBuilder();
    for (String turn : followers.get(undoing).getReverseMoves().split(" ")) {
      if (named.length() > 0) {
        named.append(' ');
      }
      named.append(asHeld(turn.charAt(0), held, turned)).append(turn.substring(1));
    }
    return named.toString();
  }

  /**
   * How many tokens the first {@code turned} turning tokens take, with the rotations after them:
   * a regrip is made before the next turn, and one closing the algorithm is never made at all.
   */
  private int tokensDone(int turned) {
    int done = 0;
    while (done < tokens.length && turningUpTo[done] <= turned) {
      done++;
    }
    return done == tokens.length ? lastTurnEnd : done;
  }

  /**
   * The letter a solver calls one of the cube's faces, holding it {@code held} quarter turns round
   * once the algorithm's {@code turned} tokens have moved their hands.
   */
  private char asHeld(char cubeFace, int held, List<String> turned) {
    String[] with = turned.toArray(new String[turned.size() + 1]);
    for (char letter : "URFDLB".toCharArray()) {
      with[with.length - 1] = String.valueOf(letter);
      List<List<String>> per = AlgorithmForm.perToken(with);
      String first = per.get(per.size() - 1).get(0).substring(0, 1);
      if (LayerRotation.piece(first, held, face).charAt(0) == cubeFace) {
        return letter;
      }
    }
    return cubeFace;
  }

  /**
   * Fewer turns to undo, then followed unbroken for longer, then further along, then with no layer
   * turn before it. Two grips can share a state part way; the one the turns kept to is the grip.
   */
  private boolean closer(int a, int b) {
    int undoA = followers.get(a).getReverseMoves().split(" ").length;
    int undoB = followers.get(b).getReverseMoves().split(" ").length;
    if (undoA != undoB) {
      return undoA < undoB;
    }
    if (!followingSince.get(a).equals(followingSince.get(b))) {
      return followingSince.get(a) < followingSince.get(b);
    }
    int doneA = done(a);
    int doneB = done(b);
    if (doneA != doneB) {
      return doneA > doneB;
    }
    return !prefixed.get(a) && prefixed.get(b);
  }

  private int done(int follower) {
    return followers.get(follower).getDoneCount() - (prefixed.get(follower) ? 1 : 0);
  }

  /** The follower furthest along an angle that does, or does not, finish the case. */
  private int furthestIndex(boolean right) {
    int best = -1;
    for (int i = 0; i < followers.size(); i++) {
      if (rightAngle.get(i) == right && (best < 0 || done(i) > done(best))) {
        best = i;
      }
    }
    return best;
  }

  /** The most algorithm turns done along an angle that does, or does not, finish the case. */
  private int furthest(boolean right) {
    int turned = 0;
    for (int i = 0; i < followers.size(); i++) {
      if (rightAngle.get(i) == right) {
        int done = followers.get(i).getDoneCount() - (prefixed.get(i) ? 1 : 0);
        turned = Math.max(turned, done);
      }
    }
    return turned;
  }

  private static List<String> flattened(List<List<String>> path) {
    List<String> flat = new ArrayList<>();
    for (List<String> token : path) {
      flat.addAll(token);
    }
    return flat;
  }

  /** Each outer turn after {@code sides} quarter turns of the cube about U, then onto the layer. */
  private static List<String> turnedBy(List<String> token, int sides, String face) {
    List<String> turned = new ArrayList<>(token.size());
    for (String turn : token) {
      turned.add(LayerRotation.piece(turn.substring(0, 1), sides, face) + turn.substring(1));
    }
    return turned;
  }
}
