package com.cube.nanotimer.smartcube.driver;

import com.cube.nanotimer.smartcube.SmartCube;
import com.cube.nanotimer.smartcube.model.CubeBrand;
import com.cube.nanotimer.smartcube.transport.BlePeripheral;
import com.cube.nanotimer.smartcube.transport.BleUuid;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One brand's protocol implementation. Kept transport-agnostic (it programs only against
 * the {@link BlePeripheral} abstraction) so it unit-tests from a fake transport and ports
 * cleanly across languages.
 */
public abstract class CubeDriver {

  public abstract CubeBrand getBrand();

  /** Name prefixes this driver claims (e.g. "WCU_MY3" for the MoYu V10). */
  public abstract List<String> getNamePrefixes();

  /** Service UUIDs this driver claims, for name-less devices. */
  public abstract List<String> getServiceUuids();

  public boolean matches(CubeAdvertisement adv) {
    String name = adv.getName();
    if (name != null) {
      for (String prefix : getNamePrefixes()) {
        if (name.startsWith(prefix)) {
          return true;
        }
      }
    }
    Set<String> claimed = new HashSet<>();
    for (String uuid : getServiceUuids()) {
      claimed.add(BleUuid.normalize(uuid));
    }
    for (String uuid : adv.getServiceUuids()) {
      if (claimed.contains(BleUuid.normalize(uuid))) {
        return true;
      }
    }
    return false;
  }

  /** True when this cube needs a MAC the caller must supply (not derivable from the advertisement). */
  public boolean needsExplicitMac(CubeAdvertisement adv) {
    return false;
  }

  /** Human-readable model label for scan-list rows (e.g. "MoYu WeiLong V11 AI"). */
  public String getModelName(CubeAdvertisement adv) {
    return getBrand().toString();
  }

  /** The cube's MAC address when it can be derived from the advertisement, else null. */
  public String getMacAddress(CubeAdvertisement adv) {
    return null;
  }

  /** Bring up a connected {@link SmartCube} over an already-open peripheral. Blocking. */
  public abstract SmartCube connect(BlePeripheral peripheral, CubeAdvertisement adv, String macAddress);
}
