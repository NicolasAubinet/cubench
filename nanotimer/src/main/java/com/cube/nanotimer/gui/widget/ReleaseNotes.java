package com.cube.nanotimer.gui.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.util.helper.Utils;

public class ReleaseNotes {

  private static final String VERSION_KEY = "app_version";

  public static void appLaunched(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    String prefVersion = prefs.getString(VERSION_KEY, null);
    String curVersion = Utils.getAppVersion(context);

    SharedPreferences.Editor editor = prefs.edit();
    if (prefVersion == null) {
      // first launch
      editor.putString(VERSION_KEY, curVersion);
      editor.apply();
    } else {
      if (!prefVersion.equals(curVersion)) {
        // app upgrade
        editor.putString(VERSION_KEY, curVersion);
        showReleaseNotesDialog(context);
        editor.apply();
      }
    }
  }

  public static void showReleaseNotesDialog(Context context) {
    LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    ViewGroup v = (ViewGroup) inflater.inflate(R.layout.release_notes_dialog, null);

    Utils.updateContextWithPrefsLocale(context); // needed to display release notes in the correct language (if default language was changed)

    final AlertDialog dialog = new AlertDialog.Builder(context, R.style.NanoTimerDialogTheme).setView(v).create();
    dialog.setTitle(context.getString(R.string.release_notes));
    dialog.setCanceledOnTouchOutside(true);

    WebView wvInfo = (WebView) v.findViewById(R.id.wvInfo);
    GUIUtils.setWebViewText(wvInfo, context.getString(R.string.release_notes_features_html));

    Button buClose = (Button) v.findViewById(R.id.buClose);
    buClose.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        dialog.dismiss();
      }
    });

    dialog.show();
  }

}
