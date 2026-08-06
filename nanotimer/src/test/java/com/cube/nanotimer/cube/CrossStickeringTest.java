package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.drill.CrossDrillSession;
import com.cube.nanotimer.smartcube.drill.DrillSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Whether the edges a cross drill leaves in colour are the edges of that cross.
 *
 * <p>Worked out from the <em>facelets</em> rather than from the tables the answer came from, which
 * is the whole point: an edge of the white cross is one with a white sticker on it, and that is a
 * fact about the drawn cube rather than about the model that produced it. A slip in the edge tables
 * or in the numbering the player is spoken to in shows up here as an edge of the wrong colour.
 *
 * <p>Every case here is a solved cube, and deliberately so. The mask names pieces and the player
 * moves each piece's stickers to wherever it sits, so what a scramble does to the picture is the
 * player's business and not this app's; what the app has to get right is which four pieces it named.
 */
public class CrossStickeringTest {

  /**
   * The player's edge pieces, in its own order (UF UR UB UL DF DR DB DL FR FL BR BL), as the two
   * facelets each occupies on a solved cube in URFDLB order.
   *
   * <p>Written out again here on purpose. Sharing a table with the code under test would only check
   * it against itself.
   */
  private static final int[][] PLAYER_EDGE_FACELETS = {
    {7, 19}, {5, 10}, {1, 46}, {3, 37}, {28, 25}, {32, 16},
    {34, 52}, {30, 43}, {23, 12}, {21, 41}, {48, 14}, {50, 39},
  };

  private static final String[] FACES = {"U", "D", "L", "R", "F", "B"};

  /** The four edges named for a cross are the four carrying that face's colour. */
  @Test
  public void theMaskLeavesExactlyThatCrossInColour() {
    for (String face : FACES) {
      CrossDrillSession session = new CrossDrillSession(DrillSpec.cross("t", face, 1, 0, null));
      session.nextRep("");
      String solved = session.getFacelets();

      List<Integer> wanted = new ArrayList<Integer>();
      for (int player = 0; player < PLAYER_EDGE_FACELETS.length; player++) {
        if (stickers(solved, player).contains(face)) {
          wanted.add(player);
        }
      }
      assertEquals(face + " has four edges", 4, wanted.size());
      assertEquals(face, wanted,
          litEdges(CubeStickering.crossAndCentres(session.getCrossEdges())));
    }
  }

  /** And nothing else is left readable: no corner, and every centre. */
  @Test
  public void theRestOfTheCubeGoesGreyExceptTheCentres() {
    CrossDrillSession session = new CrossDrillSession(DrillSpec.cross("t", "U", 1, 0, null));
    session.nextRep("");
    String mask = CubeStickering.crossAndCentres(session.getCrossEdges());
    assertTrue("a corner was left readable", lit(mask, "CORNERS").isEmpty());
    assertEquals("the centres say which face an edge belongs to", 6, lit(mask, "CENTERS").size());
  }

  @Test
  public void greyingTheCubeLeavesNothingReadable() {
    String mask = CubeStickering.allGrey();
    assertTrue(lit(mask, "EDGES").isEmpty());
    assertTrue(lit(mask, "CORNERS").isEmpty());
    assertTrue(lit(mask, "CENTERS").isEmpty());
  }

  private static List<Integer> litEdges(String mask) {
    return lit(mask, "EDGES");
  }

  /** Which pieces of one orbit the mask leaves in their own colour. */
  private static List<Integer> lit(String mask, String orbit) {
    List<Integer> shown = new ArrayList<Integer>();
    Matcher pieces = Pattern.compile("\\{\"facelets\":\\[\"(\\w+)\"").matcher(section(mask, orbit));
    for (int piece = 0; pieces.find(); piece++) {
      if ("regular".equals(pieces.group(1))) {
        shown.add(piece);
      }
    }
    return shown;
  }

  /** One orbit's slice of the mask. The orbits are written in a known order, so this can cut. */
  private static String section(String mask, String orbit) {
    String[] order = {"EDGES", "CORNERS", "CENTERS"};
    int start = mask.indexOf('"' + orbit + '"');
    assertTrue("no " + orbit + " in the mask", start >= 0);
    for (int i = 0; i < order.length - 1; i++) {
      if (order[i].equals(orbit)) {
        return mask.substring(start, mask.indexOf(",\"" + order[i + 1] + '"', start));
      }
    }
    return mask.substring(start);
  }

  private static String stickers(String facelets, int player) {
    int[] pair = PLAYER_EDGE_FACELETS[player];
    return "" + facelets.charAt(pair[0]) + facelets.charAt(pair[1]);
  }
}
