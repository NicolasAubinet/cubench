package com.cube.nanotimer.smartcube.scanner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.cube.nanotimer.smartcube.SmartCube;
import com.cube.nanotimer.smartcube.crypto.Aes128;
import com.cube.nanotimer.smartcube.drivers.QiyiDriver;
import com.cube.nanotimer.smartcube.drivers.QiyiParser;
import com.cube.nanotimer.smartcube.model.CubeBrand;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.transport.BleScanResult;
import com.cube.nanotimer.smartcube.transport.BleService;
import com.cube.nanotimer.smartcube.transport.BleUuid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Scan to connect to decode through the fake transport, the way {@link ScannerTest} does for the
 * MoYu: what differs between brands is the UUIDs and the packets, never the pipeline.
 */
public class QiyiScannerTest {

  private static final Aes128 AES = new Aes128(QiyiParser.FIXED_KEY);
  private static final String NAME = "QY-QYSC-1-ABCD";
  private static final String U_FACELETS =
      "UUUUUUUUUBBBRRRRRRRRRFFFFFFDDDDDDDDDFFFLLLLLLLLLBBBBBB";

  private FakeBle.Chr chr;

  private CubeScanner rig() {
    chr = new FakeBle.Chr(BleUuid.normalize(QiyiDriver.CHR_UUID));
    FakeBle.Service service = new FakeBle.Service(
        BleUuid.normalize(QiyiDriver.SERVICE_UUID), Collections.singletonList(chr));
    FakeBle.Peripheral peripheral =
        new FakeBle.Peripheral("dev1", NAME, Collections.singletonList(service));
    BleScanResult scanResult = new BleScanResult("dev1", NAME,
        Collections.singletonList(BleUuid.normalize(QiyiDriver.SERVICE_UUID)), null);
    return CubeScannerFactory.create(new FakeBle.Transport(peripheral, scanResult));
  }

  @Test
  public void scanConnectHelloThenDecodeMovesAndStates() {
    CubeScanner scanner = rig();

    List<DiscoveredCube> discovered = new ArrayList<>();
    scanner.scan(discovered::add);
    assertEquals(1, discovered.size());
    assertEquals(CubeBrand.QIYI, discovered.get(0).getBrand());
    assertEquals("QiYi Smart Cube", discovered.get(0).getModelName());
    assertFalse(discovered.get(0).needsMac()); // MAC derived from the name
    assertEquals("CC:A3:00:00:AB:CD", discovered.get(0).getMacAddress());

    SmartCube cube = scanner.connect(discovered.get(0));
    assertEquals(CubeConnection.READY, cube.getConnection());

    // The app must greet the cube first, or it never says anything.
    assertEquals(1, chr.written.size());
    int[] hello = unframe(chr.written.get(0));
    assertArrayEquals(new int[] {0xCD, 0xAB, 0x00, 0x00, 0xA3, 0xCC},
        Arrays.copyOfRange(hello, 13, 19));

    List<CubeState> states = new ArrayList<>();
    List<CubeMove> moves = new ArrayList<>();
    cube.addStateListener(states::add);
    cube.addMoveListener(moves::add);

    chr.push(cubeHello(CubeState.SOLVED_FACELETS));
    chr.push(stateChange(U_FACELETS, 8, false));

    assertEquals(CubeState.SOLVED, states.get(0));
    assertEquals(1, moves.size());
    assertEquals(Face.U, moves.get(0).getFace());
    assertFalse(moves.get(0).isPrime());
    assertEquals(U_FACELETS, states.get(states.size() - 1).getFacelets());
    assertEquals(U_FACELETS, cube.getCurrentState().getFacelets());
    assertEquals(Integer.valueOf(88), cube.getBatteryLevel());

    // The cube hello is acked; the app has stopped repeating its own hello.
    assertEquals(2, chr.written.size());
    cube.disconnect();
  }

  @Test
  public void aStateChangeNeedingAnAckIsAckedAndReadAsSolved() {
    CubeScanner scanner = rig();
    List<DiscoveredCube> discovered = new ArrayList<>();
    scanner.scan(discovered::add);
    SmartCube cube = scanner.connect(discovered.get(0));

    List<CubeState> states = new ArrayList<>();
    cube.addStateListener(states::add);

    chr.push(cubeHello(U_FACELETS));
    chr.push(stateChange(U_FACELETS, 8, true));

    assertEquals(CubeState.SOLVED, states.get(states.size() - 1));
    assertEquals(3, chr.written.size()); // hello + 2 acks
    cube.disconnect();
  }

  @Test
  public void aMissingServiceFailsTheConnectAndHangsUpTheLink() {
    FakeBle.Peripheral peripheral =
        new FakeBle.Peripheral("dev1", NAME, Collections.<BleService>emptyList());
    CubeScanner scanner = CubeScannerFactory.create(new FakeBle.Transport(
        peripheral, new BleScanResult("dev1", NAME, null, null)));
    List<DiscoveredCube> discovered = new ArrayList<>();
    scanner.scan(discovered::add);

    try {
      scanner.connect(discovered.get(0));
      fail("expected the missing service to fail the connect");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("QiYi service"));
    }
  }

  // ---- fixtures ----------------------------------------------------------------------------

  // Content layout: opcode, 4-byte big-endian tick count, 27 state bytes, move, battery.
  private static int[] cubeHello(String facelets) {
    int[] content = new int[34];
    content[0] = QiyiParser.OP_CUBE_HELLO;
    content[3] = 0x03; // 1000 ticks
    content[4] = 0xE8;
    System.arraycopy(stateBytes(facelets), 0, content, 5, 27);
    content[33] = 88; // battery
    return frame(content);
  }

  /** The state change runs on past the battery to a needs-ACK flag 55 spare bytes later. */
  private static int[] stateChange(String facelets, int move, boolean needsAck) {
    int[] content = new int[90];
    content[0] = QiyiParser.OP_STATE_CHANGE;
    content[3] = 0x07; // 2000 ticks
    content[4] = 0xD0;
    System.arraycopy(stateBytes(facelets), 0, content, 5, 27);
    content[32] = move;
    content[33] = 88; // battery
    content[89] = needsAck ? 1 : 0;
    return frame(content);
  }

  private static int[] stateBytes(String facelets) {
    int[] out = new int[27];
    for (int i = 0; i < 27; i++) {
      out[i] = "LRDUFB".indexOf(facelets.charAt(i * 2))
          | ("LRDUFB".indexOf(facelets.charAt(i * 2 + 1)) << 4);
    }
    return out;
  }

  private static int[] frame(int[] content) {
    int length = content.length + 4;
    int[] msg = new int[(length + 15) / 16 * 16];
    msg[0] = 0xFE;
    msg[1] = length;
    System.arraycopy(content, 0, msg, 2, content.length);
    int crc = QiyiParser.crc16Modbus(msg, length - 2);
    msg[length - 2] = crc & 0xFF;
    msg[length - 1] = crc >> 8;
    return mapBlocks(msg, true);
  }

  private static int[] unframe(int[] raw) {
    return mapBlocks(raw, false);
  }

  private static int[] mapBlocks(int[] data, boolean encrypt) {
    int[] out = data.clone();
    int[] block = new int[16];
    for (int off = 0; off + 16 <= out.length; off += 16) {
      System.arraycopy(out, off, block, 0, 16);
      System.arraycopy(encrypt ? AES.encrypt(block) : AES.decrypt(block), 0, out, off, 16);
    }
    return out;
  }
}
