package com.cube.nanotimer.smartcube.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.SmartCube;
import com.cube.nanotimer.smartcube.crypto.GanCipher;
import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.drivers.GanDriver;
import com.cube.nanotimer.smartcube.model.CubeBrand;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.transport.BleScanResult;
import com.cube.nanotimer.smartcube.transport.BleUuid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Scan → connect → decode for a GAN cube through a fake transport, so the generation is picked from
 * the advertised services and the whole pipeline is verified without hardware.
 */
public class GanScannerTest {

  private static final String MAC = "AB:12:34:56:78:9A";
  private static final String U_FACELET =
      "UUUUUUUUUBBBRRRRRRRRRFFFFFFDDDDDDDDDFFFLLLLLLLLLBBBBBB";

  /** GAN puts the MAC, reversed, in the last 6 of the first 9 manufacturer-data bytes. */
  private static final BleScanResult SCAN_RESULT = new BleScanResult("dev1", "GAN-1234",
      Collections.singletonList(BleUuid.normalize(GanDriver.GEN2_SERVICE)),
      Collections.singletonMap(0x0001,
          new int[] {0x00, 0x00, 0x00, 0x9A, 0x78, 0x56, 0x34, 0x12, 0xAB}));

  @Test
  public void scanConnectDecodeThroughFakeTransport() {
    FakeBle.Chr stateChr = new FakeBle.Chr(BleUuid.normalize(GanDriver.GEN2_STATE_CHR_UUID));
    FakeBle.Chr commandChr = new FakeBle.Chr(BleUuid.normalize(GanDriver.GEN2_COMMAND_CHR_UUID));
    FakeBle.Service service = new FakeBle.Service(BleUuid.normalize(GanDriver.GEN2_SERVICE),
        Arrays.asList(stateChr, commandChr));
    FakeBle.Peripheral peripheral =
        new FakeBle.Peripheral("dev1", "GAN-1234", Collections.singletonList(service));

    CubeScanner scanner = CubeScannerFactory.create(new FakeBle.Transport(peripheral, SCAN_RESULT));

    List<DiscoveredCube> discovered = new ArrayList<>();
    scanner.scan(discovered::add);
    assertEquals(1, discovered.size());
    assertEquals(CubeBrand.GAN, discovered.get(0).getBrand());
    assertEquals("GAN smart cube", discovered.get(0).getModelName());
    assertFalse(discovered.get(0).needsMac()); // MAC derived from the manufacturer data
    assertEquals(MAC, discovered.get(0).getMacAddress());

    SmartCube cube = scanner.connect(discovered.get(0));
    assertEquals(CubeConnection.READY, cube.getConnection());
    assertEquals(3, commandChr.written.size()); // handshake: hardware, battery, facelets

    List<CubeState> states = new ArrayList<>();
    List<CubeMove> moves = new ArrayList<>();
    cube.addStateListener(states::add);
    cube.addMoveListener(moves::add);

    stateChr.push(facelets(5)); // solved anchor
    stateChr.push(uMove(6));

    assertEquals(CubieCube.SOLVED_FACELET, states.get(0).getFacelets());
    assertEquals(1, moves.size());
    assertEquals(Face.U, moves.get(0).getFace());
    assertFalse(moves.get(0).isPrime());
    assertEquals(U_FACELET, states.get(states.size() - 1).getFacelets());
    assertEquals(U_FACELET, cube.getCurrentState().getFacelets());
  }

  /** The orientation stream is polled rather than pushed, so it has to reach the cube's own getter. */
  @Test
  public void gyroReadingsReachTheConnectedCube() {
    FakeBle.Chr stateChr = new FakeBle.Chr(BleUuid.normalize(GanDriver.GEN2_STATE_CHR_UUID));
    FakeBle.Chr commandChr = new FakeBle.Chr(BleUuid.normalize(GanDriver.GEN2_COMMAND_CHR_UUID));
    FakeBle.Service service = new FakeBle.Service(BleUuid.normalize(GanDriver.GEN2_SERVICE),
        Arrays.asList(stateChr, commandChr));
    FakeBle.Peripheral peripheral =
        new FakeBle.Peripheral("dev1", "GAN-1234", Collections.singletonList(service));
    CubeScanner scanner = CubeScannerFactory.create(new FakeBle.Transport(peripheral, SCAN_RESULT));
    List<DiscoveredCube> discovered = new ArrayList<>();
    scanner.scan(discovered::add);
    SmartCube cube = scanner.connect(discovered.get(0));

    assertNull(cube.getOrientation()); // nothing has been reported yet
    stateChr.push(gyro());

    CubeOrientation orientation = cube.getOrientation();
    assertNotNull(orientation);
    assertEquals(1.0, orientation.getW(), 1e-4);
  }

  /**
   * A GAN says this when it dozes off after sitting still, and the link survives it. Reading it as a
   * lost connection is what left the app half dead: the manager dropped the cube, so the chip lost
   * its battery number and the gyro stopped, while moves kept arriving and nothing put it back.
   */
  @Test
  public void aSleepAnnouncementDoesNotDropTheConnection() {
    FakeBle.Chr stateChr = new FakeBle.Chr(BleUuid.normalize(GanDriver.GEN2_STATE_CHR_UUID));
    FakeBle.Chr commandChr = new FakeBle.Chr(BleUuid.normalize(GanDriver.GEN2_COMMAND_CHR_UUID));
    FakeBle.Service service = new FakeBle.Service(BleUuid.normalize(GanDriver.GEN2_SERVICE),
        Arrays.asList(stateChr, commandChr));
    FakeBle.Peripheral peripheral =
        new FakeBle.Peripheral("dev1", "GAN-1234", Collections.singletonList(service));
    CubeScanner scanner = CubeScannerFactory.create(new FakeBle.Transport(peripheral, SCAN_RESULT));
    List<DiscoveredCube> discovered = new ArrayList<>();
    scanner.scan(discovered::add);
    SmartCube cube = scanner.connect(discovered.get(0));
    int afterHandshake = commandChr.written.size();

    stateChr.push(sleepAnnouncement());
    assertEquals(CubeConnection.READY, cube.getConnection());
    assertEquals(afterHandshake, commandChr.written.size()); // nothing to ask while it is quiet

    stateChr.push(sleepAnnouncement()); // saying it twice is still not waking up
    assertEquals(afterHandshake, commandChr.written.size());

    stateChr.push(gyro()); // it spoke again, so it never went
    assertEquals(CubeConnection.READY, cube.getConnection());
    // The battery is the one thing that goes stale while it dozes, so it is asked for again.
    assertEquals(afterHandshake + 1, commandChr.written.size());
  }

  /** A cube exposing no GAN service at all must fail the handshake rather than connect half-open. */
  @Test
  public void aPeripheralWithoutAGanServiceIsRejected() {
    FakeBle.Service service = new FakeBle.Service(BleUuid.normalize(
        "0000fff0-0000-1000-8000-00805f9b34fb"), Collections.emptyList());
    FakeBle.Peripheral peripheral =
        new FakeBle.Peripheral("dev1", "GAN-1234", Collections.singletonList(service));
    CubeScanner scanner = CubeScannerFactory.create(new FakeBle.Transport(peripheral, SCAN_RESULT));

    List<DiscoveredCube> discovered = new ArrayList<>();
    scanner.scan(discovered::add);

    try {
      scanner.connect(discovered.get(0));
      throw new AssertionError("expected the handshake to fail");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("GAN service"));
    }
  }

  private static int[] encrypted(int[] plain) {
    return GanCipher.forMac(GAN_BASE_KEY, GAN_BASE_IV, GanCipher.macBytes(MAC)).encode(plain);
  }

  // GAN Gen2/3/4 base key/IV (the same constants the parser derives its cipher from).
  private static final int[] GAN_BASE_KEY = {
    0x01, 0x02, 0x42, 0x28, 0x31, 0x91, 0x16, 0x07,
    0x20, 0x05, 0x18, 0x54, 0x42, 0x11, 0x12, 0x53,
  };
  private static final int[] GAN_BASE_IV = {
    0x11, 0x03, 0x32, 0x28, 0x21, 0x01, 0x76, 0x27,
    0x20, 0x95, 0x78, 0x14, 0x32, 0x12, 0x02, 0x43,
  };

  /** A Gen2 facelets packet for a solved cube: opcode 0x04, then the pieces from bit 12. */
  private static int[] facelets(int serial) {
    int[] plain = new int[20];
    plain[0] = 0x04 << 4 | (serial >> 4);
    plain[1] = (serial & 0x0F) << 4;
    writeBits(plain, 12, 3, new int[] {0, 1, 2, 3, 4, 5, 6});          // corner permutation
    writeBits(plain, 47, 4, new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}); // edge permutation
    return encrypted(plain);
  }

  /** A Gen2 gyro packet: opcode 0x01, then the identity quaternion from bit 4. */
  private static int[] gyro() {
    int[] plain = new int[20];
    plain[0] = 0x01 << 4 | 0x07; // event type, then the top nibble of w's 0x7FFF magnitude
    plain[1] = 0xFF;
    plain[2] = 0xF0;
    return encrypted(plain);
  }

  /** A Gen2 power-down announcement: opcode 0x0D and nothing else. */
  private static int[] sleepAnnouncement() {
    int[] plain = new int[20];
    plain[0] = 0x0D << 4;
    return encrypted(plain);
  }

  /** A Gen2 move packet: opcode 0x02, one U turn 500ms after the last. */
  private static int[] uMove(int serial) {
    int[] plain = new int[20];
    plain[0] = 0x02 << 4 | (serial >> 4);
    plain[1] = (serial & 0x0F) << 4; // face code 0 (U), not prime — the rest of the nibble is zero
    writeBits(plain, 47, 16, new int[] {500});
    return encrypted(plain);
  }

  private static void writeBits(int[] bytes, int startBit, int width, int[] values) {
    for (int v = 0; v < values.length; v++) {
      for (int i = 0; i < width; i++) {
        int pos = startBit + v * width + i;
        if (((values[v] >> (width - 1 - i)) & 1) == 1) {
          bytes[pos / 8] |= 1 << (7 - pos % 8);
        }
      }
    }
  }
}
