package com.cube.nanotimer.gui;

import android.content.res.Configuration;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import com.cube.nanotimer.App;
import com.cube.nanotimer.util.helper.Utils;

/**
 * Base of every activity in the app.
 *
 * It claims {@link App} for itself on the way in, which is what makes the app survive a process
 * death. Android recreates whichever activity was on top, not the main screen, so an activity that
 * relied on an earlier one having started the app found a null service and died during onCreate.
 * The call is cheap when the app is already up: App only builds itself once.
 */
public class NanoTimerActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Utils.updateContextWithPrefsLocale(this);
    App.INSTANCE.setContext(this);

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    Utils.updateContextWithPrefsLocale(this);
    App.INSTANCE.setContext(this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Utils.updateContextWithPrefsLocale(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        break;
    }
    return super.onOptionsItemSelected(item);
  }

}
