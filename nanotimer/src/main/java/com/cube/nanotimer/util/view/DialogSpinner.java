package com.cube.nanotimer.util.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.ListPopupWindow;

import java.lang.reflect.Field;

// A Spinner that keeps the native dropdown, but forces it to open scrolled to the top instead of
// jumping to the currently-selected item (which is confusing when there are many entries).
public class DialogSpinner extends AppCompatSpinner {

  public DialogSpinner(Context context) {
    super(context);
  }

  public DialogSpinner(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public DialogSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public boolean performClick() {
    boolean handled = super.performClick(); // opens the native dropdown
    scrollDropdownToTop();
    return handled;
  }

  private void scrollDropdownToTop() {
    try {
      Field popupField = AppCompatSpinner.class.getDeclaredField("mPopup");
      popupField.setAccessible(true);
      Object popup = popupField.get(this);
      if (popup instanceof ListPopupWindow) {
        final ListView listView = ((ListPopupWindow) popup).getListView();
        if (listView != null) {
          // Set it now, and again after the popup finishes its own selection pass (some androidx
          // versions scroll to the selected item via a posted runnable) so the top wins either way.
          listView.setSelection(0);
          listView.post(new Runnable() {
            @Override
            public void run() {
              listView.setSelection(0);
            }
          });
        }
      }
    } catch (Throwable t) {
      // Reflection unavailable on this platform/version - keep the stock dropdown behavior.
    }
  }

}
