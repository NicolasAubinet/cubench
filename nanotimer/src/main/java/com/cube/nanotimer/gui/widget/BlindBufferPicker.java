package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import com.cube.nanotimer.R;

/**
 * The two pieces a blind solver shoots from, picked one per type.
 *
 * <p>Shared by the settings row and by the once-only question the timer screen asks, so the two are
 * the same thing seen twice rather than two pickers to keep in step.
 *
 * <p>They are worth asking for because a blind reconstruction is named through them: which slot each
 * algorithm was shot from is read off the cube, and the buffer is what turns that into the frame the
 * solver was holding it in. See {@code BlindFrame}.
 *
 * <p>Two wheels rather than a grid of every slot, which was drawn first: twenty chips is the whole
 * of a dialog's height for a question with an answer most solvers never change.
 */
public class BlindBufferPicker {

  private final View view;
  private final NumberPicker edgeWheel;
  private final NumberPicker cornerWheel;
  private final String[] edges;
  private final String[] corners;
  private String edge;
  private String corner;

  public BlindBufferPicker(Context context, ViewGroup parent, String edge, String corner) {
    LayoutInflater inflater = LayoutInflater.from(context);
    edges = context.getResources().getStringArray(R.array.entryvalues_edge_buffer);
    corners = context.getResources().getStringArray(R.array.entryvalues_corner_buffer);
    view = inflater.inflate(R.layout.blind_buffers_wheels, parent, false);
    edgeWheel = (NumberPicker) view.findViewById(R.id.edgeBuffers);
    cornerWheel = (NumberPicker) view.findViewById(R.id.cornerBuffers);
    wheel(edgeWheel, edges, true);
    wheel(cornerWheel, corners, false);
    show(edge, corner);
  }

  /**
   * Turns the wheels to these buffers. A stored buffer no list of ours holds is not one a wheel can
   * show, so its first value takes over: confirming the dialog must write what it is showing.
   */
  public void show(String edge, String corner) {
    this.edge = edges[Math.max(0, indexOf(edges, edge))];
    this.corner = corners[Math.max(0, indexOf(corners, corner))];
    edgeWheel.setValue(indexOf(edges, this.edge));
    cornerWheel.setValue(indexOf(corners, this.corner));
  }

  public View getView() {
    return view;
  }

  public String getEdge() {
    return edge;
  }

  public String getCorner() {
    return corner;
  }

  /** One type as a wheel, wrapping round so the far end is a flick away rather than a scroll. */
  private void wheel(NumberPicker picker, final String[] buffers, final boolean forEdges) {
    picker.setMinValue(0);
    picker.setMaxValue(buffers.length - 1);
    picker.setDisplayedValues(buffers);
    picker.setWrapSelectorWheel(true);
    // Or the middle cell is an EditText and tapping it raises the keyboard over the wheel.
    picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
    picker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
      @Override
      public void onValueChange(NumberPicker changed, int was, int now) {
        if (forEdges) {
          edge = buffers[now];
        } else {
          corner = buffers[now];
        }
      }
    });
  }

  private static int indexOf(String[] buffers, String buffer) {
    for (int i = 0; i < buffers.length; i++) {
      if (buffers[i].equals(buffer)) {
        return i;
      }
    }
    return -1;
  }
}
