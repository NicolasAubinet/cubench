package com.cube.nanotimer.gui.widget;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.step.LastLayerAlgorithms;
import com.cube.nanotimer.util.view.ParticleView;
import com.cube.nanotimer.util.view.RaysView;
import com.cube.nanotimer.util.view.SetLearnedEntrance;

import java.util.ArrayList;
import java.util.List;

/**
 * The moment a coach learning drill completes the whole OLL or PLL set, shown once per set: every
 * case of it together, in the step's own colour, and what having all of them means. When it is
 * the OLL set, it stands in for the shape-group screen its last case would also have earned.
 */
public final class SetLearnedPanel {

  /** The set this panel celebrates. */
  public enum Family { OLL, PLL }

  private static final int OLL_COLUMNS = 10;
  private static final int PLL_COLUMNS = 7;

  private SetLearnedPanel() {
  }

  /** The set a case belongs to, or null for one of neither. */
  public static Family familyOf(String caseCode) {
    if (caseCode == null) {
      return null;
    }
    return caseCode.startsWith("oll_") ? Family.OLL
        : caseCode.startsWith("pll_") ? Family.PLL : null;
  }

  /** Every case of a set, in its order. */
  public static List<String> casesOf(Family family) {
    String prefix = family == Family.OLL ? "oll_" : "pll_";
    List<String> cases = new ArrayList<String>();
    for (String[] row : family == Family.OLL ? LastLayerAlgorithms.ORIENTATIONS
        : LastLayerAlgorithms.PERMUTATIONS) {
      cases.add(prefix + row[0]);
    }
    return cases;
  }

  /** Kept beside the celebrated shape groups, under a name no group has. */
  public static String celebratedKey(Family family) {
    return family == Family.OLL ? "set:oll" : "set:pll";
  }

  /**
   * @param cases every case of the set ({@code oll_7} or {@code pll_h} codes), in set order
   * @param bothSets whether the other last-layer set is complete too
   */
  public static void show(Activity activity, Family set, List<String> cases, boolean bothSets,
      final Runnable onClosed) {
    final ViewGroup root = activity.findViewById(android.R.id.content);
    final View panel = LayoutInflater.from(activity).inflate(R.layout.set_learned_panel, root,
        false);
    int accent = ContextCompat.getColor(activity,
        set == Family.OLL ? R.color.step_oll : R.color.step_pll);
    boolean oll = set == Family.OLL;

    View card = panel.findViewById(R.id.llSetLearnedCard);
    card.setBackground(cardBackground(activity, accent));
    panel.findViewById(R.id.vSetLearnedDisc).setBackground(disc(activity, accent));
    ((ImageView) panel.findViewById(R.id.ivSetLearnedTrophy))
        .setColorFilter(accent, PorterDuff.Mode.SRC_IN);
    RaysView rays = panel.findViewById(R.id.vSetLearnedRays);
    rays.setColor(accent);
    ((TextView) panel.findViewById(R.id.tvSetLearnedLabel)).setTextColor(accent);
    ((TextView) panel.findViewById(R.id.tvSetLearnedTitle)).setText(
        oll ? R.string.set_learned_title_oll : R.string.set_learned_title_pll);
    ((TextView) panel.findViewById(R.id.tvSetLearnedCount))
        .setText(activity.getString(R.string.set_learned_count, cases.size()));
    ((TextView) panel.findViewById(R.id.tvSetLearnedBody)).setText(
        oll ? R.string.set_learned_body_oll : R.string.set_learned_body_pll);
    if (bothSets) {
      TextView both = panel.findViewById(R.id.tvSetLearnedBoth);
      both.setTextColor(accent);
      both.setVisibility(View.VISIBLE);
    }
    List<View> charts = addCases(activity, (LinearLayout) panel.findViewById(
        R.id.llSetLearnedCases), cases, oll ? OLL_COLUMNS : PLL_COLUMNS);

    final ValueAnimator[] turn = new ValueAnimator[1];
    panel.findViewById(R.id.btSetLearnedContinue).setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            v.setEnabled(false); // a second tap would close it twice and skip a case
            SetLearnedEntrance.leave(panel, turn[0], new Runnable() {
              @Override
              public void run() {
                root.removeView(panel);
                if (onClosed != null) {
                  onClosed.run();
                }
              }
            });
          }
        });
    panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow(View v) {
      }

      @Override
      public void onViewDetachedFromWindow(View v) {
        if (turn[0] != null) {
          turn[0].cancel(); // the rays turn forever otherwise, with the screen gone
        }
      }
    });
    root.addView(panel);
    final View scroll = panel.findViewById(R.id.svSetLearned);
    scroll.post(new Runnable() {
      @Override
      public void run() {
        scroll.scrollTo(0, 0); // focus on the button would otherwise open it scrolled to the end
      }
    });
    turn[0] = SetLearnedEntrance.play(panel, card, rays,
        panel.findViewById(R.id.flSetLearnedBadge), charts,
        (ParticleView) panel.findViewById(R.id.vSetLearnedConfetti));
  }

  /** The dialog surface warmed from the top by the step colour, with an edge in it. */
  private static Drawable cardBackground(Activity activity, int accent) {
    float radius = 28 * activity.getResources().getDisplayMetrics().density;
    GradientDrawable base = new GradientDrawable();
    base.setColor(ContextCompat.getColor(activity, R.color.dialog_surface));
    base.setCornerRadius(radius);
    GradientDrawable warm = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        new int[] {withAlpha(accent, 0x47), withAlpha(accent, 0x0A), withAlpha(accent, 0)});
    warm.setCornerRadius(radius);
    warm.setStroke(Math.round(2 * activity.getResources().getDisplayMetrics().density),
        withAlpha(accent, 0x99));
    return new LayerDrawable(new Drawable[] {base, warm});
  }

  /** The trophy's disc: the step colour fading out, so it glows rather than sits. */
  private static Drawable disc(Activity activity, int accent) {
    GradientDrawable disc = new GradientDrawable();
    disc.setShape(GradientDrawable.OVAL);
    disc.setGradientType(GradientDrawable.RADIAL_GRADIENT);
    disc.setColors(new int[] {withAlpha(accent, 0x80), withAlpha(accent, 0x30),
        withAlpha(accent, 0)});
    disc.setGradientRadius(52 * activity.getResources().getDisplayMetrics().density);
    return disc;
  }

  private static int withAlpha(int color, int alpha) {
    return (alpha << 24) | (color & 0x00FFFFFF);
  }

  private static List<View> addCases(Activity activity, LinearLayout rows, List<String> cases,
      int columns) {
    int width = activity.getResources().getDisplayMetrics().widthPixels;
    float density = activity.getResources().getDisplayMetrics().density;
    int gap = Math.round(3 * density);
    // The card's margins and padding take 76dp of the screen's width.
    int size = Math.min(Math.round(44 * density),
        (width - Math.round(76 * density)) / columns - 2 * gap);
    List<View> charts = new ArrayList<View>();
    LinearLayout row = null;
    for (int i = 0; i < cases.size(); i++) {
      if (i % columns == 0) {
        row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        rows.addView(row, params);
      }
      LastLayerCaseView chart = new LastLayerCaseView(activity);
      chart.setDiagram(LastLayerDiagram.forCase(cases.get(i)));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
      params.setMargins(gap, gap, gap, gap);
      row.addView(chart, params);
      charts.add(chart);
    }
    return charts;
  }
}
