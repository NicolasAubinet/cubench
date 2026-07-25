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

public class GanDriverTest {

  private final GanDriver driver = new GanDriver();

  /** GAN puts the MAC, reversed, in the last 6 of the first 9 manufacturer-data bytes. */
  private static final Map<Integer, int[]> GAN_MF_DATA = Collections.singletonMap(
      0x0001, new int[] {0x00, 0x00, 0x00, 0x9A, 0x78, 0x56, 0x34, 0x12, 0xAB});

  private static CubeAdvertisement adv(String name, Map<Integer, int[]> manufacturerData) {
    return new CubeAdvertisement("id", name, null, manufacturerData);
  }

  @Test
  public void macIsDerivedFromTheManufacturerData() {
    assertEquals("AB:12:34:56:78:9A", GanDriver.deriveMac(adv("GAN-1234", GAN_MF_DATA)));
  }

  /** Only a GAN company identifier carries the MAC; every code of the form 0xNN01. */
  @Test
  public void anotherVendorsManufacturerDataIsIgnored() {
    Map<Integer, int[]> other = Collections.singletonMap(
        0x0002, new int[] {0x00, 0x00, 0x00, 0x9A, 0x78, 0x56, 0x34, 0x12, 0xAB});
    assertNull(GanDriver.deriveMac(adv("GAN-1234", other)));
  }

  @Test
  public void aMacIsAskedForWhenTheAdvertisementHidesIt() {
    assertTrue(driver.needsExplicitMac(adv("GAN-1234", null)));
    assertFalse(driver.needsExplicitMac(adv("GAN-1234", GAN_MF_DATA)));
  }

  @Test
  public void ganNamesAreClaimed() {
    assertTrue(driver.matches(adv("GAN-1234", null)));
    assertTrue(driver.matches(adv("MG-abcd", null)));
    assertTrue(driver.matches(adv("AiCube-01", null)));
    assertFalse(driver.matches(adv("WCU_MY32_ABCD", null))); // a MoYu, not ours
  }

  @Test
  public void everyGenerationsServiceIsClaimedForNamelessDevices() {
    for (String service : List.of(GanDriver.GEN2_SERVICE, GanDriver.GEN3_SERVICE,
        GanDriver.GEN4_SERVICE)) {
      assertTrue(driver.matches(
          new CubeAdvertisement("id", null, Collections.singletonList(service), null)));
    }
  }

  /** The generation only shows in the services, a connection away, so the name is all there is. */
  @Test
  public void modelNameComesFromTheAdvertisedName() {
    assertEquals("MoYu AI 2023", driver.getModelName(adv("AiCube-01", null)));
    assertEquals("Monster Go AI", driver.getModelName(adv("MG-abcd", null)));
    assertEquals("GAN smart cube", driver.getModelName(adv("GAN-1234", null)));
    assertEquals("GAN smart cube", driver.getModelName(adv(null, null)));
  }
}
