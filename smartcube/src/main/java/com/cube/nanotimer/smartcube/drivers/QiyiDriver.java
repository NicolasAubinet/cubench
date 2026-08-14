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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Driver for the QiYi Smart Cube ({@code QY-QYSC}) and the Tornado V4 ({@code XMD-TornadoV4-i}),
 * which speaks the same protocol.
 */
public final class QiyiDriver extends CubeDriver {

  public static final String SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb";

  /** One characteristic for both write and notify, unlike every other brand. */
  public static final String CHR_UUID = "0000fff6-0000-1000-8000-00805f9b34fb";

  /** Company identifier the MAC hides under when the name does not carry it. */
  private static final int MANUFACTURER_CIC = 0x0504;

  private static final Pattern NAME_MAC =
      Pattern.compile("^(?:QY-QYSC|XMD-TornadoV4-i)-.-([0-9A-Fa-f]{4})$");

  @Override
  public CubeBrand getBrand() {
    return CubeBrand.QIYI;
  }

  @Override
  public List<String> getNamePrefixes() {
    return List.of("QY-QYSC", "XMD-TornadoV4-i");
  }

  /**
   * Deliberately empty. QiYi's {@code 0000fff0} service is the <b>same UUID GAN Gen1 advertises</b>,
   * so claiming it would let this driver swallow GAN cubes. The names disambiguate; nameless QiYi
   * devices are not worth that risk.
   */
  @Override
  public List<String> getServiceUuids() {
    return Collections.emptyList();
  }

  @Override
  public boolean needsExplicitMac(CubeAdvertisement adv) {
    return deriveMac(adv) == null;
  }

  @Override
  public String getModelName(CubeAdvertisement adv) {
    String name = adv.getName() == null ? "" : adv.getName();
    return name.startsWith("XMD-TornadoV4-i") ? "Tornado V4 Smart" : "QiYi Smart Cube";
  }

  @Override
  public String getMacAddress(CubeAdvertisement adv) {
    return deriveMac(adv);
  }

  @Override
  public SmartCube connect(BlePeripheral peripheral, CubeAdvertisement adv, String macAddress) {
    String mac = macAddress != null ? macAddress : deriveMac(adv);
    if (mac == null) {
      // The key is fixed, but the App Hello carries the MAC and the cube stays silent without it —
      // so this is as mandatory as it is on GAN.
      throw new IllegalStateException("QiYi cubes require a MAC address");
    }
    DiscoveredCube device = new DiscoveredCube(
        peripheral.getId(), peripheral.getName(), CubeBrand.QIYI, getModelName(adv), mac, false);
    QiyiCube cube = new QiyiCube(device, peripheral, new QiyiParser(GanCipher.macBytes(mac)));
    try {
      cube.start();
    } catch (RuntimeException e) {
      // Handshake failed with the link open: hang up, or a retry stacks another connection on top
      // of a half-open one.
      cube.disconnect();
      throw e;
    }
    return cube;
  }

  /**
   * From the device name ({@code QY-QYSC-X-XXXX} to {@code CC:A3:00:00:XX:XX}), else from
   * advertisement manufacturer data — the <b>first</b> 6 bytes, reversed (GAN's last-6-of-first-9
   * rule does <em>not</em> apply here). Null when the platform hides both, leaving the caller to ask
   * the user.
   */
  public static String deriveMac(CubeAdvertisement adv) {
    Matcher matcher = NAME_MAC.matcher(adv.getName() == null ? "" : adv.getName());
    if (matcher.matches()) {
      String tail = matcher.group(1).toUpperCase();
      return "CC:A3:00:00:" + tail.substring(0, 2) + ":" + tail.substring(2, 4);
    }
    int[] data = adv.getManufacturerData().get(MANUFACTURER_CIC);
    if (data == null || data.length < 6) {
      return null;
    }
    StringBuilder mac = new StringBuilder();
    for (int i = 5; i >= 0; i--) {
      if (mac.length() > 0) {
        mac.append(':');
      }
      String hex = Integer.toHexString(data[i] & 0xff);
      if (hex.length() < 2) {
        mac.append('0');
      }
      mac.append(hex);
    }
    return mac.toString().toUpperCase();
  }
}
