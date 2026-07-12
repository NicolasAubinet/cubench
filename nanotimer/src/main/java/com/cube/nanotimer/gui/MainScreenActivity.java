package com.cube.nanotimer.gui;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
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
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.AboutDialog;
import com.cube.nanotimer.gui.widget.HistoryDetailDialog;
import com.cube.nanotimer.gui.widget.ResultListener;
import com.cube.nanotimer.gui.widget.SelectionHandler;
import com.cube.nanotimer.gui.widget.SelectorFragmentDialog;
import com.cube.nanotimer.gui.widget.TimeChangedHandler;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.YesNoListener;
import com.cube.nanotimer.util.exportimport.ErrorListener;
import com.cube.nanotimer.util.exportimport.csvimport.CSVImporter;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.TimeColorScale;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.NameHolder;
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
import java.util.Iterator;
import java.util.List;

public class MainScreenActivity extends DrawerLayoutActivity implements SelectionHandler, ResultListener, TimeChangedHandler {

  private Spinner spCubeType;
  private Spinner spSolveType;
  private ListView lvHistory;
  private TextView tvSolvesCount;
  private TextView tvHistory;
  private Button buStart;

  private CubeType curCubeType;
  private SolveType curSolveType;
  private final List<CubeType> cubeTypes = new ArrayList<>();
  private final List<SolveType> solveTypes = new ArrayList<>();
  private final List<String> spinnerSolveTypeNames = new ArrayList<>();
  private NameHolderSpinnerAdapter cubeTypesSpinnerAdapter;
  private ArrayAdapter<String> solveTypesSpinnerAdapter;

  private int solvesCount;
  private TimesSort timesSort = TimesSort.TIMESTAMP;
  private boolean refreshingHistory;

  private final List<SolveTime> liHistory = new ArrayList<>();
  private HistoryListAdapter historyListAdapter;
  private MenuListAdapter menuListAdapter;

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

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    App.INSTANCE.setContext(this);
    Utils.updateContextWithPrefsLocale(this); // because ad provider somehow re-initializes the context

    setContentView(R.layout.mainscreen_screen);

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
    spCubeType = (Spinner) findViewById(R.id.spCubeType);
    cubeTypesSpinnerAdapter = new NameHolderSpinnerAdapter(this, R.id.spCubeType, cubeTypes);
    cubeTypesSpinnerAdapter.setDropDownViewResource(R.layout.spinner_item);
    spCubeType.setAdapter(cubeTypesSpinnerAdapter);
    spCubeType.setOnItemSelectedListener(new OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        itemSelected(ID_CUBETYPE, i);
      }

      @Override
      public void onNothingSelected(AdapterView<?> adapterView) {
      }
    });

    spSolveType = (Spinner) findViewById(R.id.spSolveType);
    solveTypesSpinnerAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, spinnerSolveTypeNames);
    solveTypesSpinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
    spSolveType.setAdapter(solveTypesSpinnerAdapter);
    spSolveType.setOnItemSelectedListener(new OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        itemSelected(ID_SOLVETYPE, i);
      }

      @Override
      public void onNothingSelected(AdapterView<?> adapterView) {
      }
    });

    tvSolvesCount = (TextView) findViewById(R.id.tvSolvesCount);
    tvHistory = (TextView) findViewById(R.id.tvHistory);

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

    return super.onCreateOptionsMenu(menu);
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
                    historyListAdapter.notifyDataSetChanged();
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
        Intent i = new Intent(this, GraphActivity.class);
        i.putExtra("cubeType", curCubeType);
        i.putExtra("solveType", curSolveType);
        startActivity(i);
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
        ArrayList<Integer> flagIcons = new ArrayList<>(Arrays.asList(R.drawable.flag_uk, R.drawable.flag_france, R.drawable.flag_spain, R.drawable.flag_brazil));
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

    invalidateOptionsMenu();

    refreshCubeTypes();

    setSortMode(TimesSort.TIMESTAMP);
  }

  private void refreshDataSet(final ArrayAdapter arrayAdapter) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        arrayAdapter.notifyDataSetChanged();
      }
    });
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

          final CubeType newCurCubeType = newCubeType != null ? newCubeType : defaultCubeType != null ? defaultCubeType : cubeTypes.get(0);
          setCurCubeType(newCurCubeType);

          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              refreshDataSet(cubeTypesSpinnerAdapter);
              int selectedIndex = cubeTypes.indexOf(newCurCubeType);
              if (selectedIndex >= 0 && selectedIndex < spCubeType.getCount()) {
                spCubeType.setSelection(selectedIndex);
              }
            }
          });
        } else {
          setCurCubeType(null);
        }
        refreshDataSet(cubeTypesSpinnerAdapter);
        refreshSolveTypes();
      }
    });
  }

  private void refreshSolveTypes() {
    if (curCubeType != null) {
      App.INSTANCE.getService().getSolveTypes(curCubeType, new DataCallback<List<SolveType>>() {
        @Override
        public void onData(List<SolveType> data) {
          solveTypes.clear();
          solveTypes.addAll(data);
          refreshSpinnerSolveTypeNames();
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

            setCurSolveType(newCurSolveType);
            final SolveType finalCurSolveType = newCurSolveType;

            runOnUiThread(new Runnable() {
              @Override
              public void run() {
                refreshDataSet(solveTypesSpinnerAdapter);
                int selectedIndex = solveTypes.indexOf(finalCurSolveType);
                if (selectedIndex >= 0 && selectedIndex < spSolveType.getCount()) {
                  spSolveType.setSelection(selectedIndex);
                }
              }
            });
          } else {
            setCurSolveType(null);
            refreshDataSet(solveTypesSpinnerAdapter);
          }
          refreshHistory();
        }
      });
    } else {
      setCurSolveType(null);
      solveTypes.clear();
      refreshSpinnerSolveTypeNames();
      refreshDataSet(solveTypesSpinnerAdapter);
      refreshHistory();
    }
  }

  private void refreshSpinnerSolveTypeNames() {
    spinnerSolveTypeNames.clear();
    for (SolveType solveType : solveTypes) {
      String name = solveType.getName();
      spinnerSolveTypeNames.add(Utils.toSolveTypeLocalizedName(this, name));
    }
    spinnerSolveTypeNames.add(getString(R.string.edit_solve_types_dots));
  }

  public void refreshHistory() {
    previousLastItem = 0;
    refreshTimeColorAnchors();
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
              historyListAdapter.notifyDataSetChanged();
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
          liHistory.clear();
          historyListAdapter.notifyDataSetChanged();
        }
      });
    }
  }

  /**
   * Recomputes the gradient scale over the last getColorSampleSize() solves. Runs once per
   * data load so getView() coloring stays cheap while scrolling. When history coloring is
   * disabled the scale is left neutral, so times render in the default (uncolored) text color.
   */
  private void refreshTimeColorAnchors() {
    if (curSolveType == null || !Options.INSTANCE.isColorHistoryTimes()) {
      timeColorScale.setTimes(null);
      return;
    }
    App.INSTANCE.getService().getLastSolveTimes(curSolveType, Options.INSTANCE.getColorSampleSize(), new DataCallback<List<Long>>() {
      @Override
      public void onData(final List<Long> times) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            timeColorScale.setTimes(times);
            if (historyListAdapter != null) {
              historyListAdapter.notifyDataSetChanged();
            }
          }
        });
      }
    });
  }

  @Override
  public void onResult(Object... params) {
    refreshHistory();
    refreshSolveTypes();
  }

  private void setSortMode(TimesSort timesSort) {
    if (timesSort == TimesSort.TIMESTAMP) {
      tvHistory.setText(R.string.history);
    } else {
      tvHistory.setText(R.string.best_times);
    }
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
        setCurCubeType(cubeTypes.get(position));
        refreshSolveTypes();
      } else if (id == ID_SOLVETYPE) {
        if (position >= 0 && position < solveTypes.size()) {
          setCurSolveType(solveTypes.get(position));
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
    tvSolvesCount.setText(String.valueOf(solvesCount) + " " + getString(R.string.solves));
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
  public void onTimeDeleted(SolveTime solveTime) {
    for (Iterator<SolveTime> it = liHistory.iterator(); it.hasNext(); ) {
      SolveTime st = it.next();
      if (st.getId() == solveTime.getId()) {
        it.remove();
        historyListAdapter.notifyDataSetChanged();
        setSolvesCount(solvesCount - 1);
        break;
      }
    }
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
            historyListAdapter.notifyDataSetChanged();
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

  private class NameHolderSpinnerAdapter<T extends NameHolder> extends ArrayAdapter<T> {
    private LayoutInflater inflater;
    private List<T> nameHolders;

    public NameHolderSpinnerAdapter(Context context, int resource, List<T> objects) {
      super(context, resource, objects);
      this.inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      this.nameHolders = objects;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      return getView(R.layout.spinner_item, position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
      return getView(R.layout.spinner_dropdown_item, position, convertView, parent);
    }

    private View getView(int itemResourceId, int position, View convertView, ViewGroup parent) {
      TextView view = (TextView) convertView;
      if (view == null) {
        view = (TextView) inflater.inflate(itemResourceId, parent, false);
      }

      T nameHolder = nameHolders.get(position);
      String name = nameHolder.getName();
      view.setText(name);

      return view;
    }
  }

}
