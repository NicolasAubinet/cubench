package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A blindfolded solve done the way Old Pochmann and M2 do it: one algorithm per target, each
 * trading the buffer's piece for the piece sitting at a fixed helper position.
 *
 * <p>Generated rather than recorded. Nobody here solves this way, so the fixtures are built from
 * random scrambles and checked by the one thing that cannot be faked: the cube comes out. A
 * synthetic solve that did not solve its own scramble fails before it ever reaches a detector.
 *
 * <p><b>The M2 algorithms are the published ones</b>, taken from the speedsolving wiki's M2/R2
 * table rather than worked out here, and keyed by the target the cube says they shoot at rather
 * than by the letter they are published under. The two differ for the four middle-layer stickers:
 * the algorithm published for DB shoots to UF and the one published for UF shoots to DB, which is
 * the misalignment M2 is known for and not a mistake in the table.
 *
 * <p><b>The solver's frame and the cube's are not the same one.</b> An M2 turns the middle layer,
 * which is where the cube's own centres live, so the cube reports the two outer layers turning and
 * is left sitting a half turn from the hands holding it, while a rotation inside an algorithm it
 * does not report at all. Everything here is decided in the solver's frame, and the reported stream
 * is kept beside it and checked against it at every move: the two must stay one cube seen from two
 * angles, or the fixture is not a fixture.
 */
final class SyntheticTwoCycleSolve {

  /** Old Pochmann edges: buffer UR, shot to UL, one T perm each. */
  static final String T_PERM = "R U R' U' R' F R2 U' R' U' R U R' F'";
  /** Old Pochmann corners: buffer ULB, shot to DFR, one Y perm each. */
  static final String Y_PERM = "R U' R' U' R U R' F' R U R' U' R' F R";

  /** The U sticker of UR, the U sticker of ULB, the D sticker of DF: the buffers shot from. */
  static final int UR_BUFFER = 5, ULB_BUFFER = 0, DF_BUFFER = 28;

  /**
   * The M2 method's edge algorithms, as published, each under the letter it is memorised as. Which
   * sticker one actually shoots at is read off the cube below, never off these names.
   */
  private static final String[][] M2_ALGS = {
    {"UB", "M2"},
    {"FR", "U R U' M2 U R' U'"},
    {"DR", "U R2 U' M2 U R2 U'"},
    {"BR", "U R' U' M2 U R U'"},
    {"UR", "R' U R U' M2 U R' U' R"},
    {"FL", "U' L' U M2 U' L U"},
    {"DL", "U' L2 U M2 U' L2 U"},
    {"BL", "U' L U M2 U' L' U"},
    {"UL", "L U' L' U M2 U' L U L'"},
    {"RU", "x' U' R U M2 U' R' U x"},
    {"RF", "x' U' R2 U M2 U' R2 U x"},
    {"RD", "x' U' R' U M2 U' R U x"},
    {"RB", "x' R' U' R U M2 U' R' U R x"},
    {"LU", "x' U L' U' M2 U L U' x"},
    {"LF", "x' U L2 U' M2 U L2 U' x"},
    {"LD", "x' U L U' M2 U L' U' x"},
    {"LB", "x' L U L' U' M2 U L U' L' x"},
    {"DB", "M U2 M U2"},
    {"UF", "U2 M' U2 M'"},
    {"FU", "D M' U R2 U' M U R2 U' D' M2"},
    {"BU", "U B' R U' B M2 B' U R' B U'"},
    {"BD", "M2 D U R2 U' M' U R2 U' M D'"},
  };

  /** One algorithm of the solve: where it was shot, and the moves the cube reported for it. */
  static final class Shot {
    final int targetFacelet; // in the solver's frame, which is the frame a detector spells in
    final String name; // what a detector should call it: the cycle the algorithm shot
    final List<String> reported;

    Shot(int targetFacelet, String name, List<String> reported) {
      this.targetFacelet = targetFacelet;
      this.name = name;
      this.reported = reported;
    }
  }

  private static final int FACELETS = 54;
  private static final String SAID_ORDER = "UDFBRL";
  private static final char[] FACE_LETTERS = {'U', 'R', 'F', 'D', 'L', 'B'};

  /** Where each turn sends every sticker, derived from the cube rather than transcribed. */
  private static final Map<String, int[]> MOVES = new HashMap<String, int[]>();
  /** The face turns the cube sees for each turn: a slice shows as two, a rotation as none. */
  private static final Map<String, String[]> SEEN = new HashMap<String, String[]>();
  /** Which published algorithm shoots the buffer's sticker to which target sticker. */
  private static final Map<Integer, String> SHOTS = new HashMap<Integer, String>();
  /**
   * The pair every M2 algorithm carries along whatever it was shot at, taken off the cube rather
   * than written down: the two middle edges an M2 exchanges that the buffer's own is not one of.
   * Every other sticker maps to itself.
   */
  private static final int[] CARRIED = identity();

  static {
    for (char face : FACE_LETTERS) {
      int[] quarter = faceTurn(face);
      put(String.valueOf(face), quarter, String.valueOf(face));
      put(face + "2", compose(quarter, quarter), face + "2");
      put(face + "'", compose(quarter, compose(quarter, quarter)), face + "'");
    }
    int[] x = rotationCarrying(Cubies.F, Cubies.U);
    put("x", x);
    put("x'", compose(x, compose(x, x)));
    put("x2", compose(x, x));
    slice();
    for (String[] alg : M2_ALGS) {
      SHOTS.put(imageOf(alg[1])[DF_BUFFER], alg[1]);
    }
    int[] halfSlice = imageOf("M2");
    int shotAt = Cubies.slotOf(halfSlice[DF_BUFFER]);
    for (int facelet = 0; facelet < FACELETS; facelet++) {
      int slot = Cubies.slotOf(facelet);
      if (slot >= 0 && slot != Cubies.slotOf(DF_BUFFER) && slot != shotAt) {
        CARRIED[facelet] = halfSlice[facelet];
      }
    }
  }

  private String state = Cubies.SOLVED; // in the solver's frame
  private String seen = Cubies.SOLVED; // as the cube reports it, its own centres never moving
  private final List<Shot> shots = new ArrayList<Shot>();
  private final List<String> reported = new ArrayList<String>();

  /** Scramble in the solver's frame, before there is a blindfold or a drift. */
  void scramble(String scramble) {
    for (String token : scramble.trim().split("\\s+")) {
      state = apply(state, MOVES.get(token));
    }
    seen = state;
  }

  List<Shot> shots() {
    return shots;
  }

  /** Every face turn the cube reported for the solve, drift and all. */
  List<String> reportedSolve() {
    return reported;
  }

  boolean isSolved() {
    return state.equals(Cubies.SOLVED);
  }

  /** What the synthetic solve was left owing, for a fixture that did not come out. */
  String leftOver() {
    StringBuilder left = new StringBuilder();
    for (int slot = 0; slot < Cubies.PIECES.length; slot++) {
      if (!Cubies.inPlace(state, Cubies.PIECES[slot])) {
        left.append(spell(slot)).append(' ');
      }
    }
    return left.toString();
  }

  /**
   * Shoot every edge home the M2 way: the whole memo first, then one algorithm per item.
   *
   * <p><b>The memo cannot be re-read between algorithms</b>, the way Old Pochmann's can. The DF
   * buffer sits in the middle layer, so every M2 carries the other two middle edges along with it —
   * a cube read halfway through says the buffer holds a piece it does not hold yet. The memo is
   * therefore taken from the cube before a single turn is made, as a solver's is, and the cube is
   * not consulted again.
   *
   * <p><b>Which of two algorithms an item takes depends on where it falls in the memo.</b> The pair
   * every M2 carries along is outstanding after an odd number of them and back where it started
   * after an even number, so on half the items the cube sits a UF/DB swap away from what the memo
   * assumed and the algorithm has to be swapped the same way for the item to land where the memo
   * put it. For every sticker but those two and their reverses that is the same algorithm twice
   * over, which is why the shift is a middle-layer affair. The odd items take the algorithm shooting
   * at the carried pair's other sticker and the even ones the algorithm shooting at their own — the
   * published table is written the odd way round, which is what crosses the four against it.
   */
  void shootAllM2() {
    List<List<String>> algorithms = new ArrayList<List<String>>();
    List<Integer> aimed = new ArrayList<Integer>();
    boolean shifted = true;
    for (int target : memo(DF_BUFFER)) {
      String alg = SHOTS.get(shifted ? CARRIED[target] : target);
      if (alg == null) {
        throw new IllegalStateException("no M2 algorithm shoots to " + spellFrom(target));
      }
      for (List<String> part : parts(Arrays.asList(alg.split(" ")))) {
        algorithms.add(part);
        aimed.add(target);
      }
      shifted = !shifted;
    }
    cancel(algorithms, aimed);
    for (int i = 0; i < algorithms.size(); i++) {
      play(DF_BUFFER, aimed.get(i), algorithms.get(i));
    }
  }

  /**
   * Two half turns of the middle slice running are no turns at all, and a solver does not make
   * them: an algorithm that ends on a bare {@code M2} and one that opens on it cancel where they
   * meet. A memo item whose whole algorithm was that {@code M2} then leaves nothing behind at all,
   * and nothing turned is nothing anything can read.
   */
  private static void cancel(List<List<String>> algorithms, List<Integer> aimed) {
    List<List<String>> kept = new ArrayList<List<String>>();
    List<Integer> keptAims = new ArrayList<Integer>();
    for (int i = 0; i < algorithms.size(); i++) {
      int last = kept.size() - 1;
      if (last >= 0 && halfSlice(algorithms.get(i)) && halfSlice(kept.get(last))) {
        kept.remove(last);
        keptAims.remove(last);
      } else {
        kept.add(algorithms.get(i));
        keptAims.add(aimed.get(i));
      }
    }
    algorithms.clear();
    algorithms.addAll(kept);
    aimed.clear();
    aimed.addAll(keptAims);
  }

  private static boolean halfSlice(List<String> algorithm) {
    return algorithm.size() == 1 && "M2".equals(algorithm.get(0));
  }

  /**
   * What a solver memorises: the buffer's sticker says where its piece belongs, that is the target,
   * and the cycle is walked on from whatever the target held. A closed cycle breaks into whatever is
   * still out. Read off a model of the cube rather than the cube, since the cube will not be in this
   * state again until the run is over.
   */
  private List<Integer> memo(int bufferFacelet) {
    int bufferSlot = Cubies.slotOf(bufferFacelet);
    List<Integer> targets = new ArrayList<Integer>();
    String model = state;
    while (!typeSolved(model, bufferSlot)) {
      int target = homeFaceletOf(model, bufferFacelet);
      if (Cubies.slotOf(target) == bufferSlot) {
        target = breakIn(model, bufferSlot);
      }
      targets.add(target);
      model = traded(model, bufferFacelet, target);
      if (targets.size() > 40) {
        throw new IllegalStateException("the memo is not closing");
      }
    }
    return targets;
  }

  /** One memo item done to the model: the buffer's piece and the target's traded, sticker for sticker. */
  private static String traded(String facelets, int bufferFacelet, int target) {
    int[] from = Cubies.PIECES[Cubies.slotOf(bufferFacelet)];
    int[] to = Cubies.PIECES[Cubies.slotOf(target)];
    char[] traded = facelets.toCharArray();
    for (int i = 0; i < from.length; i++) {
      int a = from[(indexIn(from, bufferFacelet) + i) % from.length];
      int b = to[(indexIn(to, target) + i) % to.length];
      traded[a] = facelets.charAt(b);
      traded[b] = facelets.charAt(a);
    }
    return new String(traded);
  }

  private static int indexIn(int[] piece, int facelet) {
    for (int i = 0; i < piece.length; i++) {
      if (piece[i] == facelet) {
        return i;
      }
    }
    throw new IllegalStateException("facelet " + facelet + " is not on that piece");
  }

  /**
   * Shoot every piece of one type home the Old Pochmann way, one algorithm per target: read the
   * sticker on the buffer, set the place it belongs up to the helper, run the algorithm, undo the
   * setup.
   */
  void shootAll(String alg, int bufferFacelet) {
    List<String> algMoves = Arrays.asList(alg.split(" "));
    int helper = imageOfSequence(algMoves)[bufferFacelet];
    int bufferSlot = Cubies.slotOf(bufferFacelet);
    Map<Integer, List<String>> setups = setupsTo(helper, bufferSlot);
    while (!typeSolved(state, bufferSlot)) {
      int target = homeFaceletOf(state, bufferFacelet);
      if (Cubies.slotOf(target) == bufferSlot) {
        // The buffer holds its own piece, home or merely turned where it stands: a cycle has closed
        // and there is nowhere to shoot it, so the next algorithm breaks into one still open.
        target = breakIn(state, bufferSlot);
      }
      List<String> setup = setups.get(target);
      if (setup == null) {
        throw new IllegalStateException("no setup carries " + spellFrom(target) + " to the helper");
      }
      List<String> block = new ArrayList<String>(setup);
      block.addAll(algMoves);
      block.addAll(invert(setup));
      record(bufferFacelet, target, block);
    }
  }

  /**
   * One chosen algorithm, set up and undone around it, without asking where it ought to be shot.
   * For fixtures about the shape of an algorithm rather than about a method's target order.
   */
  void shoot(String alg, String setup) {
    List<String> block = setup.trim().isEmpty() ? new ArrayList<String>()
        : new ArrayList<String>(Arrays.asList(setup.split(" ")));
    List<String> undo = invert(block);
    block.addAll(Arrays.asList(alg.split(" ")));
    block.addAll(undo);
    record(DF_BUFFER, -1, block);
  }

  /** Play one algorithm, or the algorithms it turns out to be. */
  private void record(int bufferFacelet, int target, List<String> block) {
    for (List<String> part : parts(block)) {
      play(bufferFacelet, target, part);
    }
  }

  /**
   * The algorithms a block of turns is, which is usually the single one it was meant as. Two of the
   * published M2 algorithms open or close with a bare {@code M2}, and an {@code M2} on its own is
   * the whole of the algorithm for one target: the cube comes to rest between the two halves,
   * holding four pieces in two exchanges with the buffer in one of them, and nothing reading the
   * cube afterwards can be told the two were meant as one.
   */
  private static List<List<String>> parts(List<String> block) {
    if (block.size() > 1 && "M2".equals(block.get(0))) {
      return Arrays.asList(block.subList(0, 1), block.subList(1, block.size()));
    }
    if (block.size() > 1 && "M2".equals(block.get(block.size() - 1))) {
      return Arrays.asList(block.subList(0, block.size() - 1),
          block.subList(block.size() - 1, block.size()));
    }
    return Collections.singletonList(block);
  }

  /** Play one algorithm, keeping the cube's own account of it beside the solver's. */
  private void play(int bufferFacelet, int target, List<String> block) {
    List<String> asReported = new ArrayList<String>();
    for (String move : block) {
      int[] drift = drift();
      for (String face : SEEN.get(move)) {
        String asSeen = faceLetterOf(drift[centreOf(face.charAt(0))]) + face.substring(1);
        asReported.add(asSeen);
        seen = apply(seen, MOVES.get(asSeen));
      }
      state = apply(state, MOVES.get(move));
      drift(); // throws unless the two accounts are still one cube seen from two angles
    }
    shots.add(new Shot(target, cycleName(bufferFacelet, block), asReported));
    reported.addAll(asReported);
    if (shots.size() > 60) {
      throw new IllegalStateException("the synthetic solve is not converging");
    }
  }

  /**
   * The rotation between the hands and the cube's own account of itself, found rather than tracked:
   * whichever of the twenty-four leaves the two states equal. None of them doing so means the moves
   * reported have stopped being the moves that were made, which is a broken fixture.
   */
  private int[] drift() {
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      if (rotated(state, rotation).equals(seen)) {
        return rotationImage(rotation);
      }
    }
    throw new IllegalStateException("the moves reported are no longer the moves that were made");
  }

  private static int centreOf(char face) {
    return Cubies.FACES.indexOf(face) * 9 + 4;
  }

  private static char faceLetterOf(int facelet) {
    return Cubies.FACES.charAt(facelet / 9);
  }

  /** Where the sticker sitting on this facelet belongs, which is the target it says to shoot at. */
  private static int homeFaceletOf(String facelets, int facelet) {
    int home = Cubies.homeSlotOf(facelets, Cubies.slotOf(facelet));
    for (int candidate : Cubies.PIECES[home]) {
      if (Cubies.SOLVED.charAt(candidate) == facelets.charAt(facelet)) {
        return candidate;
      }
    }
    throw new IllegalStateException("the sticker on facelet " + facelet + " belongs nowhere");
  }

  /** A cycle has closed: the next algorithm breaks into whatever of this type is still out. */
  private static int breakIn(String facelets, int bufferSlot) {
    for (int slot = 0; slot < Cubies.PIECES.length; slot++) {
      if (slot != bufferSlot && Cubies.isEdge(slot) == Cubies.isEdge(bufferSlot)
          && !Cubies.inPlace(facelets, Cubies.PIECES[slot])) {
        return Cubies.PIECES[slot][0];
      }
    }
    throw new IllegalStateException("nothing of this type is left to break into");
  }

  private static boolean typeSolved(String facelets, int bufferSlot) {
    for (int slot = 0; slot < Cubies.PIECES.length; slot++) {
      if (Cubies.isEdge(slot) == Cubies.isEdge(bufferSlot)
          && !Cubies.inPlace(facelets, Cubies.PIECES[slot])) {
        return false;
      }
    }
    return true;
  }

  /**
   * A setup for every piece that can reach the helper, searched for rather than written out: the
   * shortest turns that leave the buffer where it stands and carry the target onto the helper
   * sticker. Keyed by the target sticker, since where a piece is shot decides how it lands.
   */
  private static Map<Integer, List<String>> setupsTo(int helper, int bufferSlot) {
    List<String> faces = new ArrayList<String>();
    for (char face : FACE_LETTERS) {
      if (Cubies.inPlace(apply(Cubies.SOLVED, MOVES.get(String.valueOf(face))),
          Cubies.PIECES[bufferSlot])) {
        faces.add(String.valueOf(face));
      }
    }
    Map<Integer, List<String>> setups = new HashMap<Integer, List<String>>();
    setups.put(helper, new ArrayList<String>());
    Deque<List<String>> queue = new ArrayDeque<List<String>>();
    queue.add(new ArrayList<String>());
    for (int depth = 0; depth < 4; depth++) {
      Deque<List<String>> next = new ArrayDeque<List<String>>();
      while (!queue.isEmpty()) {
        List<String> sequence = queue.poll();
        for (String face : faces) {
          if (!sequence.isEmpty() && sequence.get(sequence.size() - 1).charAt(0) == face.charAt(0)) {
            continue;
          }
          for (String suffix : new String[] {"", "'", "2"}) {
            List<String> longer = new ArrayList<String>(sequence);
            longer.add(face + suffix);
            int[] image = imageOfSequence(longer);
            for (int facelet = 0; facelet < FACELETS; facelet++) {
              if (image[facelet] == helper && !setups.containsKey(facelet)) {
                setups.put(facelet, longer);
              }
            }
            next.add(longer);
          }
        }
      }
      queue = next;
    }
    return setups;
  }

  private static int[] imageOfSequence(List<String> moves) {
    int[] image = identity();
    for (String move : moves) {
      image = compose(image, MOVES.get(move));
    }
    return image;
  }

  private static int[] imageOf(String moves) {
    return imageOfSequence(Arrays.asList(moves.trim().split("\\s+")));
  }

  /**
   * What an algorithm shot, said the way a memo says it: the buffer, then the sticker its piece was
   * sent to, then wherever that one's went, round until the cycle closes on the buffer again.
   *
   * <p>Read off the algorithm's own effect rather than off the target it was aimed at, because the
   * two are not always the same thing. An M2 shot at one of the four middle-layer stickers lands
   * three pieces rather than trading two, since the pair it carries along is the pair it was aimed
   * at, and a memo item that reads as a three-cycle is what the cube shows.
   */
  private static String cycleName(int bufferFacelet, List<String> block) {
    int[] image = imageOfSequence(block);
    StringBuilder name = new StringBuilder(spell(Cubies.slotOf(bufferFacelet)));
    for (int facelet = image[bufferFacelet]; facelet != bufferFacelet; facelet = image[facelet]) {
      name.append('-').append(spellFrom(facelet));
    }
    return name.toString();
  }

  /** The piece said by the faces it belongs on, in the order pieces are said. */
  static String spell(int slot) {
    return letters(Cubies.PIECES[slot], 0);
  }

  /** The same piece said from one of its stickers, which is what a target carries. */
  static String spellFrom(int facelet) {
    int[] piece = Cubies.PIECES[Cubies.slotOf(facelet)];
    int start = 0;
    for (int i = 0; i < piece.length; i++) {
      if (piece[i] == facelet) {
        start = i;
      }
    }
    return letters(piece, start);
  }

  private static String letters(int[] piece, int start) {
    StringBuilder name = new StringBuilder();
    name.append(Cubies.SOLVED.charAt(piece[start]));
    for (int axis = 0; axis < SAID_ORDER.length() / 2; axis++) {
      for (int i = 0; i < piece.length; i++) {
        char letter = Cubies.SOLVED.charAt(piece[i]);
        if (i != start && SAID_ORDER.indexOf(letter) / 2 == axis) {
          name.append(letter);
        }
      }
    }
    return name.toString();
  }

  static List<String> invert(List<String> moves) {
    List<String> inverted = new ArrayList<String>(moves.size());
    for (int i = moves.size() - 1; i >= 0; i--) {
      String move = moves.get(i);
      inverted.add(move.endsWith("2") ? move
          : move.endsWith("'") ? move.substring(0, move.length() - 1) : move + "'");
    }
    return inverted;
  }

  /** A turn and the face turns the cube sees for it: itself, unless it is a slice or a rotation. */
  private static void put(String move, int[] image, String... seen) {
    MOVES.put(move, image);
    SEEN.put(move, seen);
  }

  /**
   * The middle slice: the two outer layers turning the other way, which is what the cube reports,
   * and the core carried round with them, which it cannot report at all.
   */
  private static void slice() {
    for (String pair : new String[] {"R L'", "R' L", "L R'", "L' R"}) {
      String[] both = pair.split(" ");
      int[] turned = compose(MOVES.get(both[0]), MOVES.get(both[1]));
      for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
        int[] candidate = compose(turned, rotationImage(rotation));
        if (isSlice(candidate)) {
          put("M", candidate, both);
          put("M'", compose(candidate, compose(candidate, candidate)),
              invert(Arrays.asList(both)).toArray(new String[0]));
          put("M2", compose(candidate, candidate),
              both[0].charAt(0) + "2", both[1].charAt(0) + "2");
          return;
        }
      }
    }
    throw new IllegalStateException("no middle slice");
  }

  /** A slice leaves every corner and every edge outside the middle layer where it stands. */
  private static boolean isSlice(int[] image) {
    String after = apply(Cubies.SOLVED, image);
    List<Integer> moved = new ArrayList<Integer>();
    for (int slot = 0; slot < Cubies.PIECES.length; slot++) {
      if (!Cubies.inPlace(after, Cubies.PIECES[slot])) {
        moved.add(slot);
      }
    }
    return moved.size() == 4 && moved.contains(Cubies.UF) && moved.contains(Cubies.UB)
        && moved.contains(Cubies.DF) && moved.contains(Cubies.DB);
  }

  /** The whole cube turned to carry one face's centre onto another's, the left and right held. */
  private static int[] rotationCarrying(int from, int to) {
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      int[] image = rotationImage(rotation);
      if (image[from * 9 + 4] == to * 9 + 4 && image[Cubies.R * 9 + 4] == Cubies.R * 9 + 4) {
        return image;
      }
    }
    throw new IllegalStateException("no rotation carrying " + from + " onto " + to);
  }

  private static int[] rotationImage(int rotation) {
    int[] image = new int[FACELETS];
    for (int facelet = 0; facelet < FACELETS; facelet++) {
      image[FaceletRotations.apply(rotation, facelet)] = facelet;
    }
    return image;
  }

  private static String rotated(String facelets, int rotation) {
    char[] out = new char[FACELETS];
    for (int facelet = 0; facelet < FACELETS; facelet++) {
      out[facelet] = facelets.charAt(FaceletRotations.apply(rotation, facelet));
    }
    return new String(out);
  }

  private static String apply(String facelets, int[] image) {
    char[] next = new char[FACELETS];
    for (int facelet = 0; facelet < FACELETS; facelet++) {
      next[image[facelet]] = facelets.charAt(facelet);
    }
    return new String(next);
  }

  private static int[] compose(int[] first, int[] second) {
    int[] both = new int[FACELETS];
    for (int facelet = 0; facelet < FACELETS; facelet++) {
      both[facelet] = second[first[facelet]];
    }
    return both;
  }

  private static int[] identity() {
    int[] image = new int[FACELETS];
    for (int facelet = 0; facelet < FACELETS; facelet++) {
      image[facelet] = facelet;
    }
    return image;
  }

  /** Where a quarter turn of one face sends every sticker, read off a solved cube it was applied to. */
  private static int[] faceTurn(char face) {
    CubieCube cube = new CubieCube();
    cube.applyMove(Face.valueOf(String.valueOf(face)), false);
    String after = cube.toFaceCube();
    int[] image = identity();
    for (int slot = 0; slot < Cubies.PIECES.length; slot++) {
      int from = Cubies.homeSlotOf(after, slot);
      for (int facelet : Cubies.PIECES[slot]) {
        for (int source : Cubies.PIECES[from]) {
          if (Cubies.SOLVED.charAt(source) == after.charAt(facelet)) {
            image[source] = facelet;
          }
        }
      }
    }
    return image;
  }
}
