package com.cube.nanotimer.smartcube.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.cube.nanotimer.smartcube.SmartCube;
import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.drivers.MoyuV10Driver;
import com.cube.nanotimer.smartcube.model.CubeBrand;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeMove;
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
 * Ports the sibling package's scanner_test.dart: the same encrypted fixtures (for MAC
 * CF:30:16:00:AB:CD, which a WCU_MY32_ABCD cube derives) exercise scan → connect → decode
 * through a fake transport, so the whole pipeline is verified without hardware.
 */
public class ScannerTest {

  private static final int[] C163 = {20, 81, 108, 156, 152, 10, 152, 58, 229, 121, 98, 221, 11, 123, 49, 53, 221, 107, 154, 186};
  private static final int[] C165 = {223, 209, 150, 204, 116, 21, 65, 40, 149, 201, 145, 0, 11, 185, 99, 221, 222, 17, 54, 129};
  private static final String U_FACELET = "UUUUUUUUUBBBRRRRRRRRRFFFFFFDDDDDDDDDFFFLLLLLLLLLBBBBBB";

  @Test
  public void scanConnectDecodeThroughFakeTransport() {
    FakeBle.Chr readChr = new FakeBle.Chr(BleUuid.normalize(MoyuV10Driver.READ_CHR_UUID));
    FakeBle.Chr writeChr = new FakeBle.Chr(BleUuid.normalize(MoyuV10Driver.WRITE_CHR_UUID));
    FakeBle.Service service =
        new FakeBle.Service(BleUuid.normalize(MoyuV10Driver.SERVICE_UUID), Arrays.asList(readChr, writeChr));
    FakeBle.Peripheral peripheral = new FakeBle.Peripheral("dev1", "WCU_MY32_ABCD", Collections.singletonList(service));
    BleScanResult scanResult = new BleScanResult(
        "dev1", "WCU_MY32_ABCD",
        Collections.singletonList(BleUuid.normalize(MoyuV10Driver.SERVICE_UUID)), null);
    FakeBle.Transport transport = new FakeBle.Transport(peripheral, scanResult);

    CubeScanner scanner = CubeScannerFactory.create(transport);

    List<DiscoveredCube> discovered = new ArrayList<>();
    scanner.scan(discovered::add);
    assertEquals(1, discovered.size());
    assertEquals(CubeBrand.MOYU_V10, discovered.get(0).getBrand());
    assertEquals("MoYu WeiLong V10 AI", discovered.get(0).getModelName());
    assertFalse(discovered.get(0).needsMac()); // MAC derived from the name
    assertEquals("CF:30:16:00:AB:CD", discovered.get(0).getMacAddress());

    SmartCube cube = scanner.connect(discovered.get(0));
    assertEquals(CubeConnection.READY, cube.getConnection());
    assertEquals(3, writeChr.written.size()); // handshake: info, status, power

    List<CubeState> states = new ArrayList<>();
    List<CubeMove> moves = new ArrayList<>();
    cube.addStateListener(states::add);
    cube.addMoveListener(moves::add);

    readChr.push(C163); // solved anchor
    readChr.push(C165); // one U move

    assertEquals(CubieCube.SOLVED_FACELET, states.get(0).getFacelets());
    assertEquals(1, moves.size());
    assertEquals(Face.U, moves.get(0).getFace());
    assertFalse(moves.get(0).isPrime());
    assertEquals(U_FACELET, states.get(states.size() - 1).getFacelets());
    assertEquals(U_FACELET, cube.getCurrentState().getFacelets());
  }
}
