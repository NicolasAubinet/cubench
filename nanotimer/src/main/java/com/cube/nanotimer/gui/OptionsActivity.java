package com.cube.nanotimer.gui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.view.Menu;
import android.view.MenuItem;
import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SmartCubeGate;
import com.cube.nanotimer.gui.widget.CoachPlansForDialog;
import com.cube.nanotimer.gui.widget.CoachPlansGuard;
import com.cube.nanotimer.gui.widget.ReleaseNotes;
import com.cube.nanotimer.gui.widget.dialog.CoachCodeDialog;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.YesNoListener;
import com.cube.nanotimer.util.coach.CoachAccess;
import com.cube.nanotimer.util.coach.CoachBilling;
import com.cube.nanotimer.util.coach.CoachClient;
import com.cube.nanotimer.util.coach.CoachPlans;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveType;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class OptionsActivity extends NanoTimerActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.empty_screen);
    setTitle(R.string.settings);

    getSupportFragmentManager().beginTransaction().replace(R.id.containerLayout, new OptionsFragment()).commit();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.options_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.itReleaseNotes:
        ReleaseNotes.showReleaseNotesDialog(this);
        break;
    }
    return super.onOptionsItemSelected(item);
  }

  public static class OptionsFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preferences);

      Preference smartCube = findPreference("smart_cube_category");
      if (smartCube != null && !SmartCubeGate.ENABLED) {
        getPreferenceScreen().removePreference(smartCube);
      }

      showCoach();
      guardPreferredMethod();

      // Null when the reflection-based PreferenceFragment could not build its manager, which it
      // swallows rather than crashing on.
      Preference editSolveTypes = findPreference("edit_solve_types");
      if (editSolveTypes != null) {
        editSolveTypes.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
          @Override
          public boolean onPreferenceClick(Preference preference) {
            startActivity(new Intent(getActivity(), SolveTypesActivity.class));
            return true;
          }
        });
      }
    }

    /**
     * The coach's section: this install's id, and for a solver who has weekly plans, which solve
     * type they are for and the reminder.
     */
    private void showCoach() {
      PreferenceCategory coach = (PreferenceCategory) findPreference("coach_category");
      Preference coachId = findPreference("coach_id");
      final Preference plansFor = findPreference("coach_plans_for");
      Preference nudge = findPreference("coach_nudge_on");
      if (coach == null || coachId == null || plansFor == null || nudge == null) {
        return;
      }
      if (!SmartCubeGate.ENABLED || CoachClient.configured(getActivity()) == null) {
        getPreferenceScreen().removePreference(coach);
        return;
      }
      final String id = Options.INSTANCE.getCoachId();
      coachId.setSummary(id);
      coachId.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
          DialogUtils.copyToClipboard(getActivity(), "coach id", id, R.string.coach_id_copied);
          return true;
        }
      });
      showCoachCode(findPreference("coach_code"));
      showCoachManage(coach, findPreference("coach_manage"));
      Preference privacy = findPreference("coach_privacy");
      if (privacy != null) {
        privacy.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
          @Override
          public boolean onPreferenceClick(Preference preference) {
            Utils.openPrivacyPolicy(getActivity());
            return true;
          }
        });
      }
      if (!Options.INSTANCE.isCoachPlansOn()) {
        coach.removePreference(plansFor);
        coach.removePreference(nudge);
        return;
      }
      showPlansFor(plansFor);
      plansFor.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
          CoachPlansForDialog.show(getActivity(), new Runnable() {
            @Override
            public void run() {
              showPlansFor(plansFor);
            }
          });
          return true;
        }
      });
    }

    /** The way to the subscription in Google Play, for an install Play has shown a purchase. */
    private void showCoachManage(PreferenceCategory coach, Preference manage) {
      if (manage == null) {
        return;
      }
      if (CoachAccess.of(getActivity()).getPurchaseToken() == null) {
        coach.removePreference(manage);
        return;
      }
      manage.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
          try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://play.google.com/store/account/subscriptions?sku=" + CoachBilling.PRODUCT
                    + "&package=" + getActivity().getPackageName())));
          } catch (ActivityNotFoundException e) {
            // Nothing on the device opens a link, and then nothing sold the subscription either.
          }
          return true;
        }
      });
    }

    /** The access code row: where one is entered, and until when the last one taken lasts. */
    private void showCoachCode(final Preference code) {
      if (code == null) {
        return;
      }
      long until = CoachAccess.of(getActivity()).getCodeUntil();
      code.setSummary(until == 0 ? null
          : until == CoachAccess.NO_END ? getString(R.string.coach_code_on)
          : getString(R.string.coach_code_until,
              DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(until))));
      code.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
          DialogUtils.showFragment(getActivity(), CoachCodeDialog.newInstance(new Runnable() {
            @Override
            public void run() {
              showCoachCode(code);
            }
          }));
          return true;
        }
      });
    }

    /**
     * A preferred method other than CFOP stops every solve type that follows it from carrying the
     * weekly plans. When that is the type they are written for, the change asks first.
     */
    private void guardPreferredMethod() {
      Preference method = findPreference(Options.SMART_CUBE_METHOD_KEY);
      if (method == null) {
        return;
      }
      method.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
          final ListPreference list = (ListPreference) preference;
          final CubeMethod picked = CubeMethod.fromCode((String) value);
          if (picked == null || !Options.INSTANCE.isCoachPlansOn()) {
            return true;
          }
          // Whether the plans are touched takes a read, so the value is set once that is known.
          App.INSTANCE.getService().getSolveTypes(CubeType.THREE_BY_THREE,
              new DataCallback<List<SolveType>>() {
                @Override
                public void onData(final List<SolveType> solveTypes) {
                  final Activity activity = getActivity();
                  if (activity == null) {
                    return;
                  }
                  activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                      setPreferredMethod(list, picked, solveTypes);
                    }
                  });
                }
              });
          return false;
        }
      });
    }

    private void setPreferredMethod(final ListPreference list, final CubeMethod picked,
        List<SolveType> solveTypes) {
      final SolveType flagged = CoachPlans.flagged(solveTypes);
      if (flagged == null || CoachPlans.eligibleUnder(flagged, picked)) {
        list.setValue(picked.getCode());
        return;
      }
      boolean canMove = false;
      for (SolveType solveType : solveTypes) {
        canMove |= CoachPlans.eligibleUnder(solveType, picked);
      }
      final boolean another = canMove;
      final Activity activity = getActivity();
      String name = Utils.toSolveTypeLocalizedName(activity, flagged.getName());
      DialogUtils.showConfirmCancelDialog(activity, R.string.coach_guard_method_title,
          getString(another ? R.string.coach_guard_method_move : R.string.coach_guard_method_pause,
              name),
          CoachPlansGuard.changeConfirm(another), R.string.cancel, new YesNoListener() {
            @Override
            public void onYes() {
              list.setValue(picked.getCode());
              final Preference plansFor = findPreference("coach_plans_for");
              showPlansFor(plansFor);
              CoachPlansGuard.movePlans(activity, another, new Runnable() {
                @Override
                public void run() {
                  showPlansFor(plansFor);
                }
              });
            }
          });
    }

    /** @param plansFor null in a build with no coach, where the row is not shown */
    private void showPlansFor(final Preference plansFor) {
      if (plansFor == null) {
        return;
      }
      App.INSTANCE.getService().getSolveTypes(CubeType.THREE_BY_THREE,
          new DataCallback<List<SolveType>>() {
            @Override
            public void onData(List<SolveType> solveTypes) {
              final SolveType flagged = CoachPlans.flagged(solveTypes);
              final Activity activity = getActivity();
              if (activity == null) {
                return;
              }
              activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  plansFor.setSummary(flagged == null
                      ? activity.getString(R.string.coach_plans_setting_paused)
                      : activity.getString(R.string.coach_plans_setting_summary,
                          Utils.toSolveTypeLocalizedName(activity, flagged.getName())));
                }
              });
            }
          });
    }
  }

}
