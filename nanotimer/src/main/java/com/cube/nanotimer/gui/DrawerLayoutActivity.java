package com.cube.nanotimer.gui;

import android.content.res.Configuration;
import android.os.Bundle;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import com.cube.nanotimer.R;

public class DrawerLayoutActivity extends NanoTimerActivity {
  private Toolbar toolbar;
  private DrawerLayout drawerLayout;
  private ActionBarDrawerToggle drawerToggle;
  private NavigationView navigationView;

  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    // Sync the toggle state after onRestoreInstanceState has occurred.
    drawerToggle.syncState();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    drawerToggle.onConfigurationChanged(newConfig);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Pass the event to ActionBarDrawerToggle, if it returns
    // true, then it has handled the app icon touch event
    if (drawerToggle.onOptionsItemSelected(item)) {
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  protected void initViews() {
    toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayShowTitleEnabled(false); // the bar wears the logotype instead

    toolbar.setNavigationOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        drawerLayout.openDrawer(GravityCompat.START);
      }
    });

    drawerLayout = (DrawerLayout) findViewById(R.id.drawerLayout);
    drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.app_name, R.string.app_name) {
      @Override
      public void onDrawerStateChanged(int newState) {
        super.onDrawerStateChanged(newState);
        onDrawerStateChangedCustom(newState);
      }
    };
    drawerLayout.addDrawerListener(drawerToggle);

    // allows to always display a custom up indicator:
//    drawerToggle.setDrawerIndicatorEnabled(false);
//    getSupportActionBar().setHomeAsUpIndicator(R.drawable.menu_icon);

    showDrawerMenuIcon(true);

    navigationView = (NavigationView) findViewById(R.id.navigationView);
    navigationView.setNavigationItemSelectedListener(new OnNavigationItemSelectedListener() {
      @Override
      public boolean onNavigationItemSelected(MenuItem item) {
        drawerLayout.closeDrawer(navigationView);
        return onOptionsItemSelected(item);
      }
    });

    // Draws the indicator on the toolbar this toggle was just given, which matters when the
    // layout is re-inflated for a new orientation and there is no onPostCreate to do it.
    drawerToggle.syncState();
  }

  protected void showDrawerMenuIcon(boolean show) {
    ActionBar actionBar = getSupportActionBar();
    actionBar.setDisplayHomeAsUpEnabled(show);
    actionBar.setHomeButtonEnabled(show);
  }

  public void onDrawerStateChangedCustom(int newState) {
  }

  protected Menu getMenu() {
    return navigationView.getMenu();
  }

  protected MenuItem findMenuItem(int parResId) {
    return getMenu().findItem(parResId);
  }

  protected void closeDrawer() {
    drawerLayout.closeDrawers();
  }

}
