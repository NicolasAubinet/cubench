package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;

/**
 * The two things a blindfolded reconstruction is named through, asked together: how the solver holds
 * the cube, and which pieces they shoot from.
 *
 * <p>One body, shown both as the once-only question at the first blind solve and as the settings row
 * that answers it again. The same popup either way, so there is no second version of it to keep in
 * step.
 */
public class BlindSetupPicker {

  private final View view;
  private final BlindOrientationPicker orientation;
  private final BlindBufferPicker buffers;

  /** @param asked whether this is the question itself, which alone says where to answer it again */
  public BlindSetupPicker(Context context, boolean asked) {
    view = LayoutInflater.from(context).inflate(R.layout.blind_setup_dialog, null);
    FrameLayout orientationHost = view.findViewById(R.id.orientationPicker);
    orientation = new BlindOrientationPicker(context, orientationHost,
        Options.INSTANCE.getBlindUpFace(), Options.INSTANCE.getBlindFrontFace());
    orientationHost.addView(orientation.getView());
    FrameLayout bufferHost = view.findViewById(R.id.bufferPicker);
    buffers = new BlindBufferPicker(context, bufferHost,
        Options.INSTANCE.getBlindEdgeBuffer(), Options.INSTANCE.getBlindCornerBuffer());
    bufferHost.addView(buffers.getView());
    view.findViewById(R.id.blindSetupChangeable).setVisibility(asked ? View.VISIBLE : View.GONE);
  }

  public View getView() {
    return view;
  }

  /** Both answers as they stand, for a dialog that was confirmed. */
  public void save() {
    orientation.save();
    buffers.save();
    Options.INSTANCE.setBlindSetupAsked(true);
  }

  /** Both answers in a line, for the settings row that opens this. */
  public static String inWords(Context context) {
    return context.getString(R.string.blind_setup_summary,
        BlindOrientationPicker.inWords(context,
            Options.INSTANCE.getBlindUpFace(), Options.INSTANCE.getBlindFrontFace()),
        Options.INSTANCE.getBlindEdgeBuffer(), Options.INSTANCE.getBlindCornerBuffer());
  }
}
