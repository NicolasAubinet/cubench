package com.cube.nanotimer.gui.widget.cases;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.cases.CaseSet;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.PairCaseView;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One case of a set as a tile: its picture over its name, and its shape where the set gives one.
 * An F2L case has no name to give, so its tile is the 3D pair alone.
 * The grid of the drill picker and the strip of drill setup draw it; the library will.
 *
 * <p>Filled the way {@code CaseRow} is: a caller sets the parts its screen has, the others stay as
 * the layout leaves them. Kept on the view's tag, so a recycled grid cell finds its tile again.
 */
public final class CaseTile {

  /** Pictures are worked out from the case's algorithm, so a grid redrawn on scroll reuses them. */
  private static final Map<String, LastLayerDiagram> PICTURES =
      new ConcurrentHashMap<String, LastLayerDiagram>();
  private static final Map<String, String> PAIRS = new ConcurrentHashMap<String, String>();

  private final View view;
  private final LastLayerCaseView chart;
  /** The 3D pair an F2L case is drawn as, in layouts that hold one; null in the others. */
  private final PairCaseView pair;
  private final TextView name;
  private final TextView shape;
  private final TextView more;
  private boolean picked = true;

  private CaseTile(View view) {
    this.view = view;
    chart = view.findViewById(R.id.vCaseTileChart);
    pair = view.findViewById(R.id.vCaseTilePair);
    name = view.findViewById(R.id.tvCaseTileName);
    shape = view.findViewById(R.id.tvCaseTileShape);
    more = view.findViewById(R.id.tvCaseTileMore);
    view.setTag(R.id.tvCaseTileName, this);
  }

  /** A grid tile, with the shape line. */
  public static CaseTile inflate(LayoutInflater inflater, ViewGroup parent) {
    return new CaseTile(inflater.inflate(R.layout.case_tile, parent, false));
  }

  /**
   * A tile in a layout of its own size ({@code case_tile_small} for a strip), holding the same ids;
   * a part it leaves out is simply not drawn.
   */
  public static CaseTile inflate(LayoutInflater inflater, ViewGroup parent, int layoutResId) {
    return new CaseTile(inflater.inflate(layoutResId, parent, false));
  }

  /** The tile a recycled view holds, or a new one for a view that is not a tile yet. */
  public static CaseTile of(View view, LayoutInflater inflater, ViewGroup parent) {
    Object tile = view == null ? null : view.getTag(R.id.tvCaseTileName);
    return tile instanceof CaseTile ? (CaseTile) tile : inflate(inflater, parent);
  }

  public View view() {
    return view;
  }

  public CaseTile show(CaseSet set, String code) {
    boolean inPair = pair != null && set.picture() == CaseSet.Picture.PAIR;
    chart.setVisibility(inPair ? View.GONE : View.VISIBLE);
    if (inPair) {
      pair.setVisibility(View.VISIBLE);
      pair.setPicture(pairPicture(code));
    } else {
      if (pair != null) {
        pair.setVisibility(View.GONE);
      }
      chart.setDiagram(picture(code));
    }
    if (more != null) {
      more.setVisibility(View.GONE);
    }
    name(set.name(code));
    if (shape != null) {
      String shapeName = set.shape(code);
      shape.setText(shapeName == null ? "" : shapeName);
      // A PLL has no shape, and an empty line would still take its row on every one of the 21.
      shape.setVisibility(shapeName == null ? View.GONE : View.VISIBLE);
    }
    return this;
  }

  private static LastLayerDiagram picture(String code) {
    LastLayerDiagram picture = PICTURES.get(code);
    if (picture == null) {
      picture = LastLayerDiagram.forCase(code);
      if (picture != null) {
        PICTURES.put(code, picture);
      }
    }
    return picture;
  }

  private static String pairPicture(String code) {
    String picture = PAIRS.get(code);
    if (picture == null) {
      picture = CaseAlgorithms.pairPicture(code);
      if (picture != null) {
        PAIRS.put(code, picture);
      }
    }
    return picture;
  }

  /** What the name line says, in place of the set's name for the case. */
  public CaseTile name(CharSequence text) {
    name.setText(text);
    name.setVisibility(text == null ? View.GONE : View.VISIBLE);
    return this;
  }

  /**
   * An unpicked case is turned down rather than hidden: it still has to be findable to be picked
   * again. The chart dims itself; the name and shape go to their own off colours.
   */
  public CaseTile picked(boolean on) {
    picked = on;
    chart.setDimmed(!on);
    if (pair != null) {
      pair.setDimmed(!on);
    }
    name.setTextColor(ContextCompat.getColor(view.getContext(),
        on ? R.color.case_name : R.color.case_name_off));
    if (shape != null) {
      shape.setTextColor(ContextCompat.getColor(view.getContext(),
          on ? R.color.case_shape : R.color.case_shape_off));
    }
    return this;
  }

  public boolean isPicked() {
    return picked;
  }

  /** A strip's last slot: how many more cases there is no room for, in place of a case. */
  public CaseTile more(CharSequence count) {
    chart.setVisibility(View.INVISIBLE);
    if (pair != null) {
      pair.setVisibility(View.GONE);
    }
    name.setVisibility(View.INVISIBLE);
    more.setVisibility(View.VISIBLE);
    more.setText(count);
    return this;
  }
}
