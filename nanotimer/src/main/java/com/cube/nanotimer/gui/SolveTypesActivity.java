package com.cube.nanotimer.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
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
import com.cube.nanotimer.util.view.SolveTypeIcons;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.SolveHistory;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.SolveTypeStep;
import com.cube.nanotimer.vo.TimerQuickAction;
import com.cube.nanotimer.vo.TimesSort;
import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;
import com.mobeta.android.dslv.DragSortListView.DropListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SolveTypesActivity extends NanoTimerActivity implements SelectionHandler, FieldEditor, FieldCreator, StepsCreator {

  private DragSortListView lvSolveTypes;
  private View emptyView;
  private View listHint;
  private SolveTypeListAdapter adapter;
  private List<SolveType> liSolveTypes = new ArrayList<SolveType>();
  private boolean solveTypesLoaded;

  private List<CubeType> cubeTypes;
  private CubeType curCubeType;

  private static final int ACTION_EDIT = 0;
  private static final int ACTION_DELETE = 1;
  private static final int ACTION_CREATESTEPS = 2;

  /**
   * How much of a kind's colour the tile behind its mark, and the chip that names it, carry. Higher
   * than the picker's, because a row here is a raised card rather than a dark sheet: the same wash
   * on the lighter surface all but disappears.
   */
  private static final int TILE_ALPHA = 0x45;
  private static final int CHIP_ALPHA = 0x38;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.solvetypes_screen);
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
    emptyView = findViewById(R.id.emptyView);
    listHint = findViewById(R.id.tvListHint);
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
    // The dragged row is a rounded card, so its float view must not sit on the default black slab.
    controller.setBackgroundColor(Color.TRANSPARENT);
    lvSolveTypes.setFloatViewManager(controller);
    lvSolveTypes.setOnTouchListener(controller);

    lvSolveTypes.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        showActionsDialog(i);
      }
    });

    setTitles();
    refreshList();
  }

  // The screen is one puzzle's list, so the puzzle names the bar and the screen explains itself under it.
  private void setTitles() {
    getSupportActionBar().setTitle(curCubeType != null ? curCubeType.getName() : "");
    getSupportActionBar().setSubtitle(curCubeType != null ? getString(R.string.solve_types) : null);
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

  // A row's actions, headed by the solve type they act on so the list says what is about to change.
  private void showActionsDialog(final int position) {
    if (position < 0 || position >= liSolveTypes.size()) {
      return;
    }
    SolveType solveType = liSolveTypes.get(position);
    final List<SolveTypeAction> actions = new ArrayList<SolveTypeAction>();
    actions.add(new SolveTypeAction(ACTION_EDIT, R.string.edit, R.drawable.ic_action_edit, false));
    if (!solveType.hasSteps()) {
      actions.add(new SolveTypeAction(ACTION_CREATESTEPS, R.string.add_steps, R.drawable.ic_solvetype_steps, false));
    }
    actions.add(new SolveTypeAction(ACTION_DELETE, R.string.delete, R.drawable.ic_menu_delete, true));

    new AlertDialog.Builder(this, R.style.NanoTimerDialogTheme)
        .setTitle(Utils.toSolveTypeLocalizedName(this, solveType.getName()))
        .setAdapter(new ActionListAdapter(this, actions), new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            actionSelected(actions.get(which).id, position);
          }
        })
        .show();
  }

  private void actionSelected(int action, final int position) {
    if (action == ACTION_EDIT) {
      SolveType solveType = liSolveTypes.get(position);
      String solveTypeName = Utils.toSolveTypeLocalizedName(this, solveType.getName());
      String scrambleTypeName = (solveType.getScrambleType() != null) ? solveType.getScrambleType().getName() : null;
      SolveTypeAddDialog editDialog = SolveTypeAddDialog.newInstanceForEdit(this, curCubeType, position,
          solveTypeName, solveType.isBlind(), solveType.hasInspection(), scrambleTypeName,
          solveType.getQuickAction(), solveType.getMethodOverride());
      DialogUtils.showFragment(this, editDialog);
    } else if (action == ACTION_DELETE) {
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
    } else if (action == ACTION_CREATESTEPS) {
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
        solveTypesLoaded = true;
        refreshList();
      }
    });
    setTitles();
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
    updatedSolveType.setInspection(parseInspection(props));
    updatedSolveType.setMethod(parseMethod(props));
    updatedSolveType.setQuickAction(parseQuickAction(props));
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

  // Whether the timer inspects before this solve type's solves (a blind one never does, whatever is stored).
  private boolean parseInspection(Properties props) {
    return Boolean.valueOf(props.getProperty(SolveTypeAddDialog.KEY_INSPECTION, String.valueOf(true)));
  }

  // The method this solve type overrides the preferred one with (null to follow it).
  private CubeMethod parseMethod(Properties props) {
    return CubeMethod.fromCode(props.getProperty(SolveTypeAddDialog.KEY_METHOD, ""));
  }

  // Which timer menu action the solve type overrides the default one with (null to follow it).
  private TimerQuickAction parseQuickAction(Properties props) {
    String id = props.getProperty(SolveTypeAddDialog.KEY_QUICK_ACTION, "");
    return id.isEmpty() ? null : TimerQuickAction.fromId(Integer.parseInt(id));
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
    st.setInspection(parseInspection(props));
    st.setMethod(parseMethod(props));
    st.setQuickAction(parseQuickAction(props));

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
        if (!solveTypesLoaded) {
          return; // an empty list before the first load is "not read yet", not "none"
        }
        // The hint only makes sense next to rows, so it goes with the list.
        boolean empty = liSolveTypes.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listHint.setVisibility(empty ? View.GONE : View.VISIBLE);
        lvSolveTypes.setVisibility(empty ? View.GONE : View.VISIBLE);
      }
    });
  }

  private static class SolveTypeAction {
    private final int id;
    private final int labelId;
    private final int iconId;
    private final boolean danger;

    private SolveTypeAction(int id, int labelId, int iconId, boolean danger) {
      this.id = id;
      this.labelId = labelId;
      this.iconId = iconId;
      this.danger = danger;
    }
  }

  private static class ActionListAdapter extends ArrayAdapter<SolveTypeAction> {

    private ActionListAdapter(Context context, List<SolveTypeAction> actions) {
      super(context, R.layout.solvetype_action_item, actions);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        view = inflater.inflate(R.layout.solvetype_action_item, parent, false);
      }

      SolveTypeAction action = getItem(position);
      int color = ContextCompat.getColor(getContext(), action.danger ? R.color.danger_text : R.color.white);
      ImageView icon = (ImageView) view.findViewById(R.id.imgIcon);
      icon.setImageResource(action.iconId);
      icon.setColorFilter(color);
      TextView label = (TextView) view.findViewById(R.id.tvText);
      label.setText(action.labelId);
      label.setTextColor(color);
      return view;
    }
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

          int color = ContextCompat.getColor(getContext(), SolveTypeIcons.colorForSolveType(solveType));
          ImageView icon = (ImageView) view.findViewById(R.id.imgSolveType);
          icon.setImageResource(SolveTypeIcons.forSolveType(solveType));
          icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
          view.findViewById(R.id.solveTypeTile)
              .setBackgroundTintList(ColorStateList.valueOf(withAlpha(color, TILE_ALPHA)));

          // The chip that names the kind wears the kind's colour; the rest stay neutral, since what
          // they say is not what the mark beside them is about.
          boolean anyChip = setChip(view, R.id.tvChipBlind, solveType.isBlind() ? getString(R.string.blind) : null);
          anyChip |= setChip(view, R.id.tvChipSteps,
              solveType.hasSteps() ? getString(R.string.solvetypes_steps_count, solveType.getSteps().length) : null);
          tintChip(view, R.id.tvChipBlind, color);
          tintChip(view, R.id.tvChipSteps, color);
          anyChip |= setChip(view, R.id.tvChipScramble, getScrambleTypeChip(solveType, solveTypeName));
          // Blind types never inspect, and the blind chip already says so.
          anyChip |= setChip(view, R.id.tvChipInspection,
              (!solveType.isBlind() && !solveType.hasInspection()) ? getString(R.string.solvetypes_no_inspection) : null);
          view.findViewById(R.id.chipRow).setVisibility(anyChip ? View.VISIBLE : View.GONE);
        }
      }
      return view;
    }

    // The add dialog names a new type after its scramble type, so skip the chip when it would just
    // repeat the name.
    private String getScrambleTypeChip(SolveType solveType, String solveTypeName) {
      ScrambleType scrambleType = solveType.getScrambleType();
      if (scrambleType == null || scrambleType.isDefault()) {
        return null;
      }
      String name = Utils.toScrambleTypeLocalizedName(getContext(), scrambleType);
      return name.equals(solveTypeName) ? null : name;
    }

    private boolean setChip(View row, int chipId, String text) {
      TextView chip = (TextView) row.findViewById(chipId);
      chip.setText(text);
      chip.setVisibility(text != null ? View.VISIBLE : View.GONE);
      return text != null;
    }

    private void tintChip(View row, int chipId, int color) {
      TextView chip = (TextView) row.findViewById(chipId);
      chip.setBackgroundTintList(ColorStateList.valueOf(withAlpha(color, CHIP_ALPHA)));
      chip.setTextColor(color);
    }

    private int withAlpha(int color, int alpha) {
      return (color & 0x00FFFFFF) | (alpha << 24);
    }
  }

}
