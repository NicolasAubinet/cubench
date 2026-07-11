package com.cube.nanotimer.smartcube.transport;

import java.util.List;

public interface BleService {

  String getUuid();

  List<BleCharacteristic> getCharacteristics();
}
