package com.cube.nanotimer.scrambler;

import com.cube.nanotimer.scrambler.randomstate.RSScrambler;
import com.cube.nanotimer.scrambler.randomstate.RSThreeScrambler;
import com.cube.nanotimer.scrambler.randomstate.RSTwoScrambler;
import com.cube.nanotimer.scrambler.randomstate.ScrambleConfig;
import com.cube.nanotimer.scrambler.randomstate.fto.RSFTOScrambler;
import com.cube.nanotimer.scrambler.randomstate.pyraminx.RSPyraminxScrambler;
import com.cube.nanotimer.scrambler.randomstate.square1.RSSquare1Scrambler;
import com.cube.nanotimer.vo.CubeType;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * Writes the pools of random-state scrambles that ship in {@code res/raw}, so that a puzzle opened
 * before its solver has ever run still has something to show. See {@link SeedScrambles}.
 *
 * <p>Ignored because it writes into {@code src/main/res}: left to run with the rest of the suite it
 * would rewrite the shipped pools on every build, and what a new install opens on would change with
 * each one. Drop the {@code @Ignore}, run the puzzle you mean to redo, and put it back.
 *
 * <pre>gradlew :nanotimer:testDebugUnitTest --tests "*SeedScramblesGenerator.threeByThree"</pre>
 *
 * <p>The whole set takes under half a minute on a desktop, the tables included. Regenerate when a
 * solver changes, not to freshen the list.
 */
@Ignore("Build-time generator, run by hand. See the class comment.")
@RunWith(JUnit4.class)
public class SeedScramblesGenerator {

  private static final int POOL_SIZE = 50;

  @Test
  public void twoByTwo() throws IOException {
    generate(CubeType.TWO_BY_TWO, new RSTwoScrambler());
  }

  @Test
  public void threeByThree() throws IOException {
    generate(CubeType.THREE_BY_THREE, new RSThreeScrambler());
  }

  @Test
  public void pyraminx() throws IOException {
    generate(CubeType.PYRAMINX, new RSPyraminxScrambler());
  }

  @Test
  public void square1() throws IOException {
    generate(CubeType.SQUARE1, new RSSquare1Scrambler());
  }

  @Test
  public void fto() throws IOException {
    generate(CubeType.FTO, new RSFTOScrambler());
  }

  private void generate(CubeType cubeType, RSScrambler scrambler) throws IOException {
    File out = new File(rawDir(), SeedScrambles.getFileName(cubeType));
    ScrambleConfig config = new ScrambleConfig(ScramblerService.getRSScrambleLength(cubeType));

    long start = System.currentTimeMillis();
    scrambler.genTables();
    System.out.println(cubeType + ": tables in " + (System.currentTimeMillis() - start) + "ms");

    Writer writer = new OutputStreamWriter(new FileOutputStream(out), "UTF-8");
    try {
      for (int i = 0; i < POOL_SIZE; i++) {
        // The same shape the scramble cache file is written in, so one reader serves both.
        StringBuilder line = new StringBuilder();
        for (String move : scrambler.getNewScramble(config)) {
          line.append(move).append(" ");
        }
        writer.write(line.toString());
        writer.write("\n");
      }
    } finally {
      writer.close();
    }
    System.out.println(cubeType + ": " + POOL_SIZE + " scrambles into " + out.getAbsolutePath()
        + " in " + (System.currentTimeMillis() - start) + "ms");
  }

  private File rawDir() {
    File dir = new File("src/main/res/raw");
    Assert.assertTrue("Run from the nanotimer module: " + dir.getAbsolutePath(), dir.isDirectory());
    return dir;
  }

}
