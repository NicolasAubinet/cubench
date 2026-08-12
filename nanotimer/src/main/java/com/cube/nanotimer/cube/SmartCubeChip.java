package com.cube.nanotimer.cube;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import androidx.core.content.ContextCompat;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.view.OutlineTextView;
import com.cube.nanotimer.smartcube.model.CubeBatteryListener;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;

/**
 * Binds an app-bar chip view to the live {@link SmartCubeManager} state: taps open the
 * connect sheet, and the icon/battery reflect the connection. One per activity; call
 * {@link #bind} with the chip view (a menu action view or a custom action-bar view) and
 * {@link #start}/{@link #stop} from the activity's resume/pause.
 */
public class SmartCubeChip implements CubeConnectionListener, CubeBatteryListener {

  private final Context context;
  private final Runnable onClick;
  private MenuItem item;
  private View chip;
  private boolean hideWhenDisconnected;
  private boolean suppressed;

  public SmartCubeChip(Context context, Runnable onClick) {
    this.context = context;
    this.onClick = onClick;
  }

  public void bind(View chip) {
    bind(null, chip);
  }

  /**
   * Binds the chip to a menu item, so hiding it takes the item with it.
   *
   * <p>An action view that is GONE still leaves its item in the bar, holding the width the bar
   * gives every item. On the timer that is a gap where the chip was, which is the one place the
   * chip is meant to disappear from entirely.
   *
   * @param item the item the chip is the action view of, or null outside a menu
   */
  public void bind(MenuItem item, View chip) {
    this.item = item;
    this.chip = chip;
    if (chip != null) {
      chip.setOnClickListener(v -> onClick.run());
    }
    refresh();
  }

  /** When true, the chip is hidden entirely unless a cube is connected (timer screen). */
  public void setHideWhenDisconnected(boolean hideWhenDisconnected) {
    this.hideWhenDisconnected = hideWhenDisconnected;
    refresh();
  }

  /** Force-hide the chip regardless of connection (e.g. while the timer is running). */
  public void setSuppressed(boolean suppressed) {
    this.suppressed = suppressed;
    refresh();
  }

  public void start() {
    SmartCubeManager.INSTANCE.addConnectionListener(this);
    SmartCubeManager.INSTANCE.addBatteryListener(this);
  }

  public void stop() {
    SmartCubeManager.INSTANCE.removeConnectionListener(this);
    SmartCubeManager.INSTANCE.removeBatteryListener(this);
  }

  @Override
  public void onConnection(CubeConnection connection) {
    refresh();
  }

  @Override
  public void onBattery(int level) {
    refresh();
  }

  private void refresh() {
    if (chip == null) {
      return;
    }
    ImageView icon = chip.findViewById(R.id.imgSmartCubeChip);
    OutlineTextView battery = chip.findViewById(R.id.tvSmartCubeChipBattery);
    boolean connected = SmartCubeManager.INSTANCE.isConnected();

    boolean visible = SmartCubeGate.ENABLED && !suppressed && (connected || !hideWhenDisconnected);
    chip.setVisibility(visible ? View.VISIBLE : View.GONE);
    if (item != null) {
      item.setVisible(visible);
    }

    icon.setColorFilter(ContextCompat.getColor(context, connected ? R.color.lightblue : R.color.white));
    icon.setAlpha(connected ? 1f : 0.5f);

    Integer level = SmartCubeManager.INSTANCE.getBattery();
    if (connected && level != null) {
      battery.setText(level + "%");
      battery.setVisibility(View.VISIBLE);
    } else {
      battery.setVisibility(View.GONE);
    }
  }
}
