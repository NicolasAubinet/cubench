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
import com.cube.nanotimer.gui.widget.AboutDialog;
import com.cube.nanotimer.gui.widget.SmartCubeConnectDialog;
import com.cube.nanotimer.gui.widget.HistoryDetailDialog;
import com.cube.nanotimer.gui.widget.ResultListener;
import com.cube.nanotimer.gui.widget.SelectionHandler;
import com.cube.nanotimer.gui.widget.SelectorFragmentDialog;
import com.cube.nanotimer.gui.widget.SelectorListDialog;
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
import com.cube.nanotimer.util.view.PuzzleIcons;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MainScreenActivity extends DrawerLayoutActivity implements SelectionHandler, ResultListener, TimeChangedHandler {

  private ListView lvHistory;
  private TextView tvCubeType;
  private TextView tvSolveType;
  private TextView tvSolvesCount;
  private ImageView imgCubeType;
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

  private int solvesCount;
  private int currentOrientation;
  private TimesSort timesSort = TimesSort.TIMESTAMP;
  private boolean refreshingHistory;

  private final List<SolveTime> liHistory = new ArrayList<>();
  private HistoryListAdapter historyListAdapter;
  private MenuListAdapter menuListAdapter;
  private SmartCubeChip smartCubeChip;

  private int previousLastItem = 0;

  // History time color gradient (green=fast → white=median → red=slow), recomputed once
  // per data load over the last N solves (N = Options.getColorSampleSize()). Disabled
  // (and the scale left neutral) when Options.isColorHistoryTimes() is off.
  private TimeColorScale timeColorScale;

  private Toast quitMessage;
  private boolean inQuitMode;
  private static final long QUIT_MODE_DELAY = 3000;

  private static final int ID_CUBETYPE = 1;
  private static final int ID_SOLVETYPE = 2;
  private static final int ID_IMPORTEXPORT = 3;
  private static final int ID_LANGUAGE = 4;

  private static final int IMPORT_REQUEST_CODE = 1;

  private static final int REQUEST_READ_PERMISSIONS_CODE = 10;

  /** How many recent solves the sparkline draws, whatever the color sample size is set to. */
  private static final int TREND_SIZE = 50;

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

    tvCubeType = (TextView) findViewById(R.id.tvCubeType);
    tvSolveType = (TextView) findViewById(R.id.tvSolveType);
    tvSolvesCount = (TextView) findViewById(R.id.tvSolvesCount);
    imgCubeType = (ImageView) findViewById(R.id.imgCubeType);
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
        openGraph();
      }
    });

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

    MenuItem smartCubeItem = menu.findItem(R.id.itSmartCube);
    smartCubeChip.bind(smartCubeItem != null ? smartCubeItem.getActionView() : null);

    return super.onCreateOptionsMenu(menu);
  }

  private void openSmartCubeConnect() {
    DialogUtils.showFragment(this, new SmartCubeConnectDialog());
  }

  private void openCubeTypePicker() {
    ArrayList<String> names = new ArrayList<>();
    ArrayList<String> figures = new ArrayList<>();
    ArrayList<Integer> icons = new ArrayList<>();
    for (CubeType cubeType : cubeTypes) {
      names.add(cubeType.getName());
      figures.add(formatCount(cubeTypeCounts.get(cubeType.getId())));
      icons.add(PuzzleIcons.forCubeType(cubeType));
    }
    DialogUtils.showFragment(this, SelectorListDialog.newInstance(ID_CUBETYPE, getString(R.string.cube_type),
      names, figures, icons, cubeTypes.indexOf(curCubeType), null, 0, this));
  }

  private void openSolveTypePicker() {
    ArrayList<String> names = new ArrayList<>();
    ArrayList<String> figures = new ArrayList<>();
    ArrayList<Integer> icons = new ArrayList<>();
    int selectedIndex = -1;
    for (int i = 0; i < solveTypes.size(); i++) {
      SolveType solveType = solveTypes.get(i);
      names.add(Utils.toSolveTypeLocalizedName(this, solveType.getName()));
      figures.add(formatCount(solveTypeCounts.get(solveType.getId())));
      icons.add(solveTypeIcon(solveType));
      if (curSolveType != null && curSolveType.getId() == solveType.getId()) {
        selectedIndex = i;
      }
    }
    DialogUtils.showFragment(this, SelectorListDialog.newInstance(ID_SOLVETYPE, getString(R.string.solve_type),
      names, figures, icons, selectedIndex, getString(R.string.edit_solve_types_dots),
      R.drawable.ic_action_edit, this));
  }

  /** The mark that says what kind of solve type it is, the same one the solve types screen uses. */
  private static int solveTypeIcon(SolveType solveType) {
    if (solveType.isBlind()) {
      return R.drawable.ic_solvetype_blind;
    }
    return solveType.hasSteps() ? R.drawable.ic_solvetype_steps : R.drawable.ic_solvetype_normal;
  }

  private String formatCount(Integer count) {
    return (count == null || count == 0) ? "" : count + " " + getString(R.string.solves);
  }

  private void openGraph() {
    Intent i = new Intent(this, GraphActivity.class);
    i.putExtra("cubeType", curCubeType);
    i.putExtra("solveType", curSolveType);
    startActivity(i);
  }

  private void initHistoryList() {
    historyListAdapter = new HistoryListAdapter(this, R.id.lvHistory, liHistory);
    lvHistory = (ListView) findViewById(R.id.lvHistory);
    lvHistory.setAdapter(historyListAdapter);
    lvHistory.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        DialogUtils.showFragment(MainScreenActivity.this,
          HistoryDetailDialog.newInstance(liHistory.get(i), curCubeType, MainScreenActivity.this));
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
        DialogUtils.showYesNoConfirmation(this, R.string.clear_history_solve_type_confirmation, new YesNoListener() {
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
        DialogUtils.showFragment(this, SelectorFragmentDialog.newInstance(ID_LANGUAGE, items, flagIcons, null, true, this));
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
  }

  @Override
  protected void onPause() {
    super.onPause();
    smartCubeChip.stop();
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
        imgCubeType.setImageResource(PuzzleIcons.forCubeType(curCubeType));
        if (curSolveType != null) {
          tvSolveType.setText(Utils.toSolveTypeLocalizedName(MainScreenActivity.this, curSolveType.getName()));
          imgSolveTypeKind.setImageResource(solveTypeIcon(curSolveType));
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
   * The three cells, whose third one the solve type decides: the personal best normally, the
   * success rate for a blind solve type, and an average of 50 for one timed in steps, which has
   * no best single worth stating beside its splits.
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
            boolean blind = solveType.isBlind();
            setStatCell(R.id.tvStatKeyOne, R.id.tvStatValueOne,
              getString(blind ? R.string.mo3_label : R.string.ao5_label),
              formatTime(blind ? averages.getMeanOf3() : averages.getAvgOf5()));
            setStatCell(R.id.tvStatKeyTwo, R.id.tvStatValueTwo,
              getString(R.string.ao12_label), formatTime(averages.getAvgOf12()));

            if (blind) {
              Integer accuracy = averages.getLifetimeAccuracy();
              setStatCell(R.id.tvStatKeyThree, R.id.tvStatValueThree, getString(R.string.acc_label),
                accuracy == null ? getString(R.string.NA) : accuracy + "%");
            } else if (solveType.hasSteps()) {
              setStatCell(R.id.tvStatKeyThree, R.id.tvStatValueThree,
                getString(R.string.ao50_label), formatTime(averages.getAvgOf50()));
            } else {
              setStatCell(R.id.tvStatKeyThree, R.id.tvStatValueThree,
                getString(R.string.record_label_lifetime), formatTime(averages.getBestOfLifetime()));
            }
            EnterAnimation.stagger(findViewById(R.id.statCellOne),
              findViewById(R.id.statCellTwo), findViewById(R.id.statCellThree));
          }
        });
      }
    });
  }

  private void setStatCell(int keyViewId, int valueViewId, String key, String value) {
    ((TextView) findViewById(keyViewId)).setText(key);
    ((TextView) findViewById(valueViewId)).setText(value);
  }

  private String formatTime(Long time) {
    return FormatterService.INSTANCE.formatSolveTime(time, getString(R.string.NA));
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
    final boolean colorTimes = Options.INSTANCE.isColorHistoryTimes();
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
            timeColorScale.setTimes(colorTimes ? times.subList(0, Math.min(sampleSize, times.size())) : null);
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
    historyListAdapter.notifyDataSetChanged();
    tvNoSolves.setVisibility(liHistory.isEmpty() ? View.VISIBLE : View.GONE);
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
    tvSolvesCount.setText(formatCount(solvesCount)); // nothing rather than "0 solves": the list says that
    if (curSolveType != null) {
      solveTypeCounts.put(curSolveType.getId(), solvesCount);
    }
  }

  private void setCurCubeType(CubeType cubeType) {
    this.curCubeType = cubeType;
    Utils.setCurrentCubeType(this, cubeType);
  }

  private void setCurSolveType(SolveType solveType) {
    this.curSolveType = solveType;
    Utils.setCurrentSolveType(this, solveType);
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
        SolveTime st = liHistory.get(position);
        if (st != null) {
          ((TextView) view.findViewById(R.id.tvDate)).setText(FormatterService.INSTANCE.formatDateTime(st.getTimestamp()));
          TextView tvTime = (TextView) view.findViewById(R.id.tvTime);
          tvTime.setText(FormatterService.INSTANCE.formatSolveTime(st.getTime()));
          // Color the time on the green→white→red gradient (fast→median→slow); DNFs stay
          // gray. Set on every bind so recycled rows never keep a stale color.
          tvTime.setTextColor(timeColorScale.colorFor(st));

          boolean fromCube = st.getSmartcubeMoves() != null;
          view.findViewById(R.id.imgSmartCube).setVisibility(fromCube ? View.VISIBLE : View.GONE);
          view.findViewById(R.id.imgCircle).setVisibility(fromCube ? View.GONE : View.VISIBLE);

          if (st.isPb()) {
            view.findViewById(R.id.imgPb).setVisibility(View.VISIBLE);
          } else {
            view.findViewById(R.id.imgPb).setVisibility(View.GONE);
          }

          if (st.getComment() != null && !st.getComment().trim().equals("")) {
            view.findViewById(R.id.imgComment).setVisibility(View.VISIBLE);
          } else {
            view.findViewById(R.id.imgComment).setVisibility(View.GONE);
          }
        }

        int backgroundResourceId;
        if (position % 2 == 0) {
          backgroundResourceId = R.drawable.listview_item_alternate_1;
        } else {
          backgroundResourceId = R.drawable.listview_item_alternate_2;
        }
        view.setBackgroundResource(backgroundResourceId);
      }
      return view;
    }
  }

}
