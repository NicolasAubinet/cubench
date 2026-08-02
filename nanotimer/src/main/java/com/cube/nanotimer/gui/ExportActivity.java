package com.cube.nanotimer.gui;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.services.Service;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.exportimport.csvexport.CSVGenerator;
import com.cube.nanotimer.util.exportimport.csvexport.ExportCSVGenerator;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.FileUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.FlowLayout;
import com.cube.nanotimer.util.view.PuzzleIcons;
import com.cube.nanotimer.util.view.SolveTypeIcons;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ExportResult;
import com.cube.nanotimer.vo.SolveType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picks what leaves the app. A puzzle is a card that takes or drops everything under it at once,
 * and its solve types are pills that can be taken one by one, so the two ways of saying "all of
 * this" are the same control at two levels.
 */
public class ExportActivity extends NanoTimerActivity {

  private static final String EXPORT_FILE_NAME = "export.csv";
  private static final String EXPORT_MIME_TYPE = "text/csv";
  private static final int REQ_CREATE_DOCUMENT = 1;
  /** What you picked is what you get: nothing on this screen caps a solve type. */
  private static final int NO_LIMIT = -1;

  private static final float BOX_RADIUS_DP = 6f;
  private static final float TILE_RADIUS_DP = 10f;
  private static final float PILL_RADIUS_DP = 17f;
  private static final float STROKE_DP = 1.5f;
  private static final int WASH_ALPHA = 0x2E;
  private static final int OUTLINE_ALPHA = 0x8A;
  private static final int UNSELECTED_OUTLINE = 0x30FFFFFF;
  private static final int EMPTY_PUZZLE_ALPHA = 0x99;

  private final List<PuzzleGroup> groups = new ArrayList<PuzzleGroup>();
  private boolean loaded;
  private boolean emptyPuzzlesShown;
  private int pendingSolveTypes;
  private float density;

  private ScrollView scrollView;
  private LinearLayout puzzleCards;
  private TextView tvSelection;
  private TextView tvSelectAll;
  private TextView tvEmptyPuzzles;
  private FlowLayout emptyPuzzlePills;

  // CSV waiting to be copied to the destination picked by the system document picker
  private File pendingSaveFile;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    density = getResources().getDisplayMetrics().density;
    setContentView(R.layout.export_screen);

    setTitle(R.string.export_times);
    initViews();
    loadData();
  }

  private void initViews() {
    scrollView = (ScrollView) findViewById(R.id.scrollView);
    puzzleCards = (LinearLayout) findViewById(R.id.puzzleCards);
    tvSelection = (TextView) findViewById(R.id.tvSelection);
    tvSelectAll = (TextView) findViewById(R.id.tvSelectAll);
    tvEmptyPuzzles = (TextView) findViewById(R.id.tvEmptyPuzzles);
    emptyPuzzlePills = (FlowLayout) findViewById(R.id.emptyPuzzlePills);

    tvSelectAll.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        setAllSelected(!isEverythingSelected());
      }
    });

    tvEmptyPuzzles.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        emptyPuzzlesShown = !emptyPuzzlesShown;
        refreshEmptyPuzzles();
        if (emptyPuzzlesShown) {
          // What was just unfolded is below the fold, so the screen goes to it.
          scrollView.post(new Runnable() {
            @Override
            public void run() {
              scrollView.fullScroll(View.FOCUS_DOWN);
            }
          });
        }
      }
    });

    Button buExport = (Button) findViewById(R.id.buExport);
    buExport.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        export(true);
      }
    });

    Button buSaveToFile = (Button) findViewById(R.id.buSaveToFile);
    buSaveToFile.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        export(false);
      }
    });

    if (loaded) {
      buildCards();
    } else {
      refreshSelection();
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    setContentView(R.layout.export_screen);
    initViews();
  }

  /**
   * The puzzles, then their solve types with the solves each holds. A puzzle's total is the sum of
   * its solve types', so the counts on a card always add up to the one on its head.
   */
  private void loadData() {
    final Service service = App.INSTANCE.getService();
    service.getCubeTypes(false, new DataCallback<List<CubeType>>() {
      @Override
      public void onData(final List<CubeType> cubeTypes) {
        service.getSolvesCountPerSolveType(new DataCallback<Map<Integer, Integer>>() {
          @Override
          public void onData(final Map<Integer, Integer> counts) {
            runOnUiThread(new Runnable() {
              @Override
              public void run() {
                loadSolveTypes(cubeTypes, counts);
              }
            });
          }
        });
      }
    });
  }

  private void loadSolveTypes(List<CubeType> cubeTypes, final Map<Integer, Integer> counts) {
    groups.clear();
    for (CubeType cubeType : cubeTypes) {
      groups.add(new PuzzleGroup(cubeType));
    }
    pendingSolveTypes = groups.size();
    if (pendingSolveTypes == 0) {
      loaded = true;
      buildCards();
      return;
    }
    for (final PuzzleGroup group : groups) {
      App.INSTANCE.getService().getSolveTypes(group.cubeType, new DataCallback<List<SolveType>>() {
        @Override
        public void onData(final List<SolveType> solveTypes) {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              fillGroup(group, solveTypes, counts);
              if (--pendingSolveTypes == 0) {
                loaded = true;
                buildCards();
              }
            }
          });
        }
      });
    }
  }

  /** Solve types with nothing in them are left out: they would export an empty selection. */
  private void fillGroup(PuzzleGroup group, List<SolveType> solveTypes, Map<Integer, Integer> counts) {
    if (solveTypes == null) {
      return;
    }
    for (SolveType solveType : solveTypes) {
      Integer count = counts.get(solveType.getId());
      if (count != null && count > 0) {
        group.items.add(new SolveTypeItem(solveType,
          Utils.toSolveTypeLocalizedName(this, solveType.getName()), count));
        group.total += count;
      }
    }
  }

  private void buildCards() {
    puzzleCards.removeAllViews();
    for (PuzzleGroup group : groups) {
      if (group.total > 0) {
        puzzleCards.addView(buildCard(group));
      }
    }
    refreshEmptyPuzzles();
    refreshSelection();
  }

  private View buildCard(final PuzzleGroup group) {
    View card = getLayoutInflater().inflate(R.layout.export_puzzle_card, puzzleCards, false);
    int color = ContextCompat.getColor(this, PuzzleIcons.colorForCubeType(group.cubeType));

    ImageView imgPuzzle = (ImageView) card.findViewById(R.id.imgPuzzle);
    imgPuzzle.setImageResource(PuzzleIcons.forCubeType(group.cubeType));
    imgPuzzle.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    card.findViewById(R.id.puzzleGlyphTile).setBackground(rounded(withAlpha(color, WASH_ALPHA), TILE_RADIUS_DP));

    ((TextView) card.findViewById(R.id.tvPuzzleName)).setText(group.cubeType.getName());
    card.findViewById(R.id.puzzleHeadRow).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        setGroupSelected(group, !group.allSelected());
      }
    });

    group.box = (ImageView) card.findViewById(R.id.imgSelectionBox);
    group.tvCount = (TextView) card.findViewById(R.id.tvPuzzleCount);

    FlowLayout pills = (FlowLayout) card.findViewById(R.id.solveTypePills);
    for (final SolveTypeItem item : group.items) {
      View pill = buildPill(pills, item.name, String.valueOf(item.count),
        SolveTypeIcons.forSolveType(item.solveType),
        ContextCompat.getColor(this, SolveTypeIcons.colorForSolveType(item.solveType)));
      pill.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View view) {
          item.selected = !item.selected;
          refreshGroup(group);
          refreshSelection();
        }
      });
      item.pill = pill;
      pills.addView(pill);
    }

    refreshGroup(group);
    return card;
  }

  /** @param count what the pill holds, null for one that holds nothing */
  private View buildPill(FlowLayout parent, String name, String count, int iconRes, int color) {
    View pill = getLayoutInflater().inflate(R.layout.export_solvetype_pill, parent, false);
    ImageView icon = (ImageView) pill.findViewById(R.id.imgSolveTypeKind);
    icon.setImageResource(iconRes);
    icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    ((TextView) pill.findViewById(R.id.tvSolveTypeName)).setText(name);
    TextView tvCount = (TextView) pill.findViewById(R.id.tvSolveTypeCount);
    if (count == null) {
      tvCount.setVisibility(View.GONE);
    } else {
      tvCount.setText(count);
    }
    return pill;
  }

  private void refreshGroup(PuzzleGroup group) {
    int color = ContextCompat.getColor(this, PuzzleIcons.colorForCubeType(group.cubeType));
    int selected = group.selectedSolves();

    group.box.setBackground(boxBackground(color, selected, group.total));
    if (selected == 0) {
      group.box.setImageDrawable(null);
    } else {
      group.box.setImageResource(selected == group.total ? R.drawable.ic_box_check : R.drawable.ic_box_dash);
      group.box.setColorFilter(selected == group.total
        ? ContextCompat.getColor(this, R.color.on_accent) : color, PorterDuff.Mode.SRC_IN);
    }

    group.tvCount.setText(selected > 0 && selected < group.total
      ? getString(R.string.export_partial_count, selected, group.total)
      : String.valueOf(group.total));

    for (SolveTypeItem item : group.items) {
      int pillColor = ContextCompat.getColor(this, SolveTypeIcons.colorForSolveType(item.solveType));
      item.pill.setBackground(pillBackground(pillColor, item.selected));
      ImageView icon = (ImageView) item.pill.findViewById(R.id.imgSolveTypeKind);
      icon.setColorFilter(item.selected ? pillColor : ContextCompat.getColor(this, R.color.gray600),
        PorterDuff.Mode.SRC_IN);
      ((TextView) item.pill.findViewById(R.id.tvSolveTypeName)).setTextColor(ContextCompat.getColor(this,
        item.selected ? R.color.white : R.color.secondary_text));
      ((TextView) item.pill.findViewById(R.id.tvSolveTypeCount)).setTextColor(ContextCompat.getColor(this,
        item.selected ? R.color.secondary_text : R.color.gray600));
    }
  }

  /** The running total, and the action that takes or drops the lot. */
  private void refreshSelection() {
    int solves = 0;
    int types = 0;
    int total = 0;
    for (PuzzleGroup group : groups) {
      total += group.total;
      for (SolveTypeItem item : group.items) {
        if (item.selected) {
          solves += item.count;
          types++;
        }
      }
    }

    tvSelection.setText(solves == 0 ? getString(R.string.export_nothing_selected)
      : getString(R.string.export_selection,
        getResources().getQuantityString(R.plurals.export_solves_count, solves, solves),
        getResources().getQuantityString(R.plurals.export_types_count, types, types)));

    // Only once the cards it acts on are on screen: mid-load there is nothing for it to tick.
    tvSelectAll.setVisibility(loaded && total > 0 ? View.VISIBLE : View.GONE);
    tvSelectAll.setText(isEverythingSelected() ? R.string.export_clear : R.string.select_all);
  }

  private void refreshEmptyPuzzles() {
    List<PuzzleGroup> empty = new ArrayList<PuzzleGroup>();
    for (PuzzleGroup group : groups) {
      if (group.total == 0) {
        empty.add(group);
      }
    }
    if (empty.isEmpty()) {
      tvEmptyPuzzles.setVisibility(View.GONE);
      emptyPuzzlePills.setVisibility(View.GONE);
      return;
    }

    tvEmptyPuzzles.setVisibility(View.VISIBLE);
    tvEmptyPuzzles.setText(emptyPuzzlesShown ? getString(R.string.export_hide_empty_puzzles)
      : getResources().getQuantityString(R.plurals.export_show_empty_puzzles, empty.size(), empty.size()));

    emptyPuzzlePills.setVisibility(emptyPuzzlesShown ? View.VISIBLE : View.GONE);
    emptyPuzzlePills.removeAllViews();
    if (!emptyPuzzlesShown) {
      return;
    }
    for (PuzzleGroup group : empty) {
      int color = ContextCompat.getColor(this, PuzzleIcons.colorForCubeType(group.cubeType));
      View pill = buildPill(emptyPuzzlePills, group.cubeType.getName(), null,
        PuzzleIcons.forCubeType(group.cubeType), withAlpha(color, EMPTY_PUZZLE_ALPHA));
      // Nothing to take here, so the pill neither ripples nor answers a press.
      pill.setBackground(pillShape(color, false));
      pill.setClickable(false);
      pill.setFocusable(false);
      ((TextView) pill.findViewById(R.id.tvSolveTypeName))
        .setTextColor(ContextCompat.getColor(this, R.color.secondary_text));
      emptyPuzzlePills.addView(pill);
    }
  }

  private boolean isEverythingSelected() {
    boolean anything = false;
    for (PuzzleGroup group : groups) {
      if (group.total > 0) {
        anything = true;
        if (!group.allSelected()) {
          return false;
        }
      }
    }
    return anything;
  }

  private void setAllSelected(boolean selected) {
    for (PuzzleGroup group : groups) {
      for (SolveTypeItem item : group.items) {
        item.selected = selected;
      }
      if (group.total > 0) {
        refreshGroup(group);
      }
    }
    refreshSelection();
  }

  private void setGroupSelected(PuzzleGroup group, boolean selected) {
    for (SolveTypeItem item : group.items) {
      item.selected = selected;
    }
    refreshGroup(group);
    refreshSelection();
  }

  private static int withAlpha(int color, int alpha) {
    return (color & 0x00FFFFFF) | (alpha << 24);
  }

  private GradientDrawable rounded(int color, float radiusDp) {
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(radiusDp * density);
    shape.setColor(color);
    return shape;
  }

  private int strokePx() {
    return (int) (STROKE_DP * density);
  }

  /** Empty, part taken, all taken: an outline, a washed box, then the puzzle's own colour filled. */
  private Drawable boxBackground(int color, int selected, int total) {
    GradientDrawable box = rounded(Color.TRANSPARENT, BOX_RADIUS_DP);
    if (selected == 0) {
      box.setStroke(strokePx(), withAlpha(color, OUTLINE_ALPHA));
    } else if (selected == total) {
      box.setColor(color);
    } else {
      box.setColor(withAlpha(color, WASH_ALPHA));
      box.setStroke(strokePx(), color);
    }
    return box;
  }

  private GradientDrawable pillShape(int color, boolean selected) {
    GradientDrawable pill = rounded(selected ? withAlpha(color, WASH_ALPHA) : Color.TRANSPARENT, PILL_RADIUS_DP);
    pill.setStroke(strokePx(), selected ? withAlpha(color, OUTLINE_ALPHA) : UNSELECTED_OUTLINE);
    return pill;
  }

  private Drawable pillBackground(int color, boolean selected) {
    return new RippleDrawable(ColorStateList.valueOf(color), pillShape(color, selected),
      rounded(Color.WHITE, PILL_RADIUS_DP));
  }

  private void export(final boolean share) {
    List<Integer> solveTypeIds = new ArrayList<Integer>();
    for (PuzzleGroup group : groups) {
      for (SolveTypeItem item : group.items) {
        if (item.selected) {
          solveTypeIds.add(item.solveType.getId());
        }
      }
    }
    if (solveTypeIds.isEmpty()) {
      DialogUtils.showInfoMessage(this, R.string.select_at_least_one_solve_type);
      return;
    }
    final ProgressDialog progressDialog = new ProgressDialog(this);
    progressDialog.setMessage(getString(R.string.exporting_history));
    progressDialog.setIndeterminate(true);
    progressDialog.setCancelable(false);
    progressDialog.show();
    App.INSTANCE.getService().getExportFile(solveTypeIds, NO_LIMIT, new DataCallback<List<ExportResult>>() {
      @Override
      public void onData(final List<ExportResult> data) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            progressDialog.hide();
            progressDialog.dismiss();
            if (data != null && !data.isEmpty()) {
              CSVGenerator generator = new ExportCSVGenerator(data);
              File file = FileUtils.createCSVFile(ExportActivity.this, EXPORT_FILE_NAME, generator);
              if (share) {
                sendExportFile(file);
              } else {
                saveExportFile(file);
              }
            } else {
              DialogUtils.showInfoMessage(ExportActivity.this, R.string.no_data_to_export);
            }
          }
        });
      }
    });
  }

  private void sendExportFile(File file) {
    Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
    DialogUtils.shareData(this,
      getString(R.string.export_mail_subject),
      getString(R.string.export_mail_body, FormatterService.INSTANCE.formatDateTime(System.currentTimeMillis())),
      uri, EXPORT_MIME_TYPE);
  }

  private void saveExportFile(File file) {
    pendingSaveFile = file;
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
      .addCategory(Intent.CATEGORY_OPENABLE)
      .setType(EXPORT_MIME_TYPE)
      .putExtra(Intent.EXTRA_TITLE, getDefaultExportFileName());
    try {
      startActivityForResult(intent, REQ_CREATE_DOCUMENT);
    } catch (ActivityNotFoundException e) {
      // no document picker on this device (stripped ROM): fall back to the app's own storage folder
      saveToAppStorage(file);
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != REQ_CREATE_DOCUMENT) {
      return;
    }
    File source = pendingSaveFile;
    pendingSaveFile = null;
    if (resultCode != RESULT_OK || data == null || data.getData() == null) {
      return; // user cancelled
    }
    if (source == null) {
      // the generated CSV was lost (process killed while the picker was open)
      DialogUtils.showInfoMessage(this, R.string.export_save_failed);
      return;
    }
    try {
      FileUtils.copyFileTo(source, getContentResolver().openOutputStream(data.getData()));
      DialogUtils.showInfoMessage(this, R.string.export_saved);
    } catch (IOException e) {
      Log.e("[NanoTimer]", "Could not save export file", e);
      DialogUtils.showInfoMessage(this, R.string.export_save_failed);
    }
  }

  private void saveToAppStorage(File file) {
    File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
    if (dir == null) {
      DialogUtils.showInfoMessage(this, R.string.export_save_failed);
      return;
    }
    File dest = new File(dir, getDefaultExportFileName());
    try {
      FileUtils.copyFileTo(file, new FileOutputStream(dest));
      DialogUtils.showInfoMessage(this, getString(R.string.export_saved_to, dest.getAbsolutePath()));
    } catch (IOException e) {
      Log.e("[NanoTimer]", "Could not save export file", e);
      DialogUtils.showInfoMessage(this, R.string.export_save_failed);
    }
  }

  private String getDefaultExportFileName() {
    String date = new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ENGLISH).format(new Date());
    return "cubench_export_" + date + ".csv";
  }

  /** One puzzle and the solve types under it that hold solves. */
  private static class PuzzleGroup {
    private final CubeType cubeType;
    private final List<SolveTypeItem> items = new ArrayList<SolveTypeItem>();
    private int total;
    private ImageView box;
    private TextView tvCount;

    private PuzzleGroup(CubeType cubeType) {
      this.cubeType = cubeType;
    }

    private int selectedSolves() {
      int selected = 0;
      for (SolveTypeItem item : items) {
        if (item.selected) {
          selected += item.count;
        }
      }
      return selected;
    }

    private boolean allSelected() {
      for (SolveTypeItem item : items) {
        if (!item.selected) {
          return false;
        }
      }
      return !items.isEmpty();
    }
  }

  private static class SolveTypeItem {
    private final SolveType solveType;
    private final String name;
    private final int count;
    private boolean selected;
    private View pill;

    private SolveTypeItem(SolveType solveType, String name, int count) {
      this.solveType = solveType;
      this.name = name;
      this.count = count;
    }
  }

}
