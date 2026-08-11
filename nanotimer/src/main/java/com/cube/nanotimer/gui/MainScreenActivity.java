package com.cube.nanotimer.gui;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;

import androidx.activity.OnBackPressedCallback;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SmartCubeChip;
import com.cube.nanotimer.cube.SmartCubeGate;
import com.cube.nanotimer.gui.widget.AboutDialog;
import com.cube.nanotimer.gui.widget.SmartCubeConnectDialog;
import com.cube.nanotimer.gui.widget.HistoryDetailDialog;
import com.cube.nanotimer.gui.widget.ResultListener;
import com.cube.nanotimer.gui.widget.SelectionHandler;
import com.cube.nanotimer.gui.widget.SelectorFragmentDialog;
import com.cube.nanotimer.gui.widget.SelectorListDialog;
import com.cube.nanotimer.gui.widget.SolveNavigator;
import com.cube.nanotimer.gui.widget.TimeChangedHandler;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.YesNoListener;
import com.cube.nanotimer.util.exportimport.ErrorListener;
import com.cube.nanotimer.util.exportimport.csvimport.CSVImporter;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.TimeColorScale;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.EnterAnimation;
import com.cube.nanotimer.util.view.HeroStat;
import com.cube.nanotimer.util.view.PuzzleIcons;
import com.cube.nanotimer.util.view.SolveTypeIcons;
import com.cube.nanotimer.util.view.SolveStepBarView;
import com.cube.nanotimer.util.view.SolveStepBars;
import com.cube.nanotimer.util.view.SparklineView;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveHistory;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.TimesSort;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainScreenActivity extends DrawerLayoutActivity implements SelectionHandler, ResultListener, TimeChangedHandler, SolveNavigator {

  private ListView lvHistory;
  private TextView tvCubeType;
  private TextView tvSolveType;
  private TextView tvSolvesCount;
  private ImageView imgCubeType;
  private View heroGlyphTile;
  private ImageView imgSolveTypeKind;
  private SparklineView sparkline;
  private View sparklineBlock;
  private View heroStatsRow;
  private View tvNoSolves;
  private Button buStart;

  private CubeType curCubeType;
  private SolveType curSolveType;
  private final List<CubeType> cubeTypes = new ArrayList<>();
  private final List<SolveType> solveTypes = new ArrayList<>();

  // Lifetime solve counts, kept fresh so a picker opens with its figures already in hand.
  private final Map<Integer, Integer> cubeTypeCounts = new HashMap<>();
  private final Map<Integer, Integer> solveTypeCounts = new HashMap<>();

  // What the cells were last drawn from, so re-picking a statistic redraws them without a query.
  private SolveAverages shownAverages;

  private int solvesCount;
  private int currentOrientation;
  private TimesSort timesSort = TimesSort.TIMESTAMP;
  private boolean refreshingHistory;

  private final List<SolveTime> liHistory = new ArrayList<>();
  // Position of the first solve of each day, to its heading. Rebuilt whenever the list changes,
  // so binding a row stays a lookup.
  private final Map<Integer, String> dayHeaders = new HashMap<>();
  private final Handler timeAgoHandler = new Handler();
  private HistoryListAdapter historyListAdapter;
  private MenuListAdapter menuListAdapter;
  private SmartCubeChip smartCubeChip;

  private int previousLastItem = 0;

  // History time color gradient (green=fast → white=median → red=slow), recomputed once
  // per data load over the last N solves (N = Options.getColorSampleSize()).
  private TimeColorScale timeColorScale;
  private int recordColor;
  private int[] stepColors;

  private Toast quitMessage;
  private boolean inQuitMode;
  private static final long QUIT_MODE_DELAY = 3000;

  private static final int ID_CUBETYPE = 1;
  private static final int ID_SOLVETYPE = 2;
  private static final int ID_IMPORTEXPORT = 3;
  private static final int ID_LANGUAGE = 4;
  /** One id per statistics cell, so a pick comes back knowing which cell asked. */
  private static final int ID_STAT_CELL = 10;

  private static final int[] STAT_CELL_IDS = { R.id.statCellOne, R.id.statCellTwo, R.id.statCellThree };
  private static final int[] STAT_KEY_IDS = { R.id.tvStatKeyOne, R.id.tvStatKeyTwo, R.id.tvStatKeyThree };
  private static final int[] STAT_VALUE_IDS =
    { R.id.tvStatValueOne, R.id.tvStatValueTwo, R.id.tvStatValueThree };

  private static final int IMPORT_REQUEST_CODE = 1;

  private static final int REQUEST_READ_PERMISSIONS_CODE = 10;

  /**
   * How many recent solves the sparkline draws, whatever the color sample size is set to. Shared
   * with the graph, whose last-solves period is the same window drawn in full.
   */
  static final int TREND_SIZE = 50;

  /** How often today's rows re-state how long ago they were, while the screen is just sitting there. */
  private static final long TIME_AGO_TICK_MS = 30000;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    App.INSTANCE.setContext(this);
    Utils.updateContextWithPrefsLocale(this); // because ad provider somehow re-initializes the context

    setContentView(R.layout.mainscreen_screen);
    currentOrientation = getResources().getConfiguration().orientation;

    setVolumeControlStream(AudioManager.STREAM_MUSIC);

    curCubeType = Utils.getCurrentCubeType(this);
    curSolveType = new SolveType(Utils.getCurrentSolveTypeId(this), "", false, null, curCubeType.getId());

    initViews();

    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        if (inQuitMode) {
          if (quitMessage != null) {
            quitMessage.cancel();
          }
          setEnabled(false);
          MainScreenActivity.this.getOnBackPressedDispatcher().onBackPressed();
        } else {
          quitMessage = Toast.makeText(MainScreenActivity.this, R.string.backspace_exit, Toast.LENGTH_LONG);
          quitMessage.show();
          inQuitMode = true;
          Handler handler = new Handler();
          handler.postDelayed(new Runnable() {
            @Override
            public void run() {
              quitMessage.cancel();
              inQuitMode = false;
            }
          }, QUIT_MODE_DELAY);
        }
      }
    });
  }

  @Override
  protected void initViews() {
    super.initViews();
    timeColorScale = new TimeColorScale(this);
    recordColor = ContextCompat.getColor(this, R.color.new_record);
    stepColors = SolveStepBars.stepColors(this);

    tvCubeType = (TextView) findViewById(R.id.tvCubeType);
    tvSolveType = (TextView) findViewById(R.id.tvSolveType);
    tvSolvesCount = (TextView) findViewById(R.id.tvSolvesCount);
    imgCubeType = (ImageView) findViewById(R.id.imgCubeType);
    heroGlyphTile = findViewById(R.id.heroGlyphTile);
    imgSolveTypeKind = (ImageView) findViewById(R.id.imgSolveTypeKind);
    sparkline = (SparklineView) findViewById(R.id.sparkline);
    sparklineBlock = findViewById(R.id.sparklineBlock);
    heroStatsRow = findViewById(R.id.heroStatsRow);
    tvNoSolves = findViewById(R.id.tvNoSolves);

    findViewById(R.id.cubeTypeRow).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        openCubeTypePicker();
      }
    });
    findViewById(R.id.solveTypeRow).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        openSolveTypePicker();
      }
    });
    sparklineBlock.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        // The graph opens on the same solves the line just drew: the trend, in full.
        openGraph(GraphActivity.Period.LAST_SOLVES);
      }
    });
    for (int cell = 0; cell < STAT_CELL_IDS.length; cell++) {
      final int cellIndex = cell;
      findViewById(STAT_CELL_IDS[cell]).setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View view) {
          openStatPicker(cellIndex);
        }
      });
    }

    initHistoryList();

    menuListAdapter = new MenuListAdapter(this, R.id.lvMenuItems, getResources().getStringArray(R.array.mainscreen_menu_items));
    ListView lvMenuItems = (ListView) findViewById(R.id.lvMenuItems);
    lvMenuItems.setAdapter(menuListAdapter);
    lvMenuItems.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        closeDrawer();
        onMenuItemClick(i);
      }
    });

    setSortMode(TimesSort.TIMESTAMP);

    smartCubeChip = new SmartCubeChip(this, this::openSmartCubeConnect);

    buStart = findViewById(R.id.buStart);
//    buStart.setShadowLayer(1, 3f, 3f, getResources().getColor(R.color.black));
    buStart.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        buStart.setEnabled(false);

        Intent i = new Intent(MainScreenActivity.this, TimerActivity.class);
        i.putExtra("cubeType", curCubeType);
        i.putExtra("solveType", curSolveType);
        i.putExtra("solvesCount", solvesCount);
        startActivity(i);
      }
    });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.mainscreen_menu, menu);

    int drawableIcon;
    if (App.INSTANCE.isProEnabled()) {
      drawableIcon = R.drawable.icon_pro;
    } else {
      drawableIcon = R.drawable.app_icon_cube;
    }
    menu.findItem(R.id.itAppIcon).setIcon(drawableIcon);

    // Bound to the item and not only to its view: an action view that is GONE still leaves the item
    // holding its width in the bar, and the gate has to take the whole item away.
    MenuItem smartCubeItem = menu.findItem(R.id.itSmartCube);
    smartCubeChip.bind(smartCubeItem, smartCubeItem != null ? smartCubeItem.getActionView() : null);
    MenuItem drillItem = menu.findItem(R.id.itDrill);
    if (drillItem != null) {
      drillItem.setVisible(SmartCubeGate.ENABLED); // a drill is run on a cube: the only way in
    }

    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.itDrill) {
      startActivity(new Intent(this, DrillSetupActivity.class));
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void openSmartCubeConnect() {
    DialogUtils.showFragment(this, new SmartCubeConnectDialog());
  }

  private void openCubeTypePicker() {
    ArrayList<String> names = new ArrayList<>();
    ArrayList<String> counts = new ArrayList<>();
    ArrayList<Integer> icons = new ArrayList<>();
    ArrayList<Integer> colors = new ArrayList<>();
    for (CubeType cubeType : cubeTypes) {
      names.add(cubeType.getName());
      counts.add(formatCount(cubeTypeCounts.get(cubeType.getId())));
      icons.add(PuzzleIcons.forCubeType(cubeType));
      colors.add(PuzzleIcons.colorForCubeType(cubeType));
    }
    DialogUtils.showFragment(this, SelectorListDialog
      .newInstance(ID_CUBETYPE, names, counts, icons, colors, cubeTypes.indexOf(curCubeType), null, 0, this)
      .setHeader(getString(R.string.cube_type), currentPuzzleName(),
        PuzzleIcons.forCubeType(curCubeType), PuzzleIcons.colorForCubeType(curCubeType)));
  }

  private void openSolveTypePicker() {
    ArrayList<String> names = new ArrayList<>();
    ArrayList<String> counts = new ArrayList<>();
    ArrayList<Integer> icons = new ArrayList<>();
    ArrayList<Integer> colors = new ArrayList<>();
    int selectedIndex = -1;
    for (int i = 0; i < solveTypes.size(); i++) {
      SolveType solveType = solveTypes.get(i);
      names.add(Utils.toSolveTypeLocalizedName(this, solveType.getName()));
      counts.add(formatCount(solveTypeCounts.get(solveType.getId())));
      icons.add(SolveTypeIcons.forSolveType(solveType));
      colors.add(SolveTypeIcons.colorForSolveType(solveType));
      if (curSolveType != null && curSolveType.getId() == solveType.getId()) {
        selectedIndex = i;
      }
    }
    DialogUtils.showFragment(this, SelectorListDialog
      .newInstance(ID_SOLVETYPE, names, counts, icons, colors, selectedIndex,
        getString(R.string.edit_solve_types_dots), R.drawable.ic_action_edit, this)
      // Plural: the title under it is the puzzle these belong to, not the solve type in use, and
      // a singular eyebrow read as a label for it.
      .setHeader(getString(R.string.solve_types), currentPuzzleName(),
        PuzzleIcons.forCubeType(curCubeType), PuzzleIcons.colorForCubeType(curCubeType)));
  }

  /** Which language the list is already on, so the picker marks it as every other picker does. */
  private int currentLanguageIndex() {
    String current = getApplicationContext()
      .getSharedPreferences(Utils.LANGUAGE_PREFS_NAME, 0).getString(Utils.LANGUAGE_PREF_KEY, null);
    if (current == null) {
      current = Locale.getDefault().getLanguage();
    }
    String[] codes = getResources().getStringArray(R.array.language_codes);
    for (int i = 0; i < codes.length; i++) {
      if (codes[i].equals(current)) {
        return i;
      }
    }
    return -1;
  }

  private String currentPuzzleName() {
    return curCubeType == null ? "" : curCubeType.getName();
  }

  private String formatCount(Integer count) {
    return (count == null || count == 0) ? "" : String.valueOf(count);
  }

  private void openGraph() {
    openGraph(null);
  }

  /** @param period the period to open on, or null to open on whichever was last used */
  private void openGraph(GraphActivity.Period period) {
    Intent i = new Intent(this, GraphActivity.class);
    i.putExtra("cubeType", curCubeType);
    i.putExtra("solveType", curSolveType);
    if (period != null) {
      i.putExtra("period", period);
    }
    startActivity(i);
  }

  private void initHistoryList() {
    historyListAdapter = new HistoryListAdapter(this, R.id.lvHistory, liHistory);
    lvHistory = (ListView) findViewById(R.id.lvHistory);
    lvHistory.setAdapter(historyListAdapter);
    lvHistory.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        DialogUtils.showFragment(MainScreenActivity.this, HistoryDetailDialog.newInstance(
          liHistory.get(i), curCubeType, MainScreenActivity.this, MainScreenActivity.this));
      }
    });
    lvHistory.setOnScrollListener(new OnScrollListener() {
      @Override
      public void onScrollStateChanged(AbsListView view, int scrollState) {
      }

      @Override
      public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (view.getId() == R.id.lvHistory && !liHistory.isEmpty() && !refreshingHistory) {
          int lastVisibleItem = firstVisibleItem + visibleItemCount;
          if (totalItemCount == lastVisibleItem && lastVisibleItem != previousLastItem) {
            previousLastItem = lastVisibleItem;
            long from;
            if (timesSort == TimesSort.TIME) {
              from = liHistory.get(liHistory.size() - 1).getTime();
            } else {
              from = liHistory.get(liHistory.size() - 1).getTimestamp();
            }
            App.INSTANCE.getService().getPagedHistory(curSolveType, from, timesSort, new DataCallback<SolveHistory>() {
              @Override
              public void onData(final SolveHistory data) {
                runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                    setSolvesCount(data.getSolvesCount());
                    liHistory.addAll(data.getSolveTimes());
                    onHistoryChanged();
                  }
                });
              }
            });
          }
        }
      }
    });
  }

  private void onMenuItemClick(int index) {
    switch (index) {
      case 0:
        startActivity(new Intent(this, OptionsActivity.class));
        break;
      case 1:
        if (timesSort == TimesSort.TIMESTAMP) {
          setSortMode(TimesSort.TIME);
        } else if (timesSort == TimesSort.TIME) {
          setSortMode(TimesSort.TIMESTAMP);
        }
        break;
      case 2:
        openGraph();
        break;
      case 3:
        ArrayList<String> items = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.import_export)));
        ArrayList<Integer> icons = new ArrayList<>(Arrays.asList(R.drawable.import_icon, R.drawable.export_icon));
        DialogUtils.showFragment(this, SelectorFragmentDialog.newInstance(ID_IMPORTEXPORT, items, icons, null, true, this));
        break;
      case 4:
        DialogUtils.showDestructiveConfirmDialog(this, R.string.clear_history_title,
            R.string.clear_history_solve_type_confirmation, R.string.delete, R.string.cancel, new YesNoListener() {
          @Override
          public void onYes() {
            if (curSolveType != null) {
              App.INSTANCE.getService().deleteHistory(curSolveType, new DataCallback<Void>() {
                @Override
                public void onData(Void data) {
                  refreshHistory();
                }
              });
            }
          }
        });
        break;
      case 5:
        items = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.languages)));
        ArrayList<Integer> flagIcons = new ArrayList<>(Arrays.asList(R.drawable.flag_uk, R.drawable.flag_france, R.drawable.flag_spain, R.drawable.flag_portugal));
        DialogUtils.showFragment(this, SelectorFragmentDialog
          .newInstance(ID_LANGUAGE, items, flagIcons, null, true, this)
          .setSelection(currentLanguageIndex()));
        break;
      case 6:
        DialogUtils.showFragment(this, AboutDialog.newInstance());
        break;
      case 7:
        Utils.openPlayStorePage(this, getPackageName());
        break;
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    App.INSTANCE.setContext(this);

    buStart.setEnabled(true);

    smartCubeChip.start();
    invalidateOptionsMenu();

    refreshCubeTypes();

    setSortMode(TimesSort.TIMESTAMP);
    startTimeAgoTicks();
  }

  @Override
  protected void onPause() {
    super.onPause();
    smartCubeChip.stop();
    timeAgoHandler.removeCallbacksAndMessages(null);
  }

  /**
   * Today's rows say how long ago they were, so they go stale just by being looked at. Re-stating
   * them on a beat also picks up the day rolling over, which moves the headings as well.
   */
  private void startTimeAgoTicks() {
    timeAgoHandler.removeCallbacksAndMessages(null);
    timeAgoHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        if (timesSort == TimesSort.TIMESTAMP && !liHistory.isEmpty()) {
          rebuildDayHeaders();
          historyListAdapter.notifyDataSetChanged();
        }
        timeAgoHandler.postDelayed(this, TIME_AGO_TICK_MS);
      }
    }, TIME_AGO_TICK_MS);
  }

  /**
   * The activity keeps itself across a rotation, so the layout for the new orientation has to be
   * put up by hand: laid out as in portrait, the card alone is taller than a landscape screen.
   */
  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    if (newConfig.orientation == currentOrientation) {
      return;
    }
    currentOrientation = newConfig.orientation;
    smartCubeChip.stop();

    setContentView(R.layout.mainscreen_screen);
    initViews();
    invalidateOptionsMenu(); // the toolbar is a new one, so its items are rebuilt onto it

    smartCubeChip.start();
    refreshCubeTypes();
  }

  private void refreshCubeTypes() {
    App.INSTANCE.getService().getCubeTypes(false, new DataCallback<List<CubeType>>() {
      @Override
      public void onData(List<CubeType> data) {
        cubeTypes.clear();
        cubeTypes.addAll(data);

        if (!cubeTypes.isEmpty()) {
          CubeType defaultCubeType = null;
          CubeType newCubeType = null;

          for (CubeType ct : cubeTypes) {
            if (curCubeType != null && curCubeType.getId() == ct.getId()) {
              newCubeType = ct;
            }
            if (ct.getId() == CubeType.THREE_BY_THREE.getId()) {
              defaultCubeType = ct;
            }
          }

          setCurCubeType(newCubeType != null ? newCubeType : defaultCubeType != null ? defaultCubeType : cubeTypes.get(0));
        } else {
          setCurCubeType(null);
        }
        refreshSolveTypes();
      }
    });
    App.INSTANCE.getService().getSolvesCountPerCubeType(new DataCallback<Map<Integer, Integer>>() {
      @Override
      public void onData(Map<Integer, Integer> counts) {
        cubeTypeCounts.clear();
        cubeTypeCounts.putAll(counts);
      }
    });
  }

  private void refreshSolveTypes() {
    if (curCubeType != null) {
      App.INSTANCE.getService().getSolvesCountPerSolveType(curCubeType, new DataCallback<Map<Integer, Integer>>() {
        @Override
        public void onData(Map<Integer, Integer> counts) {
          solveTypeCounts.clear();
          solveTypeCounts.putAll(counts);
        }
      });
      App.INSTANCE.getService().getSolveTypes(curCubeType, new DataCallback<List<SolveType>>() {
        @Override
        public void onData(List<SolveType> data) {
          solveTypes.clear();
          solveTypes.addAll(data);
          SolveType newCurSolveType = null;

          if (!solveTypes.isEmpty()) {
            boolean foundType = false;
            if (curSolveType != null) {
              for (SolveType st : solveTypes) {
                if (curSolveType.getId() == st.getId()) {
                  newCurSolveType = st;
                  foundType = true;
                }
              }
            }
            if (!foundType) {
              newCurSolveType = solveTypes.get(0);
            }
          }
          setCurSolveType(newCurSolveType);
          refreshHero();
          refreshHistory();
        }
      });
    } else {
      solveTypes.clear();
      solveTypeCounts.clear();
      setCurSolveType(null);
      refreshHero();
      refreshHistory();
    }
  }

  /** Names what is selected, then asks for the numbers that describe it. */
  private void refreshHero() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        tvCubeType.setText(curCubeType != null ? curCubeType.getName() : "");
        // The puzzle wears its own colour here too, so the card and its picker are the same object.
        int puzzleColor = ContextCompat.getColor(MainScreenActivity.this,
          PuzzleIcons.colorForCubeType(curCubeType));
        imgCubeType.setImageResource(PuzzleIcons.forCubeType(curCubeType));
        imgCubeType.setColorFilter(puzzleColor, PorterDuff.Mode.SRC_IN);
        heroGlyphTile.setBackground(glyphTile(puzzleColor));
        if (curSolveType != null) {
          tvSolveType.setText(Utils.toSolveTypeLocalizedName(MainScreenActivity.this, curSolveType.getName()));
          imgSolveTypeKind.setImageResource(SolveTypeIcons.forSolveType(curSolveType));
          imgSolveTypeKind.setColorFilter(ContextCompat.getColor(MainScreenActivity.this,
            SolveTypeIcons.colorForSolveType(curSolveType)), PorterDuff.Mode.SRC_IN);
          imgSolveTypeKind.setVisibility(View.VISIBLE);
        } else {
          tvSolveType.setText(R.string.NA);
          imgSolveTypeKind.setVisibility(View.GONE);
        }
      }
    });
    refreshStatCells();
  }

  /**
   * The tile behind the puzzle mark, a wash of that puzzle's colour. Built rather than tinted: a
   * tint is composited SRC_IN, so it would multiply this alpha by the drawable's own and land at a
   * sixth of what it asks for. Mutated, since the drawable's constant state is shared.
   */
  private Drawable glyphTile(int puzzleColor) {
    GradientDrawable tile =
      (GradientDrawable) ContextCompat.getDrawable(this, R.drawable.hero_glyph).mutate();
    tile.setColor((puzzleColor & 0x00FFFFFF) | 0x2E000000);
    return tile;
  }

  /**
   * The three cells, each showing whichever statistic it was last set to. Which one that is belongs
   * to the user: the windows worth watching are not the same for a solver chasing an Ao5 and one
   * counting blind successes, and no default is right for both.
   */
  private void refreshStatCells() {
    if (curSolveType == null) {
      return;
    }
    final SolveType solveType = curSolveType;
    App.INSTANCE.getService().getSolveAverages(solveType, new DataCallback<SolveAverages>() {
      @Override
      public void onData(final SolveAverages averages) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (curSolveType == null || curSolveType.getId() != solveType.getId()) {
              return; // the user moved on while this was loading
            }
            shownAverages = averages;
            showStatCells();
            EnterAnimation.stagger(findViewById(STAT_CELL_IDS[0]),
              findViewById(STAT_CELL_IDS[1]), findViewById(STAT_CELL_IDS[2]));
          }
        });
      }
    });
  }

  /** Draws the cells from the averages already loaded, so a re-pick lands without another query. */
  private void showStatCells() {
    if (curSolveType == null) {
      return;
    }
    boolean blind = curSolveType.isBlind();
    for (int cell = 0; cell < STAT_CELL_IDS.length; cell++) {
      HeroStat stat = Options.INSTANCE.getHeroStat(cell, blind);
      ((TextView) findViewById(STAT_KEY_IDS[cell])).setText(stat.label(this));
      ((TextView) findViewById(STAT_VALUE_IDS[cell])).setText(stat.value(this, shownAverages));
    }
  }

  /**
   * The statistics this solve type has to offer, each with what it currently stands at, so the
   * picker is also the one place they can all be read at once.
   */
  private void openStatPicker(int cell) {
    if (curSolveType == null) {
      return;
    }
    boolean blind = curSolveType.isBlind();
    List<HeroStat> options = HeroStat.optionsFor(curSolveType);
    ArrayList<String> names = new ArrayList<>();
    ArrayList<String> values = new ArrayList<>();
    ArrayList<Integer> icons = new ArrayList<>();
    ArrayList<Integer> colors = new ArrayList<>();
    for (HeroStat stat : options) {
      names.add(stat.label(this));
      values.add(stat.value(this, shownAverages));
      icons.add(0);
      colors.add(SolveTypeIcons.colorForSolveType(curSolveType));
    }
    DialogUtils.showFragment(this, SelectorListDialog
      .newInstance(ID_STAT_CELL + cell, names, values, icons, colors,
        options.indexOf(Options.INSTANCE.getHeroStat(cell, blind)), null, 0, this)
      .setHeader(getString(R.string.stat_to_show),
        Utils.toSolveTypeLocalizedName(this, curSolveType.getName()),
        SolveTypeIcons.forSolveType(curSolveType), SolveTypeIcons.colorForSolveType(curSolveType))
      .setNote(blind ? getString(R.string.blind_averages_note) : null));
  }

  private void statPicked(int cell, int position) {
    if (curSolveType == null) {
      return;
    }
    List<HeroStat> options = HeroStat.optionsFor(curSolveType);
    if (position < 0 || position >= options.size()) {
      return; // dismissed without choosing
    }
    Options.INSTANCE.setHeroStat(cell, curSolveType.isBlind(), options.get(position));
    showStatCells();
  }

  public void refreshHistory() {
    previousLastItem = 0;
    refreshRecentTimes();
    if (curSolveType != null) {
      refreshingHistory = true;
      App.INSTANCE.getService().getPagedHistory(curSolveType, timesSort, new DataCallback<SolveHistory>() {
        @Override
        public void onData(final SolveHistory data) {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              setSolvesCount(data.getSolvesCount());
              liHistory.clear();
              liHistory.addAll(data.getSolveTimes());
              onHistoryChanged();
              lvHistory.setSelection(0);
              refreshingHistory = false;
            }
          });
        }
      });
    } else {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          setSolvesCount(0);
          liHistory.clear();
          onHistoryChanged();
        }
      });
    }
  }

  /**
   * One load of recent times feeds two things: the gradient the rows are colored on, over the
   * last getColorSampleSize() solves, and the sparkline, over the last {@link #TREND_SIZE}. The
   * scale only ever sees the sample size the user set, however many the sparkline asked for.
   */
  private void refreshRecentTimes() {
    if (curSolveType == null) {
      timeColorScale.setTimes(null);
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          sparkline.setTimes(null, false);
          refreshTrendVisibility();
        }
      });
      return;
    }
    final int sampleSize = Options.INSTANCE.getColorSampleSize();
    final SolveType solveType = curSolveType;
    App.INSTANCE.getService().getLastSolveTimes(solveType, Math.max(TREND_SIZE, sampleSize), new DataCallback<List<Long>>() {
      @Override
      public void onData(final List<Long> times) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (curSolveType == null || curSolveType.getId() != solveType.getId()) {
              return; // the user moved on while this was loading
            }
            timeColorScale.setTimes(times.subList(0, Math.min(sampleSize, times.size())));
            sparkline.setTimes(times, true);
            refreshTrendVisibility();
            if (historyListAdapter != null) {
              historyListAdapter.notifyDataSetChanged();
            }
          }
        });
      }
    });
  }

  /** Below a handful of solves there is no trend and no average worth a cell, so both go. */
  private void refreshTrendVisibility() {
    boolean hasTrend = sparkline.hasEnoughTimes();
    sparklineBlock.setVisibility(hasTrend ? View.VISIBLE : View.GONE);
    heroStatsRow.setVisibility(hasTrend ? View.VISIBLE : View.GONE);
  }

  private void onHistoryChanged() {
    rebuildDayHeaders();
    historyListAdapter.notifyDataSetChanged();
    tvNoSolves.setVisibility(liHistory.isEmpty() ? View.VISIBLE : View.GONE);
  }

  /**
   * Marks the first row of each day with its heading, so the list carries the dates the rows no
   * longer repeat. Sorted by time there are no days to group, and the one heading names the sort.
   */
  private void rebuildDayHeaders() {
    dayHeaders.clear();
    if (liHistory.isEmpty()) {
      return;
    }
    if (timesSort != TimesSort.TIMESTAMP) {
      dayHeaders.put(0, getString(R.string.best_times));
      return;
    }
    Calendar calendar = Calendar.getInstance();
    long today = dayStart(calendar, System.currentTimeMillis());
    calendar.add(Calendar.DAY_OF_YEAR, -1); // not today minus 24h: a day is not always that long
    long yesterday = calendar.getTimeInMillis();
    long previousDay = -1;
    for (int i = 0; i < liHistory.size(); i++) {
      long day = dayStart(calendar, liHistory.get(i).getTimestamp());
      if (day != previousDay) {
        dayHeaders.put(i, dayLabel(day, today, yesterday));
        previousDay = day;
      }
    }
  }

  private String dayLabel(long day, long today, long yesterday) {
    if (day == today) {
      return getString(R.string.today);
    }
    if (day == yesterday) {
      return getString(R.string.yesterday);
    }
    return FormatterService.INSTANCE.formatDate(day);
  }

  private static long dayStart(Calendar calendar, long timestamp) {
    calendar.setTimeInMillis(timestamp);
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTimeInMillis();
  }

  @Override
  public void onResult(Object... params) {
    refreshHistory();
    refreshSolveTypes();
  }

  private void setSortMode(TimesSort timesSort) {
    menuListAdapter.notifyDataSetChanged();

    if (this.timesSort != timesSort) {
      this.timesSort = timesSort;
      refreshHistory();
    }
  }

  @Override
  public void itemSelected(int id, int position) {
    if (position >= 0) {
      if (id == ID_CUBETYPE) {
        if (position < cubeTypes.size() && !cubeTypes.get(position).equals(curCubeType)) {
          setCurCubeType(cubeTypes.get(position));
          refreshSolveTypes();
        }
      } else if (id == ID_SOLVETYPE) {
        if (position < solveTypes.size()) {
          setCurSolveType(solveTypes.get(position));
          refreshHero();
          refreshHistory();
        } else {
          // solve types shortcut
          Intent i = new Intent(this, SolveTypesActivity.class);
          i.putExtra("cubeType", curCubeType);
          startActivity(i);
        }
      } else if (id >= ID_STAT_CELL && id < ID_STAT_CELL + STAT_CELL_IDS.length) {
        statPicked(id - ID_STAT_CELL, position);
      } else if (id == ID_IMPORTEXPORT) {
        if (position == 0) {
          tryLaunchImportActivity();
        } else if (position == 1) {
          startActivity(new Intent(this, ExportActivity.class));
        }
      } else if (id == ID_LANGUAGE) {
        String localeCode = getResources().getStringArray(R.array.language_codes)[position];
        if (localeCode.isEmpty()) {
          localeCode = null;
        }

        SharedPreferences prefs = getApplicationContext().getSharedPreferences(Utils.LANGUAGE_PREFS_NAME, 0);

        if (!prefs.getString(Utils.LANGUAGE_PREF_KEY, "").equals(localeCode)) {
          Editor editor = prefs.edit();
          editor.putString(Utils.LANGUAGE_PREF_KEY, localeCode);
          editor.commit(); // MUST use commit instead of apply to make sure the pref is updated before restarting app

          if (VERSION.SDK_INT >= 11) {
            Context context = getBaseContext();
            PackageManager packageManager = context.getPackageManager();
            Intent launchIntent = packageManager.getLaunchIntentForPackage(context.getPackageName());
            ComponentName componentName = launchIntent.getComponent();

            Intent mainIntent = Intent.makeRestartActivityTask(componentName);
            context.startActivity(mainIntent);
            System.exit(0);
          } else {
            Intent intent = getIntent();
            finish();
            startActivity(intent);
          }
        }
      }
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == IMPORT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
      InputStream inputStream;
      try {
        if (VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
          Uri uri = data.getData();
          assert uri != null;
          inputStream = getContentResolver().openInputStream(uri);
        } else {
          File file = (File) data.getSerializableExtra("file");
          inputStream = new FileInputStream(file);
        }
      } catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }

      new CSVImporter(this, this, new ErrorListener() {
        @Override
        public void onError(final String message) {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              DialogUtils.showOkDialog(MainScreenActivity.this, getString(R.string.import_error), message);
            }
          });
        }
      }).importData(inputStream);
    }
  }

  private void tryLaunchImportActivity() {
    if (VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      launchImportActivity(); // Lollipop and up can use the default OS file browser
    } else if (VERSION.SDK_INT >= 19) {
      final String permission = Manifest.permission.READ_EXTERNAL_STORAGE;
      if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
//        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
//          DialogUtils.showConfirmCancelDialog(MainScreenActivity.this, R.string.import_requires_read_permissions,
//            R.string.confirm, R.string.cancel, new YesNoListener() {
//            @Override
//            public void onYes() {
//              ActivityCompat.requestPermissions(MainScreenActivity.this, new String[] { permission }, REQUEST_READ_PERMISSIONS_CODE);
//            }
//          });
//        } else {
        ActivityCompat.requestPermissions(this, new String[] { permission }, REQUEST_READ_PERMISSIONS_CODE);
//        }
      } else {
        launchImportActivity();
      }
    } else {
      launchImportActivity();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    switch (requestCode) {
      case REQUEST_READ_PERMISSIONS_CODE:
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          launchImportActivity();
        } else {
          DialogUtils.showShortInfoMessage(this, R.string.read_permission_denied_cant_import);
        }
        break;
      default:
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  private void launchImportActivity() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      // Choose a directory using the system's file picker.
      Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("text/*");

      startActivityForResult(intent, IMPORT_REQUEST_CODE);
    } else {
      // Use custom-made file browser (not compatible with newer Android versions because of permissions)
      startActivityForResult(new Intent(this, ImportActivity.class), IMPORT_REQUEST_CODE);
    }
  }

  private void setSolvesCount(int solvesCount) {
    this.solvesCount = solvesCount;
    // Nothing rather than "0 solves": the line under the card already says that.
    tvSolvesCount.setText(solvesCount == 0 ? "" : solvesCount + " " + getString(R.string.solves));
  }

  private void setCurCubeType(CubeType cubeType) {
    this.curCubeType = cubeType;
    Utils.setCurrentCubeType(this, cubeType);
  }

  private void setCurSolveType(SolveType solveType) {
    this.curSolveType = solveType;
    Utils.setCurrentSolveType(this, solveType);
  }

  /** Found by id rather than by position, so a solve deleted meanwhile cannot hand back its seat. */
  @Override
  public SolveTime getNeighbourSolve(SolveTime solveTime, int direction) {
    for (int i = 0; i < liHistory.size(); i++) {
      if (liHistory.get(i).getId() == solveTime.getId()) {
        int neighbour = i + direction;
        return neighbour >= 0 && neighbour < liHistory.size() ? liHistory.get(neighbour) : null;
      }
    }
    return null;
  }

  @Override
  public void onTimeChanged(SolveTime solveTime) {
    for (SolveTime st : liHistory) {
      if (st.getId() == solveTime.getId()) {
        updateListTime(st);
        break;
      }
    }
  }

  @Override
  public void onTimeDeleted(final SolveTime solveTime) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        for (Iterator<SolveTime> it = liHistory.iterator(); it.hasNext(); ) {
          SolveTime st = it.next();
          if (st.getId() == solveTime.getId()) {
            it.remove();
            onHistoryChanged();
            setSolvesCount(solvesCount - 1);
            // The deleted solve may have been the record, or in the window the trend is drawn on.
            refreshHero();
            refreshRecentTimes();
            break;
          }
        }
      }
    });
  }

  private void updateListTime(final SolveTime solveTime) {
    App.INSTANCE.getService().getSolveTime(solveTime.getId(), new DataCallback<SolveTime>() {
      @Override
      public void onData(final SolveTime data) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            solveTime.setTime(data.getTime());
            solveTime.setPb(data.isPb());
            solveTime.setTimeBeforeDnf(data.getTimeBeforeDnf()); // or reopening the sheet offers to undo a DNF that is gone
            historyListAdapter.notifyDataSetChanged();
            refreshHero();
            refreshRecentTimes();
          }
        });
      }
    });
  }

  private class MenuListAdapter extends ArrayAdapter<String> {
    private LayoutInflater inflater;
    private String[] objects;

    public MenuListAdapter(Context context, int id, String[] objects) {
      super(context, id, objects);
      inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      this.objects = objects;
    }

    public View getView(final int position, View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        view = inflater.inflate(R.layout.menu_item_with_icon, parent, false);
      }

      if (position >= 0 && position < objects.length) {
        ImageView icon = (ImageView) view.findViewById(R.id.imgIcon);
        Integer imageResource = null;
        switch (position) {
          case 0:
            imageResource = R.drawable.menu_settings;
            break;
          case 1:
            imageResource = R.drawable.menu_sort_history;
            break;
          case 2:
            imageResource = R.drawable.menu_graph;
            break;
          case 3:
            imageResource = R.drawable.menu_import_export;
            break;
          case 4:
            imageResource = R.drawable.menu_clear;
            break;
          case 5:
            imageResource = R.drawable.menu_language;
            break;
          case 6:
            imageResource = R.drawable.menu_about;
            break;
          case 7:
            imageResource = R.drawable.menu_rate;
            break;
        }
        if (imageResource != null) {
          icon.setImageResource(imageResource);
        }

        TextView tvName = (TextView) view.findViewById(R.id.tvText);
        if (position == 1) {
          if (timesSort == TimesSort.TIMESTAMP) {
            tvName.setText(R.string.show_best_times);
          } else {
            tvName.setText(R.string.show_history);
          }
        } else {
          tvName.setText(objects[position]);
        }
      }
      return view;
    }
  }

  private class HistoryListAdapter extends ArrayAdapter<SolveTime> {
    private LayoutInflater inflater;

    public HistoryListAdapter(Context context, int textViewResourceId, List<SolveTime> objects) {
      super(context, textViewResourceId, objects);
      inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        view = inflater.inflate(R.layout.history_list_item, parent, false);
      }

      if (position >= 0 && position < liHistory.size()) {
        String dayHeader = dayHeaders.get(position);
        View header = view.findViewById(R.id.dayHeader);
        if (dayHeader != null) {
          ((TextView) view.findViewById(R.id.tvDayLabel)).setText(dayHeader);
          header.setVisibility(View.VISIBLE);
        } else {
          header.setVisibility(View.GONE);
        }
        // A heading already separates its day from the one above, and nothing sits above the first
        // row, so those two rows draw no line of their own.
        view.findViewById(R.id.rowDivider)
          .setVisibility(dayHeader != null || position == 0 ? View.INVISIBLE : View.VISIBLE);

        SolveTime st = liHistory.get(position);
        if (st != null) {
          // A day already has its date in its heading, so the row need only say when in the day it
          // was, and for a recent one how long ago. Sorted by time there are no days to group, so
          // the row states the date too, to the minute: the seconds do not fit beside a PB chip.
          ((TextView) view.findViewById(R.id.tvDate)).setText(
            timesSort != TimesSort.TIMESTAMP ? FormatterService.INSTANCE.formatDateTimeToMinute(st.getTimestamp())
              : FormatterService.INSTANCE.formatSolveMoment(st.getTimestamp(), System.currentTimeMillis()));

          TextView tvTime = (TextView) view.findViewById(R.id.tvTime);
          tvTime.setText(FormatterService.INSTANCE.formatSolveTime(st.getTime()));
          // A record wears the record colour, which the gradient never produces; everything else
          // is colored green→white→red (fast→median→slow), and DNFs stay gray. Set on every bind
          // so recycled rows never keep a stale color.
          tvTime.setTextColor(st.isPb() ? recordColor : timeColorScale.colorFor(st));
          view.findViewById(R.id.tvPbChip).setVisibility(st.isPb() ? View.VISIBLE : View.GONE);

          boolean commented = st.getComment() != null && !st.getComment().trim().isEmpty();
          view.findViewById(R.id.imgComment).setVisibility(commented ? View.VISIBLE : View.GONE);

          // A solve that was broken down draws its shape beside its time; one the cube read but
          // could not break down draws a plain block, which still marks it as the cube's.
          SolveStepBarView stepBar = (SolveStepBarView) view.findViewById(R.id.rowStepBar);
          boolean painted = SolveStepBars.paintRow(stepBar, st, stepColors);
          stepBar.setVisibility(painted ? View.VISIBLE : View.GONE);
        }
      }
      return view;
    }
  }

}
