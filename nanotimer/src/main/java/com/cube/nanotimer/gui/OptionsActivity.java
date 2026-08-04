package com.cube.nanotimer.gui;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.view.Menu;
import android.view.MenuItem;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.ReleaseNotes;

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
  }

}
