package com.cube.nanotimer.smartcube.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.driver.CubeAdvertisement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class QiyiDriverTest {

  private final QiyiDriver driver = new QiyiDriver();

  private static CubeAdvertisement named(String name) {
    return new CubeAdvertisement("x", name, null, null);
  }

  @Test
  public void macFromAQiyiName() {
    assertEquals("CC:A3:00:00:AB:CD", QiyiDriver.deriveMac(named("QY-QYSC-1-ABCD")));
  }

  @Test
  public void macFromATornadoV4Name() {
    assertEquals("CC:A3:00:00:12:EF", QiyiDriver.deriveMac(named("XMD-TornadoV4-i-2-12EF")));
  }

  @Test
  public void macFromManufacturerDataIsTheFirstSixBytesReversed() {
    CubeAdvertisement adv = new CubeAdvertisement("x", "QY-QYSC", null,
        Collections.singletonMap(0x0504,
            new int[] {0xCD, 0xAB, 0x00, 0x00, 0xA3, 0xCC, 0x99, 0x99}));
    assertEquals("CC:A3:00:00:AB:CD", QiyiDriver.deriveMac(adv));
  }

  @Test
  public void manufacturerDataUnderAnotherCompanyIdIsNotTheMac() {
    Map<Integer, int[]> data = Collections.singletonMap(0x0001,
        new int[] {0xCD, 0xAB, 0x00, 0x00, 0xA3, 0xCC});
    CubeAdvertisement adv = new CubeAdvertisement("x", "QY-QYSC", null, data);
    assertNull(QiyiDriver.deriveMac(adv));
    assertTrue(driver.needsExplicitMac(adv));
  }

  @Test
  public void aNameDerivedMacNeedsNoPrompt() {
    assertFalse(driver.needsExplicitMac(named("QY-QYSC-1-ABCD")));
  }

  @Test
  public void claimsBothModelNames() {
    assertTrue(driver.matches(named("QY-QYSC-1-ABCD")));
    assertTrue(driver.matches(named("XMD-TornadoV4-i-1-ABCD")));
    assertEquals("QiYi Smart Cube", driver.getModelName(named("QY-QYSC-1-ABCD")));
    assertEquals("Tornado V4 Smart", driver.getModelName(named("XMD-TornadoV4-i-1-ABCD")));
  }

  /** The fff0 service is the same UUID GAN Gen1 advertises, so claiming it would swallow GAN cubes. */
  @Test
  public void doesNotClaimTheFff0Service() {
    CubeAdvertisement gan = new CubeAdvertisement("x", "GAN-1234",
        List.of("0000fff0-0000-1000-8000-00805f9b34fb"), null);
    assertFalse(driver.matches(gan));
  }
}
