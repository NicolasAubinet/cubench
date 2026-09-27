package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SolveTypeMethod;
import com.cube.nanotimer.vo.BlindMethod;
import com.cube.nanotimer.vo.SolveType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * The things a blindfolded reconstruction is named through, asked together: how the solver holds the
 * cube, how they shoot their targets, and which pieces they shoot from.
 *
 * <p>One body, shown both as the once-only question at the first blind solve and as the settings row
 * that answers it again. The same popup either way, so there is no second version of it to keep in
 * step.
 *
 * <p><b>Each blind method keeps its own buffers</b>, since the frame is settled from them and an
 * Old Pochmann solve read against 3-style buffers is spelled in the wrong grip. Picking a method
 * turns the wheels to its buffers, which is also what suggests the usual ones to someone new to it.
 *
 * <p>Opened for a solve type, it starts on that type's method, and a method changed here is kept
 * where the type's came from: its own override, or the preference it follows.
 */
public class BlindSetupPicker {

  private static final BlindMethod[] METHODS = BlindMethod.values();

  private final View view;
  private final BlindOrientationPicker orientation;
  private final BlindBufferPicker buffers;
  // Each method's buffers as picked so far, so switching back and forth loses nothing.
  private final Map<BlindMethod, String[]> buffersOf = new EnumMap<>(BlindMethod.class);
  // As they were read, so an untouched method keeps following the default rather than freezing it.
  private final Map<BlindMethod, String[]> loaded = new EnumMap<>(BlindMethod.class);
  private final SolveType solveType;
  private BlindMethod method;

  /**
   * @param asked whether this is the question itself, which alone says where to answer it again
   * @param solveType the blind type it was opened for, or null from the settings
   */
  public BlindSetupPicker(Context context, boolean asked, SolveType solveType) {
    this.solveType = solveType;
    view = LayoutInflater.from(context).inflate(R.layout.blind_setup_dialog, null);
    FrameLayout orientationHost = view.findViewById(R.id.orientationPicker);
    orientation = new BlindOrientationPicker(context, orientationHost,
        Options.INSTANCE.getBlindUpFace(), Options.INSTANCE.getBlindFrontFace());
    orientationHost.addView(orientation.getView());
    for (BlindMethod each : METHODS) {
      loaded.put(each, new String[] {Options.INSTANCE.getBlindEdgeBuffer(each),
          Options.INSTANCE.getBlindCornerBuffer(each)});
      buffersOf.put(each, loaded.get(each).clone());
    }
    method = solveType == null ? Options.INSTANCE.getPreferredBlindMethod()
        : SolveTypeMethod.blindMethodOf(solveType);
    FrameLayout bufferHost = view.findViewById(R.id.bufferPicker);
    buffers = new BlindBufferPicker(context, bufferHost, buffersOf.get(method)[0],
        buffersOf.get(method)[1]);
    bufferHost.addView(buffers.getView());
    initMethods(context);
    // The settings row changes the preference, which a type with its own method does not follow.
    boolean ownMethod = solveType != null && solveType.getBlindMethodOverride() != null;
    view.findViewById(R.id.blindSetupChangeable)
        .setVisibility(asked && !ownMethod ? View.VISIBLE : View.GONE);
  }

  private void initMethods(Context context) {
    String[] labels = new String[METHODS.length];
    for (int i = 0; i < METHODS.length; i++) {
      labels[i] = context.getString(SolveTypeMethod.nameOf(METHODS[i]));
    }
    SegmentedControl control = new SegmentedControl(context,
        (LinearLayout) view.findViewById(R.id.blindMethodPicker), labels,
        new SegmentedControl.Listener() {
          @Override
          public void onSegmentPicked(int index) {
            keepBuffers();
            method = METHODS[index];
            buffers.show(buffersOf.get(method)[0], buffersOf.get(method)[1]);
          }
        });
    control.setSelection(method.ordinal());
  }

  private void keepBuffers() {
    buffersOf.put(method, new String[] {buffers.getEdge(), buffers.getCorner()});
  }

  /** The way out for a reading that is wrong with both of these right. Hidden where nothing offers it. */
  public void onReport(final Runnable report) {
    View link = view.findViewById(R.id.blindSetupReport);
    if (report == null) {
      link.setVisibility(View.GONE);
      return;
    }
    link.setVisibility(View.VISIBLE);
    link.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View clicked) {
        report.run();
      }
    });
  }

  public View getView() {
    return view;
  }

  /** Every answer as it stands, for a dialog that was confirmed. */
  public void save() {
    orientation.save();
    keepBuffers();
    for (BlindMethod each : METHODS) {
      String[] picked = buffersOf.get(each);
      if (!Arrays.equals(picked, loaded.get(each))) {
        Options.INSTANCE.setBlindBuffers(each, picked[0], picked[1]);
      }
    }
    saveMethod();
    Options.INSTANCE.setBlindSetupAsked(true);
  }

  private void saveMethod() {
    BlindMethod override = solveType == null ? null : solveType.getBlindMethodOverride();
    if (override == null) {
      Options.INSTANCE.setPreferredBlindMethod(method);
    } else if (override != method) {
      solveType.setBlindMethod(method); // what the caller reads again through, right away
      App.INSTANCE.getService().updateSolveTypeBlindMethod(solveType, null);
    }
  }

  /** Every answer in a line, for the settings row that opens this. */
  public static String inWords(Context context) {
    BlindMethod method = Options.INSTANCE.getPreferredBlindMethod();
    return context.getString(R.string.blind_setup_summary,
        context.getString(SolveTypeMethod.nameOf(method)),
        BlindOrientationPicker.inWords(context,
            Options.INSTANCE.getBlindUpFace(), Options.INSTANCE.getBlindFrontFace()),
        Options.INSTANCE.getBlindEdgeBuffer(method), Options.INSTANCE.getBlindCornerBuffer(method));
  }
}
