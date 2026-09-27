package com.cube.nanotimer.scrambler;

import com.cube.nanotimer.vo.CubeType;

import org.junit.Assert;
import org.junit.Test;

public class ScramblerServiceTest {

  @Test
  public void cacheFileOfMatchesThePuzzlesFilesOnly() {
    Assert.assertTrue(ScramblerService.isCacheFileOf("randomstate_scrambles_1", CubeType.TWO_BY_TWO));
    Assert.assertTrue(ScramblerService.isCacheFileOf("randomstate_scrambles_1_PLL", CubeType.TWO_BY_TWO));
    // 2x2's id is a prefix of 10, 11, 12...
    Assert.assertFalse(ScramblerService.isCacheFileOf("randomstate_scrambles_12", CubeType.TWO_BY_TWO));
    Assert.assertFalse(ScramblerService.isCacheFileOf("randomstate_scrambles_2", CubeType.TWO_BY_TWO));
    Assert.assertFalse(ScramblerService.isCacheFileOf("seed_scrambles", CubeType.TWO_BY_TWO));
  }
}
