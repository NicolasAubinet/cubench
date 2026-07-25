package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.SmartCube;
import com.cube.nanotimer.smartcube.crypto.GanCipher;
import com.cube.nanotimer.smartcube.driver.CubeAdvertisement;
import com.cube.nanotimer.smartcube.driver.CubeDriver;
import com.cube.nanotimer.smartcube.model.CubeBrand;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;
import com.cube.nanotimer.smartcube.transport.BlePeripheral;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Driver for GAN smart cubes. The generation is not something the user picks — every GAN cube
 * advertises the same names, so the protocol is chosen from whichever service the cube turns out to
 * expose:
 *
 * <ul>
 *   <li><b>Gen2</b> — GAN 356 i3, i Carry / i Carry S, GAN12 ui, Mini ui FreePlay, Monster Go 3Ai,
 *       and the MoYu AI 2023 ({@code AiCube}, same protocol, own key).
 *   <li><b>Gen3</b> — GAN 356 i Carry 2.
 *   <li><b>Gen4</b> — GAN12 ui Maglev, GAN14 ui FreePlay.
 * </ul>
 */
public final class GanDriver extends CubeDriver {

  public static final String GEN2_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dc4179";
  public static final String GEN2_COMMAND_CHR_UUID = "28be4a4a-cd67-11e9-a32f-2a2ae2dbcce4";
  public static final String GEN2_STATE_CHR_UUID = "28be4cb6-cd67-11e9-a32f-2a2ae2dbcce4";

  public static final String GEN3_SERVICE = "8653000a-43e6-47b7-9cb0-5fc21d4ae340";
  public static final String GEN3_COMMAND_CHR_UUID = "8653000c-43e6-47b7-9cb0-5fc21d4ae340";
  public static final String GEN3_STATE_CHR_UUID = "8653000b-43e6-47b7-9cb0-5fc21d4ae340";

  public static final String GEN4_SERVICE = "00000010-0000-fff7-fff6-fff5fff4fff0";
  public static final String GEN4_COMMAND_CHR_UUID = "0000fff5-0000-1000-8000-00805f9b34fb";
  public static final String GEN4_STATE_CHR_UUID = "0000fff6-0000-1000-8000-00805f9b34fb";

  /** The MoYu AI 2023 advertises under this name and speaks Gen2 with its own key. */
  private static final String MOYU_AI_NAME = "AiCube";

  /** Tried in order against the cube's advertised services. */
  static final List<Generation> GENERATIONS = List.of(
      new Generation(GEN2_SERVICE, GEN2_COMMAND_CHR_UUID, GEN2_STATE_CHR_UUID,
          GanGen2Parser::new),
      new Generation(GEN3_SERVICE, GEN3_COMMAND_CHR_UUID, GEN3_STATE_CHR_UUID,
          (mac, moyuAi) -> new GanGen3Parser(mac)),
      new Generation(GEN4_SERVICE, GEN4_COMMAND_CHR_UUID, GEN4_STATE_CHR_UUID,
          (mac, moyuAi) -> new GanGen4Parser(mac)));

  @Override
  public CubeBrand getBrand() {
    return CubeBrand.GAN;
  }

  @Override
  public List<String> getNamePrefixes() {
    return List.of("GAN", "MG", MOYU_AI_NAME);
  }

  @Override
  public List<String> getServiceUuids() {
    return List.of(GEN2_SERVICE, GEN3_SERVICE, GEN4_SERVICE);
  }

  @Override
  public boolean needsExplicitMac(CubeAdvertisement adv) {
    return deriveMac(adv) == null;
  }

  /**
   * The advertised name is all there is to go on before connecting — the generation only shows in
   * the services, which is a connection away.
   */
  @Override
  public String getModelName(CubeAdvertisement adv) {
    String name = adv.getName() == null ? "" : adv.getName();
    if (name.startsWith(MOYU_AI_NAME)) {
      return "MoYu AI 2023";
    }
    if (name.startsWith("MG")) {
      return "Monster Go AI";
    }
    return "GAN smart cube";
  }

  @Override
  public String getMacAddress(CubeAdvertisement adv) {
    return deriveMac(adv);
  }

  @Override
  public SmartCube connect(BlePeripheral peripheral, CubeAdvertisement adv, String macAddress) {
    String mac = macAddress != null ? macAddress : deriveMac(adv);
    if (mac == null) {
      throw new IllegalStateException("GAN cubes require a MAC address");
    }
    DiscoveredCube device = new DiscoveredCube(
        peripheral.getId(), peripheral.getName(), CubeBrand.GAN, getModelName(adv), mac, false);
    String name = adv.getName() != null ? adv.getName() : peripheral.getName();
    GanCube cube = new GanCube(device, peripheral, GanCipher.macBytes(mac),
        name != null && name.startsWith(MOYU_AI_NAME));
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
   * The MAC lives in the last 6 bytes (reversed) of the first 9 bytes of the manufacturer data,
   * under a GAN company identifier — every code of the form {@code 0xNN01}. Null when the platform
   * hides it, leaving the caller to ask the user.
   */
  public static String deriveMac(CubeAdvertisement adv) {
    for (Map.Entry<Integer, int[]> entry : adv.getManufacturerData().entrySet()) {
      if ((entry.getKey() & 0xFF) != 0x01) {
        continue;
      }
      int[] data = entry.getValue();
      if (data.length > 9) {
        data = Arrays.copyOf(data, 9);
      }
      if (data.length < 6) {
        continue;
      }
      StringBuilder mac = new StringBuilder();
      for (int i = 1; i <= 6; i++) {
        if (i > 1) {
          mac.append(':');
        }
        String hex = Integer.toHexString(data[data.length - i] & 0xff);
        if (hex.length() < 2) {
          mac.append('0');
        }
        mac.append(hex);
      }
      return mac.toString().toUpperCase();
    }
    return null;
  }

  /** One GAN protocol generation, and how to recognise and speak it. */
  static final class Generation {
    private final String service;
    private final String commandChrUuid;
    private final String stateChrUuid;
    private final BiFunction<int[], Boolean, GanProtocol> factory;

    Generation(String service, String commandChrUuid, String stateChrUuid,
        BiFunction<int[], Boolean, GanProtocol> factory) {
      this.service = service;
      this.commandChrUuid = commandChrUuid;
      this.stateChrUuid = stateChrUuid;
      this.factory = factory;
    }

    String getService() {
      return service;
    }

    String getCommandChrUuid() {
      return commandChrUuid;
    }

    String getStateChrUuid() {
      return stateChrUuid;
    }

    GanProtocol build(int[] mac, boolean moyuAi) {
      return factory.apply(mac, moyuAi);
    }
  }
}
