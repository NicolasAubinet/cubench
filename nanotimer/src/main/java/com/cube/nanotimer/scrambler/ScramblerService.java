package com.cube.nanotimer.scrambler;

import android.content.Context;
import android.util.Log;
import com.cube.nanotimer.scrambler.randomstate.AlreadyGeneratingException;
import com.cube.nanotimer.scrambler.randomstate.RSScrambler;
import com.cube.nanotimer.scrambler.randomstate.RSThreeScrambler;
import com.cube.nanotimer.scrambler.randomstate.RSTwoScrambler;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenEvent;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenEvent.GenerationLaunch;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenEvent.State;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenListener;
import com.cube.nanotimer.scrambler.randomstate.ScrambleConfig;
import com.cube.nanotimer.scrambler.randomstate.fto.RSFTOScrambler;
import com.cube.nanotimer.scrambler.randomstate.pyraminx.RSPyraminxScrambler;
import com.cube.nanotimer.scrambler.randomstate.skewb.RSSkewbScrambler;
import com.cube.nanotimer.scrambler.randomstate.square1.RSSquare1Scrambler;
import com.cube.nanotimer.util.ScrambleFormatterService;
import com.cube.nanotimer.util.helper.CpuUtils;
import com.cube.nanotimer.util.helper.FileUtils;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ScrambleType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public enum ScramblerService {
  INSTANCE;

  public static final int MAX_SCRAMBLES_IN_MEMORY = 500;

  /** Refill once a cache falls below this, and stop once it holds this many. */
  private static final int MIN_CACHE_SIZE = 25;
  private static final int MAX_CACHE_SIZE = 50;

  private Context context;
  private SeedScrambles seedScrambles;
  private final Map<ScrambleCacheKey, LinkedList<String[]>> cachedScrambles = new HashMap<ScrambleCacheKey, LinkedList<String[]>>();
  private final List<RSScrambler> scramblers = new ArrayList<>();
  private volatile Thread generationThread = null;

  private final List<RandomStateGenListener> listeners = new ArrayList<RandomStateGenListener>();
  private RandomStateGenEvent curState = new RandomStateGenEvent(State.IDLE, null, 0, 0);

  final private Object genThreadHelper = new Object();
  final private Object cacheMemHelper = new Object();
  final private Object cacheFileHelper = new Object();

  public void init(Context context) {
    this.context = context;
    this.seedScrambles = new SeedScrambles(context);
  }

  public void checkScrambleCaches() {
    checkCache(getRandomStateCubeTypes().get(0));
  }

  private void checkCache(CubeType cubeType) {
    try {
      generateAndAddToCache(cubeType, -1, GenerationLaunch.AUTO);
    } catch (AlreadyGeneratingException e) {
      // Ignore. Scrambles are already being generated.
    }
  }

  private int loadCacheAndGetToGenCount(CubeType cubeType, ScrambleType scrambleType) {
    Queue<String[]> scramblesCache = getCache(cubeType, scrambleType);
    if (scramblesCache.size() < MIN_CACHE_SIZE) {
      loadCacheFromFile(cubeType, scrambleType); // see if there are some more scrambles in the file
      if (scramblesCache.size() < MIN_CACHE_SIZE) {
        return Math.max(MAX_CACHE_SIZE - scramblesCache.size(), 0);
      }
    }
    return 0;
  }

  public void generateAndAddToCache(final CubeType cubeType, int scramblesCount, GenerationLaunch generationLaunch) throws AlreadyGeneratingException {
    synchronized (genThreadHelper) {
      if (generationThread != null) {
        throw new AlreadyGeneratingException();
      }
      generationThread = getNewGenerationThread(cubeType, scramblesCount, generationLaunch);
      generationThread.start();
    }
  }

  private Thread getNewGenerationThread(final CubeType cubeType, final int scramblesCount, final GenerationLaunch generationLaunch) {
    return new Thread() {
      @Override
      public void run() {
        generateScrambles(cubeType, null, scramblesCount);

        for (CubeType rsCubeType : getRandomStateCubeTypes()) {
          generateScrambles(rsCubeType, null, -1);

          for (ScrambleType scrambleType : rsCubeType.getUsedScrambledTypes()) {
            if (!scrambleType.isDefault()) {
              generateScrambles(rsCubeType, scrambleType, -1);
            }
          }
        }
        sendGenStateToListeners(new RandomStateGenEvent(RandomStateGenEvent.State.IDLE, null, 0, 0));

        synchronized (genThreadHelper) {
          if (generationThread == Thread.currentThread()) {
            //checkPluggedIn();
            generationThread = null;
          }
        }
      }

      private void generateScrambles(final CubeType cubeType, final ScrambleType scrambleType, int scramblesCount) {
        RSScrambler rsScrambler = getNewRandomStateScrambler(cubeType);
        if (generationThread != Thread.currentThread() || rsScrambler == null) {
          return;
        }
        int n;
        if (scramblesCount > 0) {
          n = scramblesCount;
        } else {
          n = loadCacheAndGetToGenCount(cubeType, scrambleType);
        }
        if (n == 0) {
          return;
        }
        int maxScrambleLength = getRSScrambleLength(cubeType);

        sendGenStateToListeners(new RandomStateGenEvent(RandomStateGenEvent.State.PREPARING, cubeType, scrambleType, generationLaunch, 0, n));
        rsScrambler.prepareGenTables(context);
        rsScrambler.genTables();

        final int threadsCount = Math.max(1, CpuUtils.getNumberOfCores() - 1);
        Log.i("NanoTimer", "Generate " + n + " " + cubeType + "|" + scrambleType + " scrambles on " + threadsCount + " threads");

        final List<String[]> toSave = new ArrayList<>();
        final Semaphore threadsSemaphore = new Semaphore(threadsCount);
        final List<ScrambleGeneratedThread> scrambleGenerationThreads = new ArrayList<>();
        final AtomicInteger generatedScramblesCount = new AtomicInteger(1);
        final ScrambleConfig scrambleConfig = new ScrambleConfig(maxScrambleLength, scrambleType);

        sendGenStateToListeners(new RandomStateGenEvent(RandomStateGenEvent.State.GENERATING, cubeType, scrambleType,
          generationLaunch, generatedScramblesCount.get(), n));

        final ScrambleGeneratedHandler scrambleGeneratedHandler = new ScrambleGeneratedHandler() {
          @Override
          public void scrambleGenerated(String[] scramble, int toGenerateCount) {
            threadsSemaphore.release();

            if (scramble == null) { // was interrupted
              return;
            }

            synchronized (cacheMemHelper) {
              Queue<String[]> scramblesCache = getCache(cubeType, scrambleType);
              if (scramblesCache.size() < MAX_SCRAMBLES_IN_MEMORY) {
                scramblesCache.add(scramble);
              }
            }

            synchronized (toSave) {
              toSave.add(scramble);
              if (toSave.size() >= 10) {
                saveNewScramblesToFile(cubeType, scrambleType, toSave); // write new scrambles to file by batches
                toSave.clear();
              }
            }

            sendGenStateToListeners(new RandomStateGenEvent(RandomStateGenEvent.State.GENERATED, cubeType, scrambleType,
              generationLaunch, generatedScramblesCount.get(), toGenerateCount));

            sendGenStateToListeners(new RandomStateGenEvent(RandomStateGenEvent.State.GENERATING, cubeType, scrambleType,
              generationLaunch, generatedScramblesCount.incrementAndGet(), toGenerateCount));
          }
        };

        try {
          for (int i = 0; i < n && generationThread == Thread.currentThread(); i++) {
            threadsSemaphore.acquire();

            rsScrambler = getNewRandomStateScrambler(cubeType);
            synchronized (scramblers) {
              scramblers.add(rsScrambler);
            }

            ScrambleGeneratedThread scrambleGeneratedThread = new ScrambleGeneratedThread(rsScrambler, scrambleConfig, scrambleGeneratedHandler, n);
            scrambleGenerationThreads.add(scrambleGeneratedThread);
            scrambleGeneratedThread.start();
          }
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        try {
          for (ScrambleGeneratedThread scrambleGenerationThread : scrambleGenerationThreads) {
            scrambleGenerationThread.join();
          }
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        Log.i("NanoTimer", "Generated " + (generatedScramblesCount.get()-1) + " scrambles for cube type " + cubeType + "!");

        if (!toSave.isEmpty()) {
          saveNewScramblesToFile(cubeType, scrambleType, toSave);
        }
      }

//      private void checkPluggedIn() {
//        // call service to check if generation should be started or stopped
//        context.sendBroadcast(new Intent(ChargingStateReceiver.CHECK_ACTION_NAME));
//      }
    };
  }

  private void sendGenStateToListeners(RandomStateGenEvent state) {
    curState = state;
    synchronized (listeners) {
      for (int i = 0; i < listeners.size(); i++) {
        listeners.get(i).onStateUpdate(state);
      }
    }
  }

  /** The longest a generated scramble may be, or 0 where the solver decides for itself. */
  public static int getRSScrambleLength(CubeType cubeType) {
    switch (cubeType) {
      case TWO_BY_TWO:
        return 11;
      case THREE_BY_THREE:
        return 21;
      case PYRAMINX:
        return 11;
      default:
        return 0;
    }
  }

  private RSScrambler getNewRandomStateScrambler(CubeType cubeType) {
    switch (cubeType) {
      case THREE_BY_THREE:
        return new RSThreeScrambler();
      case TWO_BY_TWO:
        return new RSTwoScrambler();
      case SKEWB:
        return new RSSkewbScrambler();
      case PYRAMINX:
        return new RSPyraminxScrambler();
      case SQUARE1:
        return new RSSquare1Scrambler();
      case FTO:
        return new RSFTOScrambler();
      default:
        return null;
    }
  }

  private void loadCacheFromFile(CubeType cubeType, ScrambleType scrambleType) {
    List<String> scramblesStr;
    synchronized (cacheFileHelper) {
      scramblesStr = FileUtils.readLinesFromFile(context, getFileName(cubeType, scrambleType), MAX_SCRAMBLES_IN_MEMORY);
    }

    synchronized (cacheMemHelper) {
      Queue<String[]> scramblesCache = getCache(cubeType, scrambleType);
      scramblesCache.clear();
      for (String l : scramblesStr) {
        String[] scramble = ScrambleFormatterService.INSTANCE.parseStringScrambleToArray(l, cubeType);
        scramblesCache.add(scramble);
      }
    }
  }

  private synchronized void saveNewScramblesToFile(CubeType cubeType, ScrambleType scrambleType, List<String[]> scramblesToSave) {
    List<String> scramblesStr = new ArrayList<String>(scramblesToSave.size());
    for (String[] scramble : scramblesToSave) {
      StringBuilder sb = new StringBuilder();
      for (String move : scramble) {
        sb.append(move).append(" ");
      }
      scramblesStr.add(sb.toString());
    }
    synchronized (cacheFileHelper) {
      FileUtils.appendLinesToFile(context, getFileName(cubeType, scrambleType), scramblesStr.toArray(new String[scramblesStr.size()]));
    }
  }

  private void removeFirstScrambleFromFile(CubeType cubeType, ScrambleType scrambleType) {
    synchronized (cacheFileHelper) {
      FileUtils.removeFirstLineFromFile(context, getFileName(cubeType, scrambleType));
    }
  }

  /**
   * The next scramble for this puzzle, or null when a random-state one is not ready yet.
   *
   * <p>A puzzle that has a random-state solver is never handed a random-move scramble instead: the
   * two are not the same quality, and a session whose first scramble was the lesser one is a
   * session with an odd solve in it. Where the solver has not run yet, one of the scrambles shipped
   * with the app stands in ({@link SeedScrambles}); only once those are gone too does this return
   * null, and a caller with nothing to show waits for a
   * {@link RandomStateGenEvent.State#GENERATED} event and asks again.
   *
   * <p>Reads the scramble file, so call it off the UI thread. A caller that must answer at once
   * asks {@link #getScramble(CubeType, ScrambleType, boolean)} for the memory cache alone first.
   */
  public String[] getScramble(final CubeType cubeType, final ScrambleType scrambleType) {
    return getScramble(cubeType, scrambleType, true);
  }

  /**
   * @param fromFile whether an empty memory cache may be refilled from the scramble file. Memory is
   *     filled per cube type as generation reaches it, so a puzzle whose turn has not come yet can
   *     have a full file sitting behind an empty cache. Reading it is disk access, though, and off
   *     limits on the UI thread.
   */
  public String[] getScramble(final CubeType cubeType, final ScrambleType scrambleType, boolean fromFile) {
    if (!getRandomStateCubeTypes().contains(cubeType)) {
      return ScramblerFactory.getScrambler(cubeType).getNewScramble();
    }
    String[] scramble = takeCachedScramble(cubeType, scrambleType);
    if (scramble == null && fromFile) {
      loadCacheFromFile(cubeType, scrambleType);
      scramble = takeCachedScramble(cubeType, scrambleType);
    }
    final boolean fromCache = (scramble != null);
    if (scramble == null && isDefault(scrambleType)) {
      // Only the ordinary scramble has a shipped pool: one per special type is not worth an APK.
      scramble = seedScrambles.take(cubeType, fromFile);
    }

    new Thread(new Runnable() {
      @Override
      public void run() {
        if (fromCache) { // a shipped scramble, or none at all, costs the cache file nothing
          removeFirstScrambleFromFile(cubeType, scrambleType);
        }
        checkCache(cubeType);
      }
    }).start();
    return scramble;
  }

  /** A null scramble type is the ordinary full scramble, and so is the one named "default". */
  private static boolean isDefault(ScrambleType scrambleType) {
    return scrambleType == null || scrambleType.isDefault();
  }

  private String[] takeCachedScramble(CubeType cubeType, ScrambleType scrambleType) {
    synchronized (cacheMemHelper) {
      Queue<String[]> scramblesCache = getCache(cubeType, scrambleType);
      return scramblesCache.isEmpty() ? null : scramblesCache.remove();
    }
  }

  public int getScramblesCount(CubeType cubeType, ScrambleType scrambleType) {
    int scramblesCount;
    synchronized (cacheFileHelper) {
      scramblesCount = FileUtils.getFileLinesCount(context, getFileName(cubeType, scrambleType));
    }
    return scramblesCount;
  }

  public void stopGeneration() {
    synchronized (genThreadHelper) {
      if (generationThread != null) {
        generationThread = null;
        sendGenStateToListeners(new RandomStateGenEvent(State.STOPPING, null, 0, 0));
      }
    }
    synchronized (scramblers) {
      for (RSScrambler scrambler : scramblers) {
        scrambler.stop();
      }
      scramblers.clear();
    }
  }

  public void addRandomStateGenListener(RandomStateGenListener listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
    listener.onStateUpdate(curState);
  }

  public void removeRandomStateGenListener(RandomStateGenListener listener) {
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  public List<CubeType> getRandomStateCubeTypes() {
    return Arrays.asList(CubeType.THREE_BY_THREE, CubeType.TWO_BY_TWO, /*CubeType.SKEWB,*/ CubeType.PYRAMINX, CubeType.SQUARE1, CubeType.FTO);
  }

  private String getFileName(CubeType cubeType, ScrambleType scrambleType) {
    String fileName = "randomstate_scrambles_" + cubeType.getId();
    if (!isDefault(scrambleType)) {
      fileName += "_" + scrambleType.getName();
    }
    return fileName;
  }

  private Queue<String[]> getCache(CubeType cubeType, ScrambleType scrambleType) {
    ScrambleType cacheScrambleType = isDefault(scrambleType) ? null : scrambleType;
    ScrambleCacheKey scrambleCacheKey = new ScrambleCacheKey(cubeType.getId(), cacheScrambleType);

    LinkedList<String[]> scrambles;
    synchronized (cachedScrambles) {
      scrambles = cachedScrambles.get(scrambleCacheKey);
      if (scrambles == null) {
        scrambles = new LinkedList<>();
        cachedScrambles.put(scrambleCacheKey, scrambles);
      }
    }
    return scrambles;
  }

  private class ScrambleCacheKey {
    private int cubeTypeId;
    private ScrambleType scrambleType;

    public ScrambleCacheKey(int cubeTypeId, ScrambleType scrambleType) {
      this.cubeTypeId = cubeTypeId;
      this.scrambleType = scrambleType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ScrambleCacheKey)) return false;

      ScrambleCacheKey that = (ScrambleCacheKey) o;

      if (cubeTypeId != that.cubeTypeId) return false;
      return !(scrambleType != null ? !scrambleType.equals(that.scrambleType) : that.scrambleType != null);
    }

    @Override
    public int hashCode() {
      int result = cubeTypeId;
      result = 31 * result + (scrambleType != null ? scrambleType.hashCode() : 0);
      return result;
    }
  }

  private class ScrambleGeneratedThread extends Thread {
    private RSScrambler rsScrambler;
    private ScrambleConfig scrambleConfig;
    private ScrambleGeneratedHandler scrambleGeneratedHandler;
    private int toGenerateCount;

    public ScrambleGeneratedThread(RSScrambler rsScrambler, ScrambleConfig scrambleConfig,
                                   ScrambleGeneratedHandler scrambleGeneratedHandler, int toGenerateCount) {
      this.rsScrambler = rsScrambler;
      this.scrambleConfig = scrambleConfig;
      this.scrambleGeneratedHandler = scrambleGeneratedHandler;
      this.toGenerateCount = toGenerateCount;
    }

    @Override
    public void run() {
      String[] scramble = rsScrambler.getNewScramble(scrambleConfig);
      synchronized (scramblers) {
        scramblers.remove(rsScrambler);
      }
      scrambleGeneratedHandler.scrambleGenerated(scramble, toGenerateCount);
    }
  }

  private abstract class ScrambleGeneratedHandler {
    public abstract void scrambleGenerated(String[] scramble, int toGenerateCount);
  }

}
