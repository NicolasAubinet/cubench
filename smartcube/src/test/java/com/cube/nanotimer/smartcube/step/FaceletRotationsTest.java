package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class FaceletRotationsTest {

  @Test
  public void holdsTheTwentyFourWaysTheCubeCanSitInSpace() {
    Set<String> distinct = new HashSet<>();
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      StringBuilder faces = new StringBuilder();
      Set<Integer> landedOn = new HashSet<>();
      for (int facelet = 0; facelet < 54; facelet++) {
        landedOn.add(FaceletRotations.apply(rotation, facelet));
      }
      assertEquals("every facelet lands somewhere of its own", 54, landedOn.size());
      for (int face = 0; face < 6; face++) {
        faces.append(FaceletRotations.face(rotation, face));
      }
      distinct.add(faces.toString());
    }
    assertEquals(24, distinct.size());
  }

  @Test
  public void leavesEveryFaceWhereItIsUnderTheIdentity() {
    for (int facelet = 0; facelet < 54; facelet++) {
      assertEquals(facelet, FaceletRotations.apply(FaceletRotations.IDENTITY, facelet));
    }
  }

  @Test
  public void carriesAFaceletWithBothItsPositionAndTheWayItFaces() {
    // Some rotation puts the up face at the front: it must carry the whole face with it, centre and
    // all, and the corner shared with the right and front faces must stay on all three.
    int rotation = rotationTaking(Cubies.U, Cubies.F);
    assertEquals(22, FaceletRotations.apply(rotation, 4)); // up centre to front centre
    assertEquals(Cubies.F, FaceletRotations.face(rotation, Cubies.U));

    Set<Integer> corner = new HashSet<>();
    for (int facelet : Cubies.CORNERS[0]) { // URF
      corner.add(FaceletRotations.apply(rotation, facelet));
    }
    assertEquals(3, corner.size());
  }

  @Test
  public void turnsAboutAFaceLeaveThatFaceAndItsOppositeAlone() {
    int[] about = FaceletRotations.about(Cubies.L);
    assertEquals(4, about.length);
    for (int rotation : about) {
      assertEquals(Cubies.L, FaceletRotations.face(rotation, Cubies.L));
      assertEquals(Cubies.R, FaceletRotations.face(rotation, Cubies.R));
    }
    assertTrue(contains(about, FaceletRotations.IDENTITY));
  }

  private static int rotationTaking(int from, int to) {
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      if (FaceletRotations.face(rotation, from) == to) {
        return rotation;
      }
    }
    throw new IllegalStateException("no rotation takes " + from + " to " + to);
  }

  private static boolean contains(int[] values, int value) {
    for (int candidate : values) {
      if (candidate == value) {
        return true;
      }
    }
    return false;
  }
}
