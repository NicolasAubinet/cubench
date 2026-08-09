package com.cube.nanotimer.util.helper;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.res.ResourcesCompat;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.FormatterService;

public class GUIUtils {

  public static void expandView(final LinearLayout v) {
    v.measure(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    final int targetHeight = v.getMeasuredHeight();

    v.getLayoutParams().height = 0;
    v.setVisibility(View.VISIBLE);
    Animation a = new Animation() {
      @Override
      protected void applyTransformation(float interpolatedTime, Transformation t) {
        v.getLayoutParams().height = interpolatedTime == 1
            ? LayoutParams.WRAP_CONTENT
            : (int) (targetHeight * interpolatedTime);
        v.requestLayout();
      }

      @Override
      public boolean willChangeBounds() {
        return true;
      }
    };

    // 1dp/ms
    a.setDuration((int) (targetHeight / v.getContext().getResources().getDisplayMetrics().density));
    v.startAnimation(a);
  }

  public static void collapseView(final View v) {
    final int initialHeight = v.getMeasuredHeight();

    Animation a = new Animation() {
      @Override
      protected void applyTransformation(float interpolatedTime, Transformation t) {
        if (interpolatedTime == 1) {
          v.setVisibility(View.GONE);
        } else {
          v.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
          v.requestLayout();
        }
      }

      @Override
      public boolean willChangeBounds() {
        return true;
      }
    };

    // 1dp/ms
    a.setDuration((int) (initialHeight / v.getContext().getResources().getDisplayMetrics().density));
    v.startAnimation(a);
  }

  /*private void moveViewToScreenCenter(Activity a, RelativeLayout rootLayout, View view) {
    DisplayMetrics dm = new DisplayMetrics();
    a.getWindowManager().getDefaultDisplay().getMetrics(dm);
    int statusBarOffset = dm.heightPixels - rootLayout.getMeasuredHeight();

    int originalPos[] = new int[2];
    view.getLocationOnScreen(originalPos);

    int xDest = dm.widthPixels / 2;
    xDest -= (view.getMeasuredWidth() / 2);
    int yDest = dm.heightPixels / 2 - (view.getMeasuredHeight() / 2) - statusBarOffset;

    TranslateAnimation anim = new TranslateAnimation(0, xDest - originalPos[0], 0, yDest - originalPos[1]);
    anim.setDuration(1000);
    anim.setFillAfter(true);
    view.startAnimation(anim);
  }*/

  public static int getColorCodeBetween(int color1, int color2, float stepProgression) {
    int a1 = (color1 >> 24) & 0xFF;
    int r1 = (color1 >> 16) & 0xFF;
    int g1 = (color1 >> 8) & 0xFF;
    int b1 = (color1) & 0xFF;
    int a2 = (color2 >> 24) & 0xFF;
    int r2 = (color2 >> 16) & 0xFF;
    int g2 = (color2 >> 8) & 0xFF;
    int b2 = (color2) & 0xFF;
    int a = (int) ((a2 - a1) * stepProgression) + a1;
    int r = (int) ((r2 - r1) * stepProgression) + r1;
    int g = (int) ((g2 - g1) * stepProgression) + g1;
    int b = (int) ((b2 - b1) * stepProgression) + b1;

    int res = a << 24;
    res |= r << 16;
    res |= g << 8;
    res |= b;
    return res;
  }

  public static void setSessionTimeCellText(TextView tv, long time, int timeInd, int bestInd, int worstInd) {
    String strTime = FormatterService.INSTANCE.formatSolveTime(time);
    if (time < 0) { // DNF: always grayed out, for consistency across the app
      setHtmlColoredTime(tv, strTime, tv.getContext().getResources().getColor(R.color.dnf_time));
    } else if (timeInd == bestInd) {
      setHtmlColoredTime(tv, strTime, tv.getContext().getResources().getColor(R.color.green));
    } else if (timeInd == worstInd) {
      setHtmlColoredTime(tv, strTime, tv.getContext().getResources().getColor(R.color.red));
    } else {
      tv.setText(strTime);
    }
  }

  /** Renders a session time tinted with the given color (gradient coloring). */
  public static void setSessionTimeCellColor(TextView tv, long time, int color) {
    setHtmlColoredTime(tv, FormatterService.INSTANCE.formatSolveTime(time), color);
  }

  private static void setHtmlColoredTime(TextView tv, String strTime, int color) {
    tv.setText(Html.fromHtml("<font color='" + color + "'>" + strTime + "</font>"));
  }

  public static void setWebViewText(WebView webView, String text) {
    WebSettings settings = webView.getSettings();
    settings.setDefaultTextEncodingName("utf-8");

    webView.setBackgroundColor(Color.TRANSPARENT);
    // Not loadData: it wraps the text in a data: URL, where a '#' truncates the rest.
    webView.loadDataWithBaseURL(null, text, "text/html", "utf-8", null);
  }

  public static AlertDialog showLoadingIndicator(Context context) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.NanoTimerDialogTheme);
    ProgressBar pbar = new ProgressBar(context);
    pbar.setIndeterminate(true);
    AlertDialog dialog = builder.setView(pbar).create();
    dialog.show();
    return dialog;
  }

  public static void showNotification(Context c, int id, String title, String message, Class resultClass) {
    int notificationIcon;
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      notificationIcon = R.drawable.generating_scrambles;
    } else {
      notificationIcon = R.drawable.icon;
    }

    String channelId = c.getPackageName() + ".notifications";
    NotificationManager notifManager = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      String channelName = "Android channel";

      int importance = NotificationManager.IMPORTANCE_DEFAULT;
      NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
      channel.setSound(null, null);
      notifManager.createNotificationChannel(channel);
    }

    NotificationCompat.Builder builder = new NotificationCompat.Builder(c, channelId)
        .setSmallIcon(notificationIcon)
        .setContentTitle(title)
        .setContentText(message);

    Intent resultIntent = new Intent(c, resultClass);
    TaskStackBuilder stackBuilder = TaskStackBuilder.create(c);
    stackBuilder.addParentStack(resultClass);
    stackBuilder.addNextIntent(resultIntent);

    PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    builder.setContentIntent(pendingIntent);

    notifManager.notify(id, builder.build());
  }

  public static void hideNotification(Context c, int id) {
    NotificationManager notifManager = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    notifManager.cancel(id);
  }

  /**
   * The face the whole app is set in, the one the themes name. The theme reaches every view
   * inflated from a layout, but not one built in code, and {@code setTypeface(null, style)} throws
   * it away for the platform default, which is a different font on every ROM: both come here.
   */
  public static Typeface appFont(Context c) {
    try {
      Typeface font = ResourcesCompat.getFont(c, R.font.roboto);
      if (font != null) {
        return font;
      }
    } catch (RuntimeException e) {
      Log.e("NanoTimer", "Unable to load the app font", e);
    }
    return Typeface.defaultFromStyle(Typeface.NORMAL);
  }

  /** Sets a text view's weight without losing the face it is set in. */
  public static void setWeight(TextView tv, int style) {
    tv.setTypeface(appFont(tv.getContext()), style);
  }

  /** A text view built in code rather than inflated, so it has to be told the app's face. */
  public static TextView newTextView(Context c) {
    TextView tv = new TextView(c);
    tv.setTypeface(appFont(c));
    return tv;
  }

}
