package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.helper.Utils;

/**
 * The orientation a blind solver holds the cube in, picked as two colours: the one they hold up and
 * the one they hold in front.
 *
 * <p>Shared by the settings row and by the once-only question the timer screen asks, as the buffer
 * picker beside it is, so the two are the same thing seen twice rather than two pickers to keep in
 * step.
 *
 * <p>It is worth asking for because a blind solve that stops before its second piece type settles
 * only half the frame its names are spelled in, and no cube state tells the rest apart. A
 * declaration does, outright: the cube writes its state against its own centres, so each of its
 * faces is a fixed colour and naming two of them names one orientation of the 24. See
 * {@code BlindFrame}.
 *
 * <p>Colours and not the cube's own letters, which is what a solver sees when they pick it up. Not
 * {@link CrossFaceSwatches}, which answers with a face letter and has every face to offer: here the
 * letter is the thing being hidden, and the two faces on the up axis are not front and cannot be
 * offered. The colour table underneath is the shared one all the same.
 */
public class BlindOrientationPicker {

  /** The cube's own faces, in the order the chips are laid out. */
  private static final char[] FACES = {'U', 'R', 'F', 'D', 'L', 'B'};

  private final View view;
  private final TextView said;
  private final LinearLayout upRow;
  private final LinearLayout frontRow;
  private char up;
  private char front;

  public BlindOrientationPicker(Context context, ViewGroup parent, String upFace, String frontFace) {
    up = upFace.charAt(0);
    front = frontFace.charAt(0);
    view = LayoutInflater.from(context).inflate(R.layout.blind_orientation_faces, parent, false);
    said = view.findViewById(R.id.orientationSaid);
    upRow = view.findViewById(R.id.upFaces);
    frontRow = view.findViewById(R.id.frontFaces);
    fill(upRow, true);
    fill(frontRow, false);
    refresh();
  }

  public View getView() {
    return view;
  }

  public String getUpFace() {
    return String.valueOf(up);
  }

  public String getFrontFace() {
    return String.valueOf(front);
  }

  /** The orientation as it stands, for a picker whose dialog was confirmed. */
  public void save() {
    Options.INSTANCE.setBlindOrientation(getUpFace(), getFrontFace());
  }

  /** The declared pair in words, which is what the settings row shows and the chips repeat. */
  public static String inWords(Context context, String upFace, String frontFace) {
    return context.getString(R.string.blind_orientation_summary,
        context.getString(Utils.getFaceColourNameRes(upFace.charAt(0))),
        context.getString(Utils.getFaceColourNameRes(frontFace.charAt(0))));
  }

  private void fill(LinearLayout row, final boolean picksUp) {
    Context context = row.getContext();
    for (int i = 0; i < FACES.length; i++) {
      final char face = FACES[i];
      View chip = new View(context);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(context, 40), 1f);
      if (i > 0) {
        lp.leftMargin = dp(context, 6);
      }
      chip.setLayoutParams(lp);
      chip.setContentDescription(context.getString(Utils.getFaceColourNameRes(face)));
      chip.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View clicked) {
          pick(face, picksUp);
        }
      });
      row.addView(chip);
    }
  }

  /** A face the up row takes carries the front one with it, since the two cannot share an axis. */
  private void pick(char face, boolean picksUp) {
    if (picksUp) {
      up = face;
      if (!canFront(front)) {
        front = firstFront();
      }
    } else if (canFront(face)) {
      front = face;
    } else {
      return;
    }
    refresh();
  }

  private void refresh() {
    for (int i = 0; i < FACES.length; i++) {
      paint(upRow.getChildAt(i), FACES[i], FACES[i] == up, true);
      paint(frontRow.getChildAt(i), FACES[i], FACES[i] == front, canFront(FACES[i]));
    }
    said.setText(inWords(said.getContext(), getUpFace(), getFrontFace()));
  }

  /**
   * Every face in its own colour, the border alone saying which is picked: a dimmed yellow or white
   * stops reading as that colour at all. The two the up face rules out are the exception, where
   * saying nothing at all is what has to come across.
   */
  private void paint(View chip, char face, boolean picked, boolean offered) {
    Context context = chip.getContext();
    GradientDrawable fill = new GradientDrawable();
    fill.setShape(GradientDrawable.RECTANGLE);
    fill.setCornerRadius(dp(context, 8));
    fill.setColor(ContextCompat.getColor(context, Utils.getFaceColorRes(face)));
    fill.setStroke(dp(context, picked ? 5 : 1),
        ContextCompat.getColor(context, picked ? R.color.lightblue : R.color.gray700));
    chip.setBackground(fill);
    chip.setAlpha(offered ? 1f : 0.22f);
    chip.setClickable(offered);
  }

  /** A face is front unless it stands on the up face's axis, which is it and the one beneath it. */
  private boolean canFront(char face) {
    return axis(face) != axis(up);
  }

  private char firstFront() {
    for (char face : FACES) {
      if (canFront(face)) {
        return face;
      }
    }
    return Options.DEFAULT_FRONT_FACE.charAt(0);
  }

  /** Which of the three axes a face is on: {@link #FACES} lists each face three before its own. */
  private static int axis(char face) {
    return new String(FACES).indexOf(face) % 3;
  }

  private static int dp(Context context, int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}
