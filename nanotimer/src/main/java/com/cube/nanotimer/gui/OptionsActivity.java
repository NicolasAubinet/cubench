package com.cube.nanotimer.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.ReleaseNotes;
import com.cube.nanotimer.scrambler.ScramblerService;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenEvent;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenEvent.State;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenListener;
import com.cube.nanotimer.util.YesNoListener;
import com.cube.nanotimer.util.helper.DialogUtils;

public class OptionsActivity extends NanoTimerActivity {

  private static final int MIN_DELTA_BETWEEN_SCRAMBLES_CACHE_MIN_MAX = 10;

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

    private OnSharedPreferenceChangeListener prefChangedListener;
    private AlertDialog dialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preferences);

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

      prefChangedListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
          preferenceChanged(sharedPreferences, key);
        }
      };
      PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(prefChangedListener);
    }

    @Override
    public void onDestroy() {
      super.onDestroy();

      PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(prefChangedListener);
    }

    private void preferenceChanged(SharedPreferences pref, String key) {
      Context context = getContext();
      if (context == null) {
        context = App.INSTANCE.getContext();
      }
      assert context != null;
      if (context == null) {
        return;
      }
      if (key.equals(Options.RANDOMSTATE_SCRAMBLES_KEY)) {
        Boolean defaultValue = context.getResources().getBoolean(R.bool.randomstate_scrambles);
        boolean randomState = pref.getBoolean(Options.RANDOMSTATE_SCRAMBLES_KEY, defaultValue);
        ScramblerService.INSTANCE.activateRandomStateScrambles(randomState);
      } else if (key.equals(Options.SCRAMBLES_QUALITY_KEY)) {
        // Calls to runOnUiThread and isFinishing were added to fix a crash when opening the dialog
        DialogUtils.showYesNoConfirmation(context, R.string.want_to_clear_scramble_cache, new YesNoListener() {
          @Override
          public void onYes() {
            ScramblerService.INSTANCE.stopGeneration();
            ScramblerService.INSTANCE.addRandomStateGenListener(new RandomStateGenListener() {
              @Override
              public void onStateUpdate(RandomStateGenEvent event) {
                if (event.getState() == State.IDLE) {
                  ScramblerService.INSTANCE.removeRandomStateGenListener(this);
                  ScramblerService.INSTANCE.deleteCaches();
                  ScramblerService.INSTANCE.checkScrambleCaches();
                }
              }
            });
          }
        });
      } else if (key.equals(Options.SCRAMBLES_MIN_CACHE_SIZE_KEY)) {
        int max = Options.INSTANCE.getScramblesMaxCacheSize();
        int defaultMinValue = context.getResources().getInteger(R.integer.min_scramble_cache_size);
        int min = pref.getInt(key, defaultMinValue);
        if (min > (max - MIN_DELTA_BETWEEN_SCRAMBLES_CACHE_MIN_MAX)) {
          int newValue = max - MIN_DELTA_BETWEEN_SCRAMBLES_CACHE_MIN_MAX;
          final String infoMsg = (min > max) ? context.getString(R.string.min_scramble_cache_bigger_than_max, newValue) :
            context.getString(R.string.min_scramble_cache_too_close_to_max, newValue);
          DialogUtils.showInfoMessage(context, infoMsg);
          min = newValue;
          Editor editor = pref.edit();
          editor.putInt(key, min);
          editor.apply();
        }
        ScramblerService.INSTANCE.checkScrambleCaches();
      } else if (key.equals(Options.SCRAMBLES_MAX_CACHE_SIZE_KEY)) {
        int min = Options.INSTANCE.getScramblesMinCacheSize();
        int defaultMaxValue = context.getResources().getInteger(R.integer.max_scramble_cache_size);
        int max = pref.getInt(key, defaultMaxValue);
        if (max < (min + MIN_DELTA_BETWEEN_SCRAMBLES_CACHE_MIN_MAX)) {
          int newValue = min + MIN_DELTA_BETWEEN_SCRAMBLES_CACHE_MIN_MAX;
          final String infoMsg = (max < min) ? context.getString(R.string.max_scramble_cache_smaller_than_min, newValue) :
            context.getString(R.string.max_scramble_cache_too_close_to_min, newValue);
          DialogUtils.showInfoMessage(context, infoMsg);
          max = newValue;
          Editor editor = pref.edit();
          editor.putInt(key, max);
          editor.apply();
        }
//      } else if (key.equals(Options.SCRAMBLES_GEN_WHEN_PLUGGED_IN_KEY) ||
//        key.equals(Options.SCRAMBLES_GEN_COUNT_WHEN_PLUGGED_IN_KEY)) {
//        // call service to check if generation should be started or stopped
//        context.sendBroadcast(new Intent(ChargingStateReceiver.CHECK_ACTION_NAME));
      }
    }
  }

}
