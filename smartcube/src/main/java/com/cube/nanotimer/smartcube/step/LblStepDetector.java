package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Detects the layer-by-layer milestones a beginner solves in: the cross, the first layer's corners,
 * the second layer's edges, and the last layer. Like the other detectors these are net-state
 * predicates, so any order of building reaches them, and the cross face is auto-detected — all six
 * are tracked and the first layer confirms which one the solve was built on.
 *
 * <p>What sets this method apart is that its steps are not a chain. Two shapes of that, both
 * ordinary:
 *
 * <p><b>The solver goes back to a layer.</b> Keyhole leaves one first-layer corner out so the empty
 * slot can carry the second layer's edges through, and puts the corner in at the end. So the steps
 * are laid out the way a blind solve's are: each piece is dated where it went home, and a
 * <em>stretch</em> of pieces of the same layer is one step. A keyholed solve therefore reads as
 * cross, three corners, three edges, then a corner and an edge again — which is what the solver did,
 * rather than one first layer awkwardly finishing after the second.
 *
 * <p><b>The last layer is done in whatever order was taught.</b> Its four parts (orienting the
 * edges, orienting the corners, permuting the corners, permuting the edges) are sub-goals of one
 * step, each dated when it is first reached, and the display sorts them by time. Every beginner
 * variant is then just a different order of the same four.
 *
 * <p>The two permutation parts are read <b>up to a turn of the last layer</b>, since the solver
 * leaves it wherever the algorithm ended. But not on their own terms: "the edges are placed after
 * some turn" is also true of an H perm, and the corners' version is true of a diagonal swap, so a
 * turn one kind of piece claims counts only where the other kind has no claim of its own or makes
 * the same one. Placed where they stand, needing no turn at all, is never in doubt.
 */
public final class LblStepDetector implements StepDetector {

  private static final String CROSS = "cross";
  private static final String FIRST_LAYER = "layer1";
  private static final String SECOND_LAYER = "layer2";
  private static final String LAST_LAYER = "ll";

  /** The last layer's four parts. Sub-goals of one step, reached in any order. */
  private static final String[] LAST_LAYER_PARTS = {"lleo", "llco", "llcp", "llep"};
  private static final int EO = 0, CO = 1, CP = 2, EP = 3;
  private static final int PART_COUNT = LAST_LAYER_PARTS.length;

  /** How many first-layer corners must be in before the second layer is started for the solve to be
   * one built layer by layer. Three rather than four: keyhole holds the last corner back. */
  private static final int CORNERS_BEFORE_THE_SECOND_LAYER = 3;

  private static final int PIECES_PER_LAYER = 4;

  /** The first layer's corners and the second layer's edges, per cross face, with their codes. */
  private static final int[][][] FIRST_LAYER_CORNERS = new int[6][][];
  private static final int[][][] SECOND_LAYER_EDGES = new int[6][][];
  private static final String[][] CORNER_CODES = new String[6][PIECES_PER_LAYER];
  private static final String[][] EDGE_CODES = new String[6][PIECES_PER_LAYER];

  /** The last layer's slots, in the order a turn of it carries them through. */
  private static final int[][][] LAST_LAYER_CORNERS = new int[6][][];
  private static final int[][][] LAST_LAYER_EDGES = new int[6][][];

  static {
    for (int face = 0; face < 6; face++) {
      FIRST_LAYER_CORNERS[face] = touching(Cubies.CORNERS, face);
      SECOND_LAYER_EDGES[face] = middleEdges(face);
      for (int piece = 0; piece < PIECES_PER_LAYER; piece++) {
        CORNER_CODES[face][piece] = code("corner_", FIRST_LAYER_CORNERS[face][piece], face);
        EDGE_CODES[face][piece] = code("edge_", SECOND_LAYER_EDGES[face][piece], face);
      }
      int toLastLayer = bringingUpTo(Cubies.opposite(face));
      LAST_LAYER_CORNERS[face] = rotate(toLastLayer, Arrays.copyOf(Cubies.CORNERS, 4));
      LAST_LAYER_EDGES[face] = rotate(toLastLayer, Arrays.copyOf(Cubies.EDGES, 4));
    }
  }

  private final Long[] crossMs = new Long[6];
  private final Long[][] cornerMs = new Long[6][PIECES_PER_LAYER];
  private final Long[][] edgeMs = new Long[6][PIECES_PER_LAYER];
  private final Long[][] partMs = new Long[6][PART_COUNT];

  /** When the first two layers were finished, which is when the last layer's parts start counting.
   * Kept once reached: the beginner's way of orienting the corners takes the layers apart and puts
   * them back, and the last layer is being solved throughout. */
  private final Long[] layersMs = new Long[6];

  private final Long[] firstLayerMs = new Long[6];

  private List<Long> reported = new ArrayList<Long>();

  private Integer crossFace; // provisional until the first layer confirms it
  private boolean confirmed;
  private Long solvedMs;
  private long lastTimestampMs;
  private long solveStartMs;

  @Override
  public void reset(CubeState startState, long startTimestampMs) {
    for (int face = 0; face < 6; face++) {
      crossMs[face] = null;
      firstLayerMs[face] = null;
      layersMs[face] = null;
      Arrays.fill(cornerMs[face], null);
      Arrays.fill(edgeMs[face], null);
      Arrays.fill(partMs[face], null);
    }
    reported = new ArrayList<Long>();
    crossFace = null;
    confirmed = false;
    solvedMs = null;
    lastTimestampMs = startTimestampMs;
    solveStartMs = startTimestampMs;
    evaluate(startState.getFacelets(), startTimestampMs);
    reported = stepTimes();
  }

  @Override
  public List<StepBoundaryEvent> onState(CubeState state, CubeMove lastMove) {
    if (lastMove != null) {
      lastTimestampMs = lastMove.getCubeTimestampMs();
    }
    evaluate(state.getFacelets(), lastTimestampMs);

    List<Long> times = stepTimes();
    List<StepBoundaryEvent> events = new ArrayList<>();
    for (int step = 0; step < times.size(); step++) {
      Long time = times.get(step);
      Long before = step < reported.size() ? reported.get(step) : null;
      if (time != null && !time.equals(before)) {
        events.add(new StepBoundaryEvent(step, time));
      }
    }
    reported = times;
    return events;
  }

  private void evaluate(String facelets, long timestampMs) {
    if (Cubies.SOLVED.equals(facelets) && solvedMs == null) {
      solvedMs = timestampMs;
    }
    for (int face = 0; face < 6; face++) {
      if (crossMs[face] == null && Cubies.crossDone(facelets, face)) {
        crossMs[face] = timestampMs;
      }
      if (crossMs[face] == null) {
        continue; // a piece put in before the cross was is dated with the cross, not before it
      }
      int corners = mark(cornerMs[face], FIRST_LAYER_CORNERS[face], facelets, timestampMs);
      int edges = mark(edgeMs[face], SECOND_LAYER_EDGES[face], facelets, timestampMs);
      if (corners == PIECES_PER_LAYER && firstLayerMs[face] == null) {
        firstLayerMs[face] = timestampMs;
      }
      if (corners == PIECES_PER_LAYER && edges == PIECES_PER_LAYER && layersMs[face] == null) {
        layersMs[face] = timestampMs;
      }
      if (layersMs[face] != null) {
        markLastLayer(face, facelets, timestampMs);
      }
    }
    updateCrossFace();
  }

  /** Dates the pieces newly home, and says how many of them are; each is dated once and for all,
   * since the pieces that follow disturb it in passing. */
  private static int mark(Long[] times, int[][] pieces, String facelets, long timestampMs) {
    int home = 0;
    for (int piece = 0; piece < pieces.length; piece++) {
      if (!Cubies.inPlace(facelets, pieces[piece])) {
        continue;
      }
      home++;
      if (times[piece] == null) {
        times[piece] = timestampMs;
      }
    }
    return home;
  }

  private void markLastLayer(int face, String facelets, long timestampMs) {
    int corners = Cubies.placingTurns(facelets, LAST_LAYER_CORNERS[face]);
    int edges = Cubies.placingTurns(facelets, LAST_LAYER_EDGES[face]);
    markPart(face, EO, Cubies.lastLayerOriented(facelets, face, Cubies.EDGE_POSITIONS), timestampMs);
    markPart(face, CO, Cubies.lastLayerOriented(facelets, face, Cubies.CORNER_POSITIONS),
        timestampMs);
    markPart(face, CP, placed(corners, edges), timestampMs);
    markPart(face, EP, placed(edges, corners), timestampMs);
  }

  /**
   * Whether these pieces are permuted, given which turns of the last layer would place them and
   * which would place the other kind. Placed where they stand needs no argument. Placed a turn away
   * is a claim on that turn, and it holds only where the other pieces have no claim of their own or
   * make the same one: an H perm's edges are a turn from home and so are a diagonal swap's corners,
   * and neither is anything but unsolved.
   */
  private static boolean placed(int turns, int otherTurns) {
    if ((turns & 1) != 0) {
      return true;
    }
    return turns != 0 && (otherTurns == 0 || (turns & otherTurns) != 0);
  }

  private void markPart(int face, int part, boolean done, long timestampMs) {
    if (done && partMs[face][part] == null) {
      partMs[face][part] = timestampMs;
    }
  }

  /**
   * The first layer confirms the cross face, the way F2L confirms CFOP's. Every face reaches it on a
   * solved cube, so when several land in the same state the real one is the one whose cross was
   * built first.
   */
  private void updateCrossFace() {
    if (confirmed) {
      return;
    }
    int best = -1;
    for (int face = 0; face < 6; face++) {
      if (firstLayerMs[face] != null && (best == -1 || crossMs[face] < crossMs[best])) {
        best = face;
      }
    }
    if (best != -1) {
      crossFace = best;
      confirmed = true;
      return;
    }
    if (crossFace != null && crossMs[crossFace] != null) {
      return;
    }
    crossFace = null;
    for (int face = 0; face < 6; face++) {
      if (crossMs[face] != null && (crossFace == null || crossMs[face] < crossMs[crossFace])) {
        crossFace = face;
      }
    }
  }

  /**
   * The solve as steps: the cross, then a step per stretch of pieces of the same layer, then the
   * last layer. A solve still short of its first two layers ends on the stretch it was in, left
   * undated so the turning past the last piece reads as the tail it is.
   */
  private List<Section> layout() {
    List<Section> sections = new ArrayList<>();
    if (crossFace == null || crossMs[crossFace] == null) {
      sections.add(new Section(CROSS, null));
      return sections;
    }
    sections.add(new Section(CROSS, crossMs[crossFace]));

    Section open = null;
    for (Piece piece : pieces()) {
      if (open == null || open.firstLayer != piece.firstLayer) {
        open = new Section(piece.firstLayer ? FIRST_LAYER : SECOND_LAYER, null, piece.firstLayer);
        sections.add(open);
      }
      open.parts.add(piece);
      open.completeMs = piece.timestampMs;
    }
    if (layersMs[crossFace] == null) {
      // Still in the layers: the step it stopped in is the one it was working on.
      sections.add(new Section(open == null ? FIRST_LAYER : open.name, null,
          open == null || open.firstLayer));
      return sections;
    }
    Section lastLayer = new Section(LAST_LAYER, solvedMs);
    for (int part = 0; part < PART_COUNT; part++) {
      lastLayer.parts.add(new Piece(LAST_LAYER_PARTS[part], partMs[crossFace][part], false));
    }
    sections.add(lastLayer);
    return sections;
  }

  /**
   * The pieces of the first two layers that went home, oldest first. Pieces landing on the same move
   * are ordered so that any of the stretch already open comes first: one move is one step, and a
   * corner that goes in with an edge must not open a stretch of its own for that single move.
   */
  private List<Piece> pieces() {
    List<Piece> pieces = new ArrayList<>();
    for (int piece = 0; piece < PIECES_PER_LAYER; piece++) {
      if (cornerMs[crossFace][piece] != null) {
        pieces.add(new Piece(CORNER_CODES[crossFace][piece], cornerMs[crossFace][piece], true));
      }
      if (edgeMs[crossFace][piece] != null) {
        pieces.add(new Piece(EDGE_CODES[crossFace][piece], edgeMs[crossFace][piece], false));
      }
    }
    Collections.sort(pieces, (left, right) -> left.timestampMs.compareTo(right.timestampMs));

    List<Piece> ordered = new ArrayList<>();
    boolean firstLayer = true;
    for (int at = 0; at < pieces.size(); ) {
      int end = at;
      while (end < pieces.size() && pieces.get(end).timestampMs.equals(pieces.get(at).timestampMs)) {
        end++;
      }
      for (int pass = 0; pass < 2; pass++) {
        for (int piece = at; piece < end; piece++) {
          if (pieces.get(piece).firstLayer == (firstLayer ^ (pass == 1))) {
            ordered.add(pieces.get(piece));
          }
        }
      }
      firstLayer = ordered.get(ordered.size() - 1).firstLayer;
      at = end;
    }
    return ordered;
  }

  private List<Long> stepTimes() {
    List<Long> times = new ArrayList<>();
    for (Section section : layout()) {
      times.add(section.completeMs);
    }
    return times;
  }

  /** The face the cross was built on, or null before any cross completes. */
  public Face getCrossFace() {
    return crossFace == null ? null : Cubies.faceAt(crossFace);
  }

  @Override
  public int stepCount() {
    return layout().size();
  }

  @Override
  public String stepName(int index) {
    return layout().get(index).name;
  }

  @Override
  public Long getStepTimestampMs(int index) {
    return layout().get(index).completeMs;
  }

  /**
   * A last-layer turn opening the last layer is an AUF: the solver is squaring the case up to read
   * it. During the layers the same turn is usually the piece going in, so there it only counts as
   * looking when the solver stopped again after it. The cross is left out, as it is entered from
   * inspection rather than from reading a case.
   */
  @Override
  public boolean isAlignmentMove(int step, CubeMove move, boolean pausedAfter) {
    if (crossFace == null) {
      return false;
    }
    String name = layout().get(step).name;
    if (CROSS.equals(name) || (!LAST_LAYER.equals(name) && !pausedAfter)) {
      return false;
    }
    return move.getFace() == Cubies.faceAt(Cubies.opposite(crossFace));
  }

  @Override
  public int subStepCount(int step) {
    return layout().get(step).parts.size();
  }

  @Override
  public String subStepName(int step, int subStep) {
    return layout().get(step).parts.get(subStep).code;
  }

  @Override
  public Long getSubStepTimestampMs(int step, int subStep) {
    return layout().get(step).parts.get(subStep).timestampMs;
  }

  @Override
  public boolean isComplete() {
    return solvedMs != null;
  }

  /**
   * A layer-by-layer solve builds the first layer and only then the second, which is what tells it
   * from a method that pairs a corner with its edge and puts both in at once. So the second layer
   * was started with the first all but finished: {@value #CORNERS_BEFORE_THE_SECOND_LAYER} of its
   * corners in, keyhole's held-back one being the fourth.
   *
   * <p>A solve that stopped before the second layer was started is judged on the same count, since a
   * prefix that has not reached the two layers' boundary cannot show it. A cross on its own proves
   * nothing, as every method builds one eventually.
   *
   * <p>Nothing is asked of the last layer's four parts: the order they are done in is the one thing
   * beginners differ on, and this method is where they are all at home. And nothing is asked of a
   * solve whose scramble handed it the first two layers, a last-layer drill having nothing to build.
   */
  @Override
  public boolean matchesMethod() {
    if (crossFace == null || crossMs[crossFace] == null) {
      return false;
    }
    if (layersMs[crossFace] != null && layersMs[crossFace] == solveStartMs) {
      return true;
    }
    Long firstEdge = earliest(edgeMs[crossFace]);
    long deadline = firstEdge != null ? firstEdge : Long.MAX_VALUE;
    int corners = 0;
    for (Long corner : cornerMs[crossFace]) {
      if (corner != null && corner < deadline && corner > crossMs[crossFace]) {
        corners++;
      }
    }
    return corners >= CORNERS_BEFORE_THE_SECOND_LAYER;
  }

  private static Long earliest(Long[] times) {
    Long first = null;
    for (Long time : times) {
      if (time != null && (first == null || time < first)) {
        first = time;
      }
    }
    return first;
  }

  /** One piece of the first two layers, or one part of the last layer. */
  private static final class Piece {
    final String code;
    final Long timestampMs;
    final boolean firstLayer;

    Piece(String code, Long timestampMs, boolean firstLayer) {
      this.code = code;
      this.timestampMs = timestampMs;
      this.firstLayer = firstLayer;
    }
  }

  /** One step of the layout: a stretch of pieces of the same layer, or one of the fixed steps. */
  private static final class Section {
    final String name;
    final boolean firstLayer;
    final List<Piece> parts = new ArrayList<>();
    Long completeMs;

    Section(String name, Long completeMs) {
      this(name, completeMs, false);
    }

    Section(String name, Long completeMs, boolean firstLayer) {
      this.name = name;
      this.completeMs = completeMs;
      this.firstLayer = firstLayer;
    }
  }

  private static int[][] touching(int[][] pieces, int face) {
    List<int[]> found = new ArrayList<>();
    for (int[] piece : pieces) {
      if (Cubies.touches(piece, face)) {
        found.add(piece);
      }
    }
    return found.toArray(new int[0][]);
  }

  /** The four edges of the slice between the cross face and the last layer. */
  private static int[][] middleEdges(int face) {
    List<int[]> found = new ArrayList<>();
    for (int[] edge : Cubies.EDGES) {
      if (!Cubies.touches(edge, face) && !Cubies.touches(edge, Cubies.opposite(face))) {
        found.add(edge);
      }
    }
    return found.toArray(new int[0][]);
  }

  /** A piece's code, carrying the faces it sits between ("corner_rf") so it can be told apart. */
  private static String code(String prefix, int[] piece, int face) {
    StringBuilder sides = new StringBuilder(prefix);
    for (int facelet : piece) {
      char colour = Cubies.SOLVED.charAt(facelet);
      if (colour != Cubies.FACES.charAt(face)) {
        sides.append(colour);
      }
    }
    return sides.toString().toLowerCase(Locale.US);
  }

  /** A rotation that brings the up face onto the given one, so the U layer's tables can be read
   * against any last layer. */
  private static int bringingUpTo(int face) {
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      if (FaceletRotations.face(rotation, Cubies.U) == face) {
        return rotation;
      }
    }
    throw new IllegalStateException("No rotation brings U to " + face);
  }

  private static int[][] rotate(int rotation, int[][] pieces) {
    int[][] rotated = new int[pieces.length][];
    for (int piece = 0; piece < pieces.length; piece++) {
      rotated[piece] = new int[pieces[piece].length];
      for (int facelet = 0; facelet < pieces[piece].length; facelet++) {
        rotated[piece][facelet] = FaceletRotations.apply(rotation, pieces[piece][facelet]);
      }
    }
    return rotated;
  }
}
