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
import com.cube.nanotimer.smartcube.transport.BleCharacteristic;
import com.cube.nanotimer.smartcube.transport.BlePeripheral;
import com.cube.nanotimer.smartcube.transport.BleScanResult;
import com.cube.nanotimer.smartcube.transport.BleService;
import com.cube.nanotimer.smartcube.transport.BleTransport;
import com.cube.nanotimer.smartcube.transport.BleUuid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
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
    FakeChr readChr = new FakeChr(BleUuid.normalize(MoyuV10Driver.READ_CHR_UUID));
    FakeChr writeChr = new FakeChr(BleUuid.normalize(MoyuV10Driver.WRITE_CHR_UUID));
    FakeService service =
        new FakeService(BleUuid.normalize(MoyuV10Driver.SERVICE_UUID), Arrays.asList(readChr, writeChr));
    FakePeripheral peripheral = new FakePeripheral("dev1", "WCU_MY32_ABCD", Collections.singletonList(service));
    BleScanResult scanResult = new BleScanResult(
        "dev1", "WCU_MY32_ABCD",
        Collections.singletonList(BleUuid.normalize(MoyuV10Driver.SERVICE_UUID)), null);
    FakeTransport transport = new FakeTransport(peripheral, scanResult);

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

  private static final class FakeChr implements BleCharacteristic {
    private final String uuid;
    private final List<Consumer<int[]>> listeners = new ArrayList<>();
    final List<int[]> written = new ArrayList<>();

    FakeChr(String uuid) {
      this.uuid = uuid;
    }

    void push(int[] value) {
      for (Consumer<int[]> listener : listeners) {
        listener.accept(value);
      }
    }

    @Override
    public String getUuid() {
      return uuid;
    }

    @Override
    public void addValueListener(Consumer<int[]> onValue) {
      listeners.add(onValue);
    }

    @Override
    public void enableNotifications() {}

    @Override
    public void write(int[] data) {
      written.add(data);
    }
  }

  private static final class FakeService implements BleService {
    private final String uuid;
    private final List<BleCharacteristic> characteristics;

    FakeService(String uuid, List<BleCharacteristic> characteristics) {
      this.uuid = uuid;
      this.characteristics = characteristics;
    }

    @Override
    public String getUuid() {
      return uuid;
    }

    @Override
    public List<BleCharacteristic> getCharacteristics() {
      return characteristics;
    }
  }

  private static final class FakePeripheral implements BlePeripheral {
    private final String id;
    private final String name;
    private final List<BleService> services;

    FakePeripheral(String id, String name, List<BleService> services) {
      this.id = id;
      this.name = name;
      this.services = services;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public void addConnectionListener(Consumer<Boolean> onConnected) {}

    @Override
    public List<BleService> discoverServices() {
      return services;
    }

    @Override
    public void disconnect() {}
  }

  private static final class FakeTransport implements BleTransport {
    private final BlePeripheral peripheral;
    private final BleScanResult result;

    FakeTransport(BlePeripheral peripheral, BleScanResult result) {
      this.peripheral = peripheral;
      this.result = result;
    }

    @Override
    public void scan(Consumer<BleScanResult> onResult) {
      onResult.accept(result);
    }

    @Override
    public void stopScan() {}

    @Override
    public BlePeripheral connect(String deviceId) {
      return peripheral;
    }
  }
}
