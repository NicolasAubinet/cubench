package com.cube.nanotimer.gui;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import androidx.core.content.FileProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.TextView;
import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.ExportActivity.ListItem.Type;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.exportimport.csvexport.CSVGenerator;
import com.cube.nanotimer.util.exportimport.csvexport.ExportCSVGenerator;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.FileUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ExportResult;
import com.cube.nanotimer.vo.SolveType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExportActivity extends NanoTimerActivity {

  private ExportListAdapter adapter;
  private final List<ListItem> liItems = new ArrayList<ListItem>();

  private CheckBox cbLimit;
  private EditText tfLimit;

  private static final String PREFS_NAME = "export";
  private static final String EXPORT_LIMIT_KEY = "limit";
  private static final String EXPORT_FILE_NAME = "export.csv";
  private static final String EXPORT_MIME_TYPE = "text/csv";
  private static final int REQ_CREATE_DOCUMENT = 1;

  // CSV waiting to be copied to the destination picked by the system document picker
  private File pendingSaveFile;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.export_screen);

    setTitle(R.string.export_times);
    initViews();

    App.INSTANCE.getService().getCubeTypes(false, new DataCallback<List<CubeType>>() {
      @Override
      public void onData(final List<CubeType> data) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            liItems.add(new ListItem(Type.SELECTALL, 0, getString(R.string.select_all), false));
            for (CubeType ct : data) {
              synchronized (liItems) {
                liItems.add(new ListItem(Type.CUBETYPE, ct.getId(), ct.getName(), false));
              }
              App.INSTANCE.getService().getSolveTypes(ct, new DataCallback<List<SolveType>>() {
                @Override
                public void onData(final List<SolveType> data) {
                  runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                      addSolveTypesToList(data);
                    }
                  });
                }
              });
            }
          }
        });
      }
    });
  }

  private void initViews() {
    ListView lvItems = (ListView) findViewById(R.id.lvItems);
    adapter = new ExportListAdapter(this, R.id.lvItems, liItems);
    lvItems.setAdapter(adapter);

    tfLimit = (EditText) findViewById(R.id.tfLimit);

    cbLimit = (CheckBox) findViewById(R.id.cbLimit);
    cbLimit.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (b) {
          tfLimit.setText(String.valueOf(getLastExportLimit()));
          tfLimit.setEnabled(true);
        } else {
          tfLimit.setText(R.string.no_limit);
          tfLimit.setEnabled(false);
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
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    boolean timesLimitChecked = cbLimit.isChecked();
    String timesLimitText = tfLimit.getText().toString();

    setContentView(R.layout.export_screen);
    initViews();

    cbLimit.setChecked(timesLimitChecked);
    tfLimit.setText(timesLimitText);
  }

  private void export(final boolean share) {
    List<Integer> solveTypeIds = new ArrayList<Integer>();
    synchronized (liItems) {
      for (ListItem it : liItems) {
        if (it.isSelected() && it.getType() == Type.SOLVETYPE) {
          solveTypeIds.add(it.getId());
        }
      }
    }
    if (solveTypeIds.isEmpty()) {
      DialogUtils.showInfoMessage(this, R.string.select_at_least_one_solve_type);
      return;
    }
    int limit = -1;
    if (cbLimit.isChecked()) {
      try {
        limit = Integer.parseInt(tfLimit.getText().toString());
        saveLastExportLimit(limit);
      } catch (NumberFormatException e) {
        Log.e("[NanoTimer]", "Export limit parsing exception");
      }
    }

    final ProgressDialog progressDialog = new ProgressDialog(this);
    progressDialog.setMessage(getString(R.string.exporting_history));
    progressDialog.setIndeterminate(true);
    progressDialog.setCancelable(false);
    progressDialog.show();
    App.INSTANCE.getService().getExportFile(solveTypeIds, limit, new DataCallback<List<ExportResult>>() {
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

  private class ExportListAdapter extends ArrayAdapter<ListItem> {

    public ExportListAdapter(Context context, int textViewResourceId, List<ListItem> objects) {
      super(context, textViewResourceId, objects);
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
      LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      View view = inflater.inflate(R.layout.export_listitem, parent, false); // inflating every time to avoid bugs in items on old htc (looks like caching bugs when selecting rows)

      synchronized (liItems) {
        if (position >= 0 && position < liItems.size()) {
          ListItem st = liItems.get(position);
          if (st != null) {
            ((TextView) view.findViewById(R.id.tvTitle)).setText(st.getName());
            ImageView imgTab = (ImageView) view.findViewById(R.id.imgTab);
            View spacerView = view.findViewById(R.id.spacerView);
            LayoutParams layoutParams = (LayoutParams) spacerView.getLayoutParams();
            if (st.getType() == Type.CUBETYPE
            ||  st.getType() == Type.SELECTALL) {
              layoutParams.width = 15;
              imgTab.setVisibility(View.GONE);
            } else if (st.getType() == Type.SOLVETYPE) {
              layoutParams.width = 30;
              imgTab.setVisibility(View.VISIBLE);
            }
            spacerView.setLayoutParams(layoutParams);

            final CheckBox cbSelected = (CheckBox) view.findViewById(R.id.cbSelected);
            cbSelected.setChecked(st.isSelected());
            cbSelected.setOnCheckedChangeListener(new OnCheckedChangeListener() {
              @Override
              public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                checkedStatusChanged(position, b);
              }
            });

            view.setOnClickListener(new OnClickListener() {
              @Override
              public void onClick(View view) {
                checkedStatusChanged(position, !cbSelected.isChecked());
              }
            });
          }
        }
      }
      return view;
    }
  }

  public void checkedStatusChanged(int i, boolean checked) {
    synchronized (liItems) {
      ListItem it = liItems.get(i);
      it.setSelected(checked);
      if (it.getType() == Type.CUBETYPE) {
        for (int j = i + 1; j < liItems.size() && liItems.get(j).getType() == Type.SOLVETYPE; j++) {
          liItems.get(j).setSelected(checked);
        }
      } else if (it.getType() == Type.SOLVETYPE) {
        boolean allSolveTypesSelected = true;
        ListItem parentCubeType = getCubeTypeOf(it);
        List<ListItem> solveTypes = getSolveTypesOf(parentCubeType);
        for (ListItem st : solveTypes) {
          if (!st.isSelected()) {
            allSolveTypesSelected = false;
            break;
          }
        }
        if (allSolveTypesSelected) {
          parentCubeType.setSelected(true);
        }
      } else if (it.getType() == Type.SELECTALL) {
        for (ListItem item : liItems) {
          item.setSelected(checked);
        }
      }
      adapter.notifyDataSetChanged();
    }
  }

  public ListItem getCubeTypeOf(ListItem solveTypeItem) {
    if (solveTypeItem.getType() == Type.CUBETYPE) {
      return solveTypeItem;
    }
    ListItem cubeType = null;
    // Search the solveTypeItem index
    int ind = getListItemIndex(solveTypeItem);
    if (ind >= 0) {
      // Search for parent cube type
      boolean found = false;
      synchronized (liItems) {
        for (int i = ind - 1; i >= 0 && !found; i--) {
          if (liItems.get(i).getType() == Type.CUBETYPE) {
            found = true;
            cubeType = liItems.get(i);
          }
        }
      }
    }
    return cubeType;
  }

  private List<ListItem> getSolveTypesOf(ListItem cubeTypeItem) {
    if (cubeTypeItem.getType() == Type.SOLVETYPE) {
      return Collections.emptyList();
    }
    List<ListItem> solveTypes = null;
    int ind = getListItemIndex(cubeTypeItem);
    if (ind >= 0) {
      solveTypes = new ArrayList<ListItem>();
      synchronized (liItems) {
        for (int i = ind + 1; i < liItems.size() && liItems.get(i).getType() != Type.CUBETYPE; i++) {
          solveTypes.add(liItems.get(i));
        }
      }
    }
    return solveTypes;
  }

  private int getListItemIndex(ListItem listItem) {
    synchronized (liItems) {
      for (int i = 0; i < liItems.size(); i++) {
        if (liItems.get(i).equals(listItem)) {
          return i;
        }
      }
    }
    return -1;
  }

  private void addSolveTypesToList(List<SolveType> solveTypes) {
    if (solveTypes != null && solveTypes.size() > 0) {
      synchronized (liItems) {
        int cubeTypeId = solveTypes.get(0).getCubeTypeId();
        for (int i = 0; i < liItems.size(); i++) {
          ListItem itCubeType = liItems.get(i);
          if (itCubeType.getType() == Type.CUBETYPE && itCubeType.getId() == cubeTypeId) {
            // found corresponding cube type
            for (int j = solveTypes.size() - 1; j >= 0; j--) {
              SolveType solveType = solveTypes.get(j);
              String localizedSolveTypeName = Utils.toSolveTypeLocalizedName(this, solveType.getName());
              liItems.add(i + 1, new ListItem(Type.SOLVETYPE, solveType.getId(), localizedSolveTypeName, solveType.hasSteps()));
            }
            break;
          }
        }
        adapter.notifyDataSetChanged();
      }
    }
  }

  private int getLastExportLimit() {
    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
    return prefs.getInt(EXPORT_LIMIT_KEY, 1000);
  }

  private void saveLastExportLimit(int limit) {
    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
    Editor editor = prefs.edit();
    editor.putInt(EXPORT_LIMIT_KEY, limit);
    editor.apply();
  }

  static class ListItem {
    enum Type {CUBETYPE, SOLVETYPE, SELECTALL}

    private Type type;
    private int id;
    private String name;
    private boolean hasSteps;
    private boolean selected;

    private ListItem(Type type, int id, String name, boolean hasSteps) {
      this.type = type;
      this.id = id;
      this.name = name;
      this.hasSteps = hasSteps;
      this.selected = false;
    }

    public Type getType() {
      return type;
    }

    public int getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public boolean isHasSteps() {
      return hasSteps;
    }

    public boolean isSelected() {
      return selected;
    }

    public void setSelected(boolean selected) {
      this.selected = selected;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ListItem)) return false;

      ListItem listItem = (ListItem) o;
      if (id != listItem.id) return false;
      if (selected != listItem.selected) return false;
      if (name != null ? !name.equals(listItem.name) : listItem.name != null) return false;
      if (type != listItem.type) return false;
      return true;
    }
  }

}
