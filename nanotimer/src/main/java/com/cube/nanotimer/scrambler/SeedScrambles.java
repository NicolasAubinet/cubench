package com.cube.nanotimer.scrambler;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.ScrambleFormatterService;
import com.cube.nanotimer.vo.CubeType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The random-state scrambles shipped with the app, one pool per puzzle that has a solver.
 *
 * <p>Generating one costs the solver's pruning tables first, which is tens of seconds the first
 * time a puzzle is asked. Rather than make whoever opens a puzzle first sit through that, a pool is
 * generated at build time (see {@code SeedScramblesGenerator}) and read out of the APK. It is a
 * floor, not a supply: the moment generation has one of its own that is what gets handed out, and
 * what is left of the pool stays where it is against the next puzzle running dry.
 *
 * <p>No scramble is ever given out twice. How many of a pool have gone is kept in preferences,
 * along with a starting offset drawn once per install, so that two phones do not open on the same
 * scramble.
 */
public class SeedScrambles {

  private static final String PREFS_NAME = "seed_scrambles";
  private static final String KEY_OFFSET = "offset_";
  private static final String KEY_USED = "used_";

  private final Context context;
  private final Map<CubeType, List<String[]>> pools = new HashMap<>();

  SeedScrambles(Context context) {
    this.context = context;
  }

  /**
   * The next unused shipped scramble for this puzzle, or null once the pool is spent, or where the
   * puzzle has none at all.
   *
   * @param mayRead whether the pool may be read out of the APK when it is not in memory yet. That
   *     is file access, so a caller on the UI thread passes false and gets an answer only if some
   *     earlier call already paid for the read.
   */
  public synchronized String[] take(CubeType cubeType, boolean mayRead) {
    List<String[]> pool = pools.get(cubeType);
    if (pool == null) {
      if (!mayRead) {
        return null;
      }
      pool = read(cubeType);
      pools.put(cubeType, pool);
    }
    if (pool.isEmpty()) {
      return null;
    }
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    int used = prefs.getInt(KEY_USED + cubeType.getId(), 0);
    if (used >= pool.size()) {
      return null; // spent: from here on generation is the only source, as it is meant to be
    }
    int offset = getOffset(prefs, cubeType, pool.size());
    prefs.edit().putInt(KEY_USED + cubeType.getId(), used + 1).apply();
    return pool.get((offset + used) % pool.size());
  }

  /** Where in the pool this install starts reading. Drawn once, then kept. */
  private int getOffset(SharedPreferences prefs, CubeType cubeType, int poolSize) {
    int offset = prefs.getInt(KEY_OFFSET + cubeType.getId(), -1);
    if (offset < 0) {
      offset = new Random().nextInt(poolSize);
      prefs.edit().putInt(KEY_OFFSET + cubeType.getId(), offset).apply();
    }
    return offset;
  }

  private List<String[]> read(CubeType cubeType) {
    Integer resource = getResource(cubeType);
    if (resource == null) {
      return Collections.emptyList();
    }
    List<String[]> pool = new ArrayList<>();
    InputStream is = context.getResources().openRawResource(resource);
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        if (!line.trim().isEmpty()) {
          pool.add(ScrambleFormatterService.INSTANCE.parseStringScrambleToArray(line, cubeType));
        }
      }
    } catch (IOException e) {
      Log.e("NanoTimer", "Could not read the shipped scrambles for " + cubeType, e);
    } finally {
      try {
        is.close();
      } catch (IOException e) {
        // Nothing to do about it, and nothing depends on it.
      }
    }
    return pool;
  }

  /**
   * The name the pool is shipped under, which is also its raw resource name. Public because the
   * generator writes the files and cannot see {@code R}.
   */
  public static String getFileName(CubeType cubeType) {
    switch (cubeType) {
      case TWO_BY_TWO:
        return "seed_scrambles_2x2";
      case THREE_BY_THREE:
        return "seed_scrambles_3x3";
      case PYRAMINX:
        return "seed_scrambles_pyraminx";
      case SQUARE1:
        return "seed_scrambles_square1";
      case FTO:
        return "seed_scrambles_fto";
      default:
        return null;
    }
  }

  private static Integer getResource(CubeType cubeType) {
    switch (cubeType) {
      case TWO_BY_TWO:
        return R.raw.seed_scrambles_2x2;
      case THREE_BY_THREE:
        return R.raw.seed_scrambles_3x3;
      case PYRAMINX:
        return R.raw.seed_scrambles_pyraminx;
      case SQUARE1:
        return R.raw.seed_scrambles_square1;
      case FTO:
        return R.raw.seed_scrambles_fto;
      default:
        return null;
    }
  }

}
