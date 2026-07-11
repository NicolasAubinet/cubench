package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.SmartCube;
import com.cube.nanotimer.smartcube.crypto.GanCipher;
import com.cube.nanotimer.smartcube.driver.CubeAdvertisement;
import com.cube.nanotimer.smartcube.driver.CubeDriver;
import com.cube.nanotimer.smartcube.model.CubeBrand;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;
import com.cube.nanotimer.smartcube.transport.BlePeripheral;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Driver for the MoYu WeiLong V10 AI ({@code WCU_MY32}). */
public final class MoyuV10Driver extends CubeDriver {

  public static final String SERVICE_UUID = "0783b03e-7735-b5a0-1760-a305d2795cb0";
  public static final String READ_CHR_UUID = "0783b03e-7735-b5a0-1760-a305d2795cb1";
  public static final String WRITE_CHR_UUID = "0783b03e-7735-b5a0-1760-a305d2795cb2";

  private static final Pattern NAME_MAC = Pattern.compile("^WCU_MY32_[0-9A-Fa-f]{4}$");

  @Override
  public CubeBrand getBrand() {
    return CubeBrand.MOYU_V10;
  }

  @Override
  public List<String> getNamePrefixes() {
    return Collections.singletonList("WCU_MY3");
  }

  @Override
  public List<String> getServiceUuids() {
    return Collections.singletonList(SERVICE_UUID);
  }

  @Override
  public boolean needsExplicitMac(CubeAdvertisement adv) {
    return deriveMac(adv) == null;
  }

  @Override
  public SmartCube connect(BlePeripheral peripheral, CubeAdvertisement adv, String macAddress) {
    String mac = macAddress != null ? macAddress : deriveMac(adv);
    if (mac == null) {
      throw new IllegalStateException("MoYu V10 requires a MAC address");
    }
    DiscoveredCube device =
        new DiscoveredCube(peripheral.getId(), peripheral.getName(), CubeBrand.MOYU_V10, false);
    MoyuV10Cube cube = new MoyuV10Cube(device, peripheral, new MoyuV10Parser(GanCipher.macBytes(mac)));
    cube.start();
    return cube;
  }

  /**
   * Best-effort MAC discovery: from the device name (WCU_MY32_XXXX → CF:30:16:00:XX:XX),
   * else from advertisement manufacturer data (last 6 bytes, reversed). Null if neither works.
   */
  public static String deriveMac(CubeAdvertisement adv) {
    String name = adv.getName() == null ? "" : adv.getName();
    if (NAME_MAC.matcher(name).matches()) {
      String tail = name.substring(9).toUpperCase();
      return "CF:30:16:00:" + tail.substring(0, 2) + ":" + tail.substring(2, 4);
    }
    for (int[] data : adv.getManufacturerData().values()) {
      if (data.length >= 6) {
        StringBuilder mac = new StringBuilder();
        for (int i = 0; i < 6; i++) {
          if (i > 0) {
            mac.append(':');
          }
          String hex = Integer.toHexString(data[data.length - i - 1] & 0xff);
          if (hex.length() < 2) {
            mac.append('0');
          }
          mac.append(hex);
        }
        return mac.toString().toUpperCase();
      }
    }
    return null;
  }
}
