package com.cube.nanotimer.smartcube.drivers;

import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.smartcube.driver.CubeAdvertisement;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public class MoyuV10DriverTest {

  private final MoyuV10Driver driver = new MoyuV10Driver();

  // Advertisement captured from a real V11 (2026-07-22): same WCU_MY32 name format as the
  // V10, but the real MAC (manufacturer data, reversed) has prefix CF:30:16:02, not :00.
  private static final Map<Integer, int[]> V11_MF_DATA = Collections.singletonMap(
      0x0000, new int[]{0x00, 0x00, 0x30, 0x88, 0x52, 0x02, 0x16, 0x30, 0xcf});

  @Test
  public void manufacturerDataMacWinsOverNameDerivation() {
    CubeAdvertisement adv = new CubeAdvertisement("id", "WCU_MY32_5288", null, V11_MF_DATA);
    assertEquals("CF:30:16:02:52:88", MoyuV10Driver.deriveMac(adv));
  }

  @Test
  public void nameDerivationIsTheFallback() {
    CubeAdvertisement adv = new CubeAdvertisement("id", "WCU_MY32_ABCD", null, null);
    assertEquals("CF:30:16:00:AB:CD", MoyuV10Driver.deriveMac(adv));
  }

  @Test
  public void modelNameDistinguishesV11FromV10ByMacPrefix() {
    assertEquals("MoYu WeiLong V11 AI",
        driver.getModelName(new CubeAdvertisement("id", "WCU_MY32_5288", null, V11_MF_DATA)));
    assertEquals("MoYu WeiLong V10 AI",
        driver.getModelName(new CubeAdvertisement("id", "WCU_MY32_ABCD", null, null)));
    assertEquals("MoYu WeiLong AI",
        driver.getModelName(new CubeAdvertisement("id", null, null, null)));
  }
}
