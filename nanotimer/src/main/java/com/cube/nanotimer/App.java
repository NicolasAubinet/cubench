package com.cube.nanotimer;

import android.content.Context;
import com.cube.nanotimer.Options.ScrambleNotificationMode;
import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.gui.MainScreenActivity;
import com.cube.nanotimer.gui.widget.ReleaseNotes;
import com.cube.nanotimer.scrambler.ScramblerService;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenEvent;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenEvent.GenerationLaunch;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenEvent.State;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenListener;
import com.cube.nanotimer.services.Service;
import com.cube.nanotimer.services.ServiceImpl;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ScrambleType;

import java.util.List;
import java.util.Map;

public enum App {
  INSTANCE;

  private Context context;
  private Service service;
  private boolean appGUILaunched;
  private DynamicTranslations dynamicTranslations;
  private SoundManager soundManager;

  private static final int SCRAMBLE_NOTIF_ID = 1;

  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    init(context, false);
  }

  public void setApplicationContext(Context context) { // called from broadcast receiver
    init(context, true);
  }

  private void init(Context context, boolean fromService) {
    boolean appStarted = (this.context == null);
    if (appStarted || !fromService) {
      this.context = context;
    }

    if (appStarted) { // app started (either from GUI or from service)
      service = ServiceImpl.getInstance(context);
      Options.INSTANCE.setContext(context);
      ScramblerService.INSTANCE.init(context);
      SmartCubeManager.INSTANCE.init(context);
      initRandomStateGenListener(context);

      getService().getAllUsedScrambleTypes(new DataCallback<Map<CubeType, List<ScrambleType>>>() {
        @Override
        public void onData(Map<CubeType, List<ScrambleType>> data) {
          for (Map.Entry<CubeType, List<ScrambleType>> locEntry : data.entrySet()) {
            CubeType cubeType = locEntry.getKey();
            for (ScrambleType scrambleType : locEntry.getValue()) {
              cubeType.addUsedScrambleType(scrambleType);
            }
          }
          ScramblerService.INSTANCE.checkScrambleCaches();
        }
      });

      if (dynamicTranslations == null) {
        dynamicTranslations = new DynamicTranslations();
        dynamicTranslations.init(context);
      }

      if (soundManager == null) {
        soundManager = new SoundManager();
        soundManager.init();
      }
    }

    if (!appGUILaunched && !fromService) { // app GUI started
      appGUILaunched(context);
    }
  }

  private void appGUILaunched(Context context) {
    appGUILaunched = true;
    AppLaunchStats.appLaunched(context);
    ReleaseNotes.appLaunched(context);
  }

  private void initRandomStateGenListener(final Context context) {
    RandomStateGenListener randomStateGenListener = new RandomStateGenListener() {
      @Override
      public void onStateUpdate(RandomStateGenEvent event) {
        String title = context.getString(R.string.scrambles_being_generated, event.getCubeTypeName());
        ScrambleNotificationMode scrambleNotificationMode = Options.INSTANCE.getGenScrambleNotificationMode();
        boolean showNotif = (scrambleNotificationMode == ScrambleNotificationMode.ALWAYS ||
            (scrambleNotificationMode == ScrambleNotificationMode.MANUAL &&
                (event.getGenerationLaunch() == GenerationLaunch.MANUAL || event.getGenerationLaunch() == GenerationLaunch.PLUGGED)));
        if (event.getState() == State.PREPARING && showNotif) {
          GUIUtils.showNotification(context, SCRAMBLE_NOTIF_ID, title, context.getString(R.string.preparing_generation), MainScreenActivity.class);
        } else if (event.getState() == State.GENERATING && showNotif) {
          String text = context.getString(R.string.generating_scramble, event.getCurScramble(), event.getTotalToGenerate());
          ScrambleType scrambleType = event.getScrambleType();
          if (scrambleType != null && !event.getScrambleType().isDefault()) {
            text += " (" + Utils.toScrambleTypeLocalizedName(context, scrambleType) + ")";
          }
          GUIUtils.showNotification(context, SCRAMBLE_NOTIF_ID, title, text, MainScreenActivity.class);
        } else if (event.getState() == State.IDLE || !showNotif) {
          GUIUtils.hideNotification(context, SCRAMBLE_NOTIF_ID);
        }
      }
    };
    ScramblerService.INSTANCE.addRandomStateGenListener(randomStateGenListener);
  }

  public Service getService() {
    return service;
  }

  public boolean isProEnabled() {
    return ProChecker.getProState(context) == ProChecker.ProState.ENABLED;
  }

  public DynamicTranslations getDynamicTranslations() {
    return dynamicTranslations;
  }

  public SoundManager getSoundManager() {
    return soundManager;
  }
}
