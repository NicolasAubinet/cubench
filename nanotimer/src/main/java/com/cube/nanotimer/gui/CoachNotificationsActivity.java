package com.cube.nanotimer.gui;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.preference.Preference;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.coach.CoachRecapWorker;

import java.text.DateFormat;
import java.util.Calendar;

/** The coach's notifications: all of them, plan ready, the solve reminder and the daily recap. */
public class CoachNotificationsActivity extends NanoTimerActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.empty_screen);
    setTitle(R.string.coach_notifications);
    getSupportFragmentManager().beginTransaction()
        .replace(R.id.containerLayout, new NotificationsFragment()).commit();
  }

  public static class NotificationsFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.coach_notifications);
      Preference.OnPreferenceChangeListener reschedule = (preference, value) -> {
        // Read once the new value is stored, which is after this listener returns.
        getView().post(() -> CoachRecapWorker.schedule(getActivity()));
        return true;
      };
      for (String key : new String[] {Options.COACH_NOTIFICATIONS_ON_KEY,
          Options.COACH_RECAP_ON_KEY}) {
        Preference toggle = findPreference(key);
        if (toggle != null) {
          toggle.setOnPreferenceChangeListener(reschedule);
        }
      }
      final Preference time = findPreference(Options.COACH_RECAP_TIME_KEY);
      if (time == null) {
        return;
      }
      showTime(time);
      time.setOnPreferenceClickListener(preference -> {
        int minutes = Options.INSTANCE.getCoachRecapMinutes();
        new TimePickerDialog(getActivity(), R.style.NanoTimerDialogTheme, (picker, hour, minute) -> {
          Options.INSTANCE.setCoachRecapMinutes(hour * 60 + minute);
          showTime(time);
          CoachRecapWorker.schedule(getActivity());
        }, minutes / 60, minutes % 60,
            android.text.format.DateFormat.is24HourFormat(getActivity())).show();
        return true;
      });
    }

    private void showTime(Preference time) {
      int minutes = Options.INSTANCE.getCoachRecapMinutes();
      Calendar at = Calendar.getInstance();
      at.set(Calendar.HOUR_OF_DAY, minutes / 60);
      at.set(Calendar.MINUTE, minutes % 60);
      DateFormat format = android.text.format.DateFormat.getTimeFormat(getActivity());
      time.setSummary(getString(R.string.coach_recap_time_summary, format.format(at.getTime())));
    }
  }
}
