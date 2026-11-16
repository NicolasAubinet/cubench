package com.cube.nanotimer.gui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cases.CaseSet;
import com.cube.nanotimer.cases.CaseSets;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.view.DrillSplitBarView;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.vo.CaseHistory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The algorithm library: every set the app knows, as cards, a method's sets together,
 * each opening its set. For everyone, cube or none: a timer-only solver reads it as a reference
 * and sees how many algorithms they have picked; a smart cube adds how far through each set they
 * are, from the same case knowledge Analysis reads.
 */
public class LibraryActivity extends NanoTimerActivity {

  /** How strongly a card is washed in its set's colour, at its left edge. */
  private static final float CARD_WASH = 0.16f;

  private LinearLayout library;
  private final Map<String, CaseKnowledge.Status> statuses =
      new LinkedHashMap<String, CaseKnowledge.Status>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.library);
    setTitle(R.string.menu_algorithms);
    library = findViewById(R.id.llLibrary);
    show();
  }

  /** Read again on the way back: a case may have been starred or drilled meanwhile. */
  @Override
  protected void onResume() {
    super.onResume();
    App.INSTANCE.getService().getCaseHistory(0, new DataCallback<CaseHistory>() {
      @Override
      public void onData(final CaseHistory history) {
        runOnUiThread(() -> {
          statuses.clear();
          for (CaseKnowledge known : history.getCases()) {
            statuses.put(known.getCode(), known.getStatus());
          }
          show();
        });
      }
    });
  }

  private void show() {
    // The cards sit above the two notes the layout ends on.
    while (library.getChildCount() > 2) {
      library.removeViewAt(0);
    }
    LayoutInflater inflater = LayoutInflater.from(this);
    List<String> methods = new ArrayList<String>();
    for (CaseSet set : CaseSets.all()) {
      if (!methods.contains(set.method())) {
        methods.add(set.method());
      }
    }
    int at = 0;
    boolean anything = false;
    // A method's sets stay together, with no header: the cards name themselves.
    for (String method : methods) {
      for (CaseSet set : CaseSets.all()) {
        if (set.method().equals(method)) {
          View card = inflater.inflate(R.layout.library_set_card, library, false);
          anything |= fill(card, set);
          library.addView(card, at++);
        }
      }
    }
    findViewById(R.id.tvLibraryHint).setVisibility(anything ? View.GONE : View.VISIBLE);
  }

  /** @return whether the solver has anything in the set, known or picked */
  private boolean fill(View card, final CaseSet set) {
    int hue = StepPalette.cfop(this).colorFor(set.id() + "_");
    GradientDrawable ground = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
        new int[] {ColorUtils.blendARGB(ContextCompat.getColor(this, R.color.case_alg_row), hue,
            CARD_WASH), ContextCompat.getColor(this, R.color.case_alg_row)});
    ground.setCornerRadius(16 * getResources().getDisplayMetrics().density);
    card.setBackground(ground);
    card.setOnClickListener(v -> {
      Intent intent = new Intent(this, CaseSetActivity.class);
      intent.putExtra(CaseSetActivity.EXTRA_SET, set.id());
      startActivity(intent);
    });

    ((LastLayerCaseView) card.findViewById(R.id.vLibrarySetCover))
        .setDiagram(LastLayerDiagram.forCase(set.coverCase()));
    ((TextView) card.findViewById(R.id.tvLibrarySetName)).setText(set.title());
    int size = set.cases().size();
    ((TextView) card.findViewById(R.id.tvLibrarySetCount))
        .setText(getResources().getQuantityString(R.plurals.case_set_cases, size, size));
    ((TextView) card.findViewById(R.id.tvLibrarySetDescription)).setText(set.descriptionRes());

    int known = 0;
    int review = 0;
    int learn = 0;
    int picked = 0;
    for (String code : set.cases()) {
      CaseKnowledge.Status status = statuses.get(code);
      known += status == CaseKnowledge.Status.KNOWN ? 1 : 0;
      review += status == CaseKnowledge.Status.NEEDS_REVIEW ? 1 : 0;
      learn += status == CaseKnowledge.Status.TO_LEARN ? 1 : 0;
      picked += Options.INSTANCE.getCaseAlgorithm(code) != null ? 1 : 0;
    }
    DrillSplitBarView bar = card.findViewById(R.id.vLibrarySetProgress);
    TextView progress = card.findViewById(R.id.tvLibrarySetProgress);
    List<String> words = new ArrayList<String>();
    if (known + review + learn > 0) {
      // Where the solver stands in the set, in the knowledge ring's shades.
      bar.setSegments(new long[] {known, review, learn, size - known - review - learn},
          new int[] {color(R.color.case_status_known), color(R.color.case_status_review),
              color(R.color.case_status_learn), color(R.color.case_status_unseen)});
      words.add(getString(R.string.library_known, known));
      if (review > 0) {
        words.add(getString(R.string.library_review, review));
      }
      if (learn > 0) {
        words.add(getString(R.string.library_to_learn, learn));
      }
    } else {
      // No smart cube has read the set: how many of its algorithms the solver has picked.
      bar.setSegments(new long[] {picked, size - picked},
          new int[] {color(R.color.case_status_known), color(R.color.case_status_unseen)});
      words.add(getString(R.string.library_picked, picked));
    }
    progress.setText(TextUtils.join("  ·  ", words));
    return known + review + learn + picked > 0;
  }

  private int color(int colorResId) {
    return ContextCompat.getColor(this, colorResId);
  }
}
