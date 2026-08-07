package com.cube.nanotimer.scrambler;

import com.cube.nanotimer.util.ScrambleFormatterService;
import com.cube.nanotimer.vo.CubeType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guards the scramble pools that ship in {@code res/raw}. They are written by hand-run generator
 * (see {@code SeedScramblesGenerator}) rather than by the build, so nothing else would notice a
 * file that came out truncated, in the wrong format, or edited by mistake.
 */
@RunWith(JUnit4.class)
public class SeedScramblesTest {

  private static final int POOL_SIZE = 50;
  private static final int MIN_MOVES = 5;

  @Test
  public void everyRandomStatePuzzleShipsAPool() {
    for (CubeType cubeType : ScramblerService.INSTANCE.getRandomStateCubeTypes()) {
      String fileName = SeedScrambles.getFileName(cubeType);
      Assert.assertNotNull("No shipped scrambles named for " + cubeType, fileName);
      Assert.assertTrue("Missing " + fileName, new File(rawDir(), fileName).isFile());
    }
  }

  @Test
  public void puzzlesWithoutASolverShipNothing() {
    List<CubeType> randomState = ScramblerService.INSTANCE.getRandomStateCubeTypes();
    for (CubeType cubeType : CubeType.values()) {
      if (!randomState.contains(cubeType)) {
        Assert.assertNull("Nothing generates a pool for " + cubeType,
            SeedScrambles.getFileName(cubeType));
      }
    }
  }

  @Test
  public void poolsAreFullAndReadBackUnchanged() throws IOException {
    for (CubeType cubeType : ScramblerService.INSTANCE.getRandomStateCubeTypes()) {
      List<String> lines = read(cubeType);
      Assert.assertEquals(cubeType + " pool size", POOL_SIZE, lines.size());

      Set<String> seen = new HashSet<>();
      for (String line : lines) {
        String[] moves = ScrambleFormatterService.INSTANCE.parseStringScrambleToArray(line, cubeType);
        Assert.assertTrue(cubeType + " scramble is too short: " + line, moves.length >= MIN_MOVES);
        for (String move : moves) {
          Assert.assertFalse(cubeType + " has a blank move in: " + line, move.trim().isEmpty());
        }
        // The pool is read with the reader the scramble cache file uses, so it has to survive it.
        Assert.assertEquals(cubeType + " does not read back", line, join(moves));
        Assert.assertTrue(cubeType + " repeats a scramble: " + line, seen.add(line));
      }
    }
  }

  /** The shape {@code ScramblerService} writes its cache file in, which the pools share. */
  private String join(String[] moves) {
    StringBuilder sb = new StringBuilder();
    for (String move : moves) {
      sb.append(move).append(" ");
    }
    return sb.toString();
  }

  private List<String> read(CubeType cubeType) throws IOException {
    File file = new File(rawDir(), SeedScrambles.getFileName(cubeType));
    return Files.readAllLines(file.toPath(), Charset.forName("UTF-8"));
  }

  private File rawDir() {
    File dir = new File("src/main/res/raw");
    Assert.assertTrue("Run from the nanotimer module: " + dir.getAbsolutePath(), dir.isDirectory());
    return dir;
  }

}
