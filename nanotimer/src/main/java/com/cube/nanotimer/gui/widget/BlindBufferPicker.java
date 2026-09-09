package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import com.cube.nanotimer.Options;
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
  private String edge;
  private String corner;

  public BlindBufferPicker(Context context, ViewGroup parent, String edge, String corner) {
    this.edge = edge;
    this.corner = corner;
    LayoutInflater inflater = LayoutInflater.from(context);
    String[] edges = context.getResources().getStringArray(R.array.entryvalues_edge_buffer);
    String[] corners = context.getResources().getStringArray(R.array.entryvalues_corner_buffer);
    view = inflater.inflate(R.layout.blind_buffers_wheels, parent, false);
    wheel((NumberPicker) view.findViewById(R.id.edgeBuffers), edges, true);
    wheel((NumberPicker) view.findViewById(R.id.cornerBuffers), corners, false);
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
  private void wheel(NumberPicker picker, final String[] buffers, final boolean edges) {
    picker.setMinValue(0);
    picker.setMaxValue(buffers.length - 1);
    picker.setDisplayedValues(buffers);
    // A stored buffer no list of ours holds is not one the wheel can show, so the wheel's own first
    // value takes over: confirming the dialog must write what it is showing.
    int at = indexOf(buffers, edges ? edge : corner);
    if (at < 0) {
      at = 0;
      if (edges) {
        edge = buffers[0];
      } else {
        corner = buffers[0];
      }
    }
    picker.setValue(at);
    picker.setWrapSelectorWheel(true);
    // Or the middle cell is an EditText and tapping it raises the keyboard over the wheel.
    picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
    picker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
      @Override
      public void onValueChange(NumberPicker changed, int was, int now) {
        if (edges) {
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

  /** The buffers as they stand, for a picker whose dialog was confirmed. */
  public void save() {
    Options.INSTANCE.setBlindBuffers(edge, corner);
  }
}
