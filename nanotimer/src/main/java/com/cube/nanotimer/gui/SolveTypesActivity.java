package com.cube.nanotimer.gui;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.AddStepsDialog;
import com.cube.nanotimer.gui.widget.SelectionHandler;
import com.cube.nanotimer.gui.widget.SelectorFragmentDialog;
import com.cube.nanotimer.gui.widget.StepsCreator;
import com.cube.nanotimer.gui.widget.dialog.FieldCreator;
import com.cube.nanotimer.gui.widget.dialog.FieldEditor;
import com.cube.nanotimer.gui.widget.dialog.SolveTypeAddDialog;
import com.cube.nanotimer.scrambler.ScramblerService;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.YesNoListener;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.SolveHistory;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.SolveTypeStep;
import com.cube.nanotimer.vo.TimesSort;
import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;
import com.mobeta.android.dslv.DragSortListView.DropListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SolveTypesActivity extends NanoTimerActivity implements SelectionHandler, FieldEditor, FieldCreator, StepsCreator {

  private DragSortListView lvSolveTypes;
  private SolveTypeListAdapter adapter;
  private List<SolveType> liSolveTypes = new ArrayList<SolveType>();

  private List<CubeType> cubeTypes;
  private CubeType curCubeType;

  private static final int ACTION_EDIT = 0;
  private static final int ACTION_DELETE = 1;
  private static final int ACTION_CREATESTEPS = 2;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.solvetypes_screen);
    getSupportActionBar().setTitle("");

    initViews();

    CubeType cubeType = (CubeType) getIntent().getSerializableExtra("cubeType");
    if (cubeType != null) {
      setCubeType(cubeType);
    } else {
      App.INSTANCE.getService().getCubeTypes(true, new DataCallback<List<CubeType>>() {
        @Override
        public void onData(List<CubeType> data) {
          cubeTypes = data;
          if (cubeTypes != null) {
            ArrayList<String> types = new ArrayList<String>();
            for (CubeType t : cubeTypes) {
              types.add(t.getName());
            }
            DialogUtils.showFragment(SolveTypesActivity.this,
              SelectorFragmentDialog.newInstance(0, types, getString(R.string.choose_cube_type), false, SolveTypesActivity.this));
          } else {
            finish();
          }
        }
      });
    }
  }

  private void initViews() {
    lvSolveTypes = (DragSortListView) findViewById(R.id.lvSolveTypes);
    adapter = new SolveTypeListAdapter(this, R.id.lvSolveTypes, liSolveTypes);
    lvSolveTypes.setDropListener(new DropListener() {
      @Override
      public void drop(int from, int to) {
        if (from != to) {
          SolveType item = adapter.getItem(from);
          liSolveTypes.remove(item);
          liSolveTypes.add(to, item);
          App.INSTANCE.getService().saveSolveTypesOrder(liSolveTypes, null);
          refreshList();
        }
      }
    });
    lvSolveTypes.setAdapter(adapter);

    DragSortController controller = new DragSortController(lvSolveTypes);
    controller.setDragHandleId(R.id.imgMove);
    lvSolveTypes.setFloatViewManager(controller);
    lvSolveTypes.setOnTouchListener(controller);

    lvSolveTypes.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        registerForContextMenu(lvSolveTypes);
        openContextMenu(view);
        unregisterForContextMenu(lvSolveTypes);
      }
    });

    if (curCubeType != null) {
      getSupportActionBar().setTitle(curCubeType.getName());
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    setContentView(R.layout.solvetypes_screen);
    initViews();
  }

  private void showAddDialog() {
    SolveTypeAddDialog dialog = SolveTypeAddDialog.newInstance(this, curCubeType);
    DialogUtils.showFragment(this, dialog);
  }

  @Override
  public boolean onContextItemSelected(MenuItem menuItem) {
    final int position = ((AdapterContextMenuInfo) menuItem.getMenuInfo()).position;
    if (menuItem.getItemId() == ACTION_EDIT) {
      SolveType solveType = liSolveTypes.get(position);
      String solveTypeName = Utils.toSolveTypeLocalizedName(this, solveType.getName());
      String scrambleTypeName = (solveType.getScrambleType() != null) ? solveType.getScrambleType().getName() : null;
      SolveTypeAddDialog editDialog =
          SolveTypeAddDialog.newInstanceForEdit(this, curCubeType, position, solveTypeName, solveType.isBlind(), scrambleTypeName);
      DialogUtils.showFragment(this, editDialog);
    } else if (menuItem.getItemId() == ACTION_DELETE) {
      String solveTypeName = Utils.toSolveTypeLocalizedName(this, liSolveTypes.get(position).getName());
      DialogUtils.showYesNoConfirmation(this, getString(R.string.delete_solve_type_confirmation, solveTypeName),
          new YesNoListener() {
            @Override
            public void onYes() {
              App.INSTANCE.getService().deleteSolveType(liSolveTypes.get(position), new DataCallback<Void>() {
                @Override
                public void onData(Void data) {
                  liSolveTypes.remove(position);
                  refreshList();
                }
              });
            }
          });
    } else if (menuItem.getItemId() == ACTION_CREATESTEPS) {
      App.INSTANCE.getService().getPagedHistory(liSolveTypes.get(position), TimesSort.TIMESTAMP, new DataCallback<SolveHistory>() {
        @Override
        public void onData(final SolveHistory data) {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              if (data.getSolveTimes().isEmpty()) {
                DialogUtils.showFragment(SolveTypesActivity.this, AddStepsDialog.newInstance(SolveTypesActivity.this, position));
              } else {
                DialogUtils.showYesNoConfirmation(SolveTypesActivity.this, R.string.solvetype_has_times_addsteps, new YesNoListener() {
                  @Override
                  public void onYes() {
                    DialogUtils.showFragment(SolveTypesActivity.this, AddStepsDialog.newInstance(SolveTypesActivity.this, position));
                  }
                });
              }
            }
          });
        }
      });
    }
    return super.onContextItemSelected(menuItem);
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    if (v.getId() == R.id.lvSolveTypes) {
      int position = ((AdapterContextMenuInfo) menuInfo).position;
      menu.setHeaderTitle(R.string.action);
      menu.add(v.getId(), ACTION_EDIT, 0, R.string.edit);
      menu.add(v.getId(), ACTION_DELETE, 0, R.string.delete);
      if (position >= 0 && position < liSolveTypes.size() && !liSolveTypes.get(position).hasSteps()) {
        menu.add(v.getId(), ACTION_CREATESTEPS, 0, R.string.add_steps);
      }
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.solvetypes_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.itAdd:
        showAddDialog();
        break;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void itemSelected(int id, int position) {
    if (position < 0 || position >= cubeTypes.size()) {
      finish();
      return;
    }
    setCubeType(cubeTypes.get(position));
  }

  private void setCubeType(CubeType cubeType) {
    curCubeType = cubeType;
    App.INSTANCE.getService().getSolveTypes(curCubeType, new DataCallback<List<SolveType>>() {
      @Override
      public void onData(List<SolveType> data) {
        liSolveTypes.clear();
        liSolveTypes.addAll(data);
        refreshList();
      }
    });
    if (curCubeType != null) {
      getSupportActionBar().setTitle(curCubeType.getName());
    }
  }

  @Override
  public boolean editField(int index, String name, Properties props) {
    name = name.trim();
    if (!checkSolveTypeName(name, index)) {
      return false;
    }
    SolveType oldSolveType = liSolveTypes.get(index);
    boolean blindMode = Boolean.valueOf(props.getProperty(SolveTypeAddDialog.KEY_BLD, String.valueOf(false)));
    ScrambleType scrambleType = parseScrambleType(props);

    // Toggling blind changes what the cached avg columns mean, so the service must recompute them.
    boolean blindChanged = (oldSolveType.isBlind() != blindMode);

    SolveType updatedSolveType = new SolveType(oldSolveType.getId(), name, blindMode, scrambleType, oldSolveType.getCubeTypeId());
    updatedSolveType.setSteps(oldSolveType.getSteps());
    liSolveTypes.set(index, updatedSolveType);

    App.INSTANCE.getService().updateSolveType(updatedSolveType, blindChanged, new DataCallback<Void>() {
      @Override
      public void onData(Void data) {
        refreshList();
      }
    });
    return true;
  }

  // Resolves the dialog's chosen scramble type (KEY_SCRAMBLE_TYPE is a spinner position; 0 = none),
  // warming the random-state cache when a non-default type is first used.
  private ScrambleType parseScrambleType(Properties props) {
    int scrambleTypeIndex = Integer.parseInt(props.getProperty(SolveTypeAddDialog.KEY_SCRAMBLE_TYPE, String.valueOf(-1)));
    if (scrambleTypeIndex > 0) {
      ScrambleType scrambleType = curCubeType.getAvailableScrambleTypes()[scrambleTypeIndex];
      if (!scrambleType.isDefault()) {
        if (curCubeType.addUsedScrambleType(scrambleType)) {
          ScramblerService.INSTANCE.checkScrambleCaches();
        }
      }
      return scrambleType;
    }
    return null;
  }

  @Override
  public boolean createField(String name, Properties props) {
    name = name.trim();
    if (!checkSolveTypeName(name, null)) {
      return false;
    }
    boolean blindMode = Boolean.valueOf(props.getProperty(SolveTypeAddDialog.KEY_BLD, String.valueOf(false)));
    ScrambleType scrambleType = parseScrambleType(props);
    SolveType st = new SolveType(name, blindMode, scrambleType, curCubeType.getId());

    liSolveTypes.add(st);
    App.INSTANCE.getService().addSolveType(st, new DataCallback<Integer>() {
      @Override
      public void onData(Integer data) {
        refreshList();
      }
    });
    return true;
  }

  private boolean checkSolveTypeName(String name, Integer index) {
    if ("".equals(name)) {
      return false;
    }

    Character forbiddenChar = Utils.checkForForbiddenCharacters(name);
    if (forbiddenChar != null) {
      DialogUtils.showInfoMessage(this, getString(R.string.name_contains_forbidden_char, forbiddenChar));
      return false;
    }

//    if (Utils.isDefaultSolveTypeName(name)) {
//      DialogUtils.showInfoMessage(this, R.string.solve_type_name_reserved);
//      return false;
//    }

    for (int i = 0; i < liSolveTypes.size(); i++) {
      String solveTypeName = liSolveTypes.get(i).getName();
      for (String solveTypeNameVariant : App.INSTANCE.getDynamicTranslations().getSolveTypeNameVariants(solveTypeName)) {
        if (solveTypeNameVariant.equals(name)) {
          if (index == null || i != index) {
            DialogUtils.showInfoMessage(this, R.string.solve_type_already_exists);
            return false;
          } else {
            // The name was not changed, do nothing
            return true;
          }
        }
      }
    }
    return true;
  }

  @Override
  public void addSteps(final List<String> stepNames, final int pos) {
    // delete existing times (if there are some) before adding the steps
    App.INSTANCE.getService().deleteHistory(liSolveTypes.get(pos), new DataCallback<Void>() {
      @Override
      public void onData(Void data) {
        SolveTypeStep[] steps = new SolveTypeStep[stepNames.size()];
        for (int i = 0; i < stepNames.size(); i++) {
          SolveTypeStep step = new SolveTypeStep();
          step.setName(stepNames.get(i));
          steps[i] = step;
        }
        SolveType solveType = liSolveTypes.get(pos);
        solveType.setSteps(steps);
        App.INSTANCE.getService().addSolveTypeSteps(solveType, new DataCallback<Void>() {
          @Override
          public void onData(Void data) {
            refreshList();
          }
        });
      }
    });
  }

  private void refreshList() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        adapter.notifyDataSetChanged();
      }
    });
  }

  private class SolveTypeListAdapter extends ArrayAdapter<SolveType> {

    public SolveTypeListAdapter(Context context, int id, List<SolveType> list) {
      super(context, id, list);
    }

    public View getView(final int position, View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        view = inflater.inflate(R.layout.solvetypes_list_item, parent, false);
      }

      if (position >= 0 && position < liSolveTypes.size()) {
        SolveType solveType = liSolveTypes.get(position);
        if (solveType != null) {
          TextView tvName = (TextView) view.findViewById(R.id.tvSolveType);
          String solveTypeName = Utils.toSolveTypeLocalizedName(getContext(), solveType.getName());
          tvName.setText(solveTypeName);

          TextView tvAdditionalInfo = (TextView) view.findViewById(R.id.tvAdditionalInfo);
          if (solveType.hasSteps()) {
            StringBuilder stepsCount = new StringBuilder();
            stepsCount.append("(").append(solveType.getSteps().length).append(" ").append(getString(R.string.steps)).append(")");
            tvAdditionalInfo.setText(stepsCount.toString());
          } else if (solveType.isBlind()) {
            tvAdditionalInfo.setText(R.string.blind);
          } else {
            tvAdditionalInfo.setText("");
          }
        }
      }
      return view;
    }
  }

}
