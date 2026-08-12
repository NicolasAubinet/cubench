package com.cube.nanotimer.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A named set of cases to drill: the ten of 2-look OLL, the eight learned this month, whatever set
 * the user keeps coming back to.
 *
 * <p><b>A preset is a stored copy of a pick and nothing more.</b> Applying one ticks those cases,
 * which is the same act as ticking them by hand, so a preset opens no history of its own and a case
 * keeps one set of figures however the drill that met it was reached. That is what lets these stay
 * a convenience rather than a second kind of drill.
 *
 * <p>They are stored as JSON rather than a delimited list because the name is the user's own text,
 * and a preset called "R, U and away" must not saw the stored value in half.
 */
public class DrillCasePreset {

  private static final String NAME = "name";
  private static final String CASES = "cases";

  private String name;
  private final Set<String> cases = new LinkedHashSet<String>();

  public DrillCasePreset(String name, Collection<String> cases) {
    this.name = name;
    this.cases.addAll(cases);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Set<String> getCases() {
    return cases;
  }

  public void setCases(Collection<String> cases) {
    this.cases.clear();
    this.cases.addAll(cases);
  }

  /** Whether this preset is exactly the given pick, which is the whole of what marks it as active. */
  public boolean holds(Collection<String> picked) {
    return new HashSet<String>(cases).equals(new HashSet<String>(picked));
  }

  /** The preset the given pick stands at, or null if it stands at none of them. */
  public static DrillCasePreset matching(List<DrillCasePreset> presets, Collection<String> picked) {
    for (DrillCasePreset preset : presets) {
      if (preset.holds(picked)) {
        return preset;
      }
    }
    return null;
  }

  public static DrillCasePreset named(List<DrillCasePreset> presets, String name) {
    for (DrillCasePreset preset : presets) {
      if (preset.getName().equals(name)) {
        return preset;
      }
    }
    return null;
  }

  public static String toJson(List<DrillCasePreset> presets) {
    JSONArray array = new JSONArray();
    try {
      for (DrillCasePreset preset : presets) {
        JSONObject stored = new JSONObject();
        stored.put(NAME, preset.name);
        stored.put(CASES, new JSONArray(preset.cases));
        array.put(stored);
      }
    } catch (JSONException e) {
      return "[]";
    }
    return array.toString();
  }

  /** Anything that cannot be read back is read as no presets: a set of cases half restored would
      be applied to a drill without ever looking wrong. */
  public static List<DrillCasePreset> fromJson(String stored) {
    List<DrillCasePreset> presets = new ArrayList<DrillCasePreset>();
    if (stored == null || stored.isEmpty()) {
      return presets;
    }
    try {
      JSONArray array = new JSONArray(stored);
      for (int i = 0; i < array.length(); i++) {
        JSONObject entry = array.getJSONObject(i);
        JSONArray cases = entry.getJSONArray(CASES);
        List<String> codes = new ArrayList<String>();
        for (int j = 0; j < cases.length(); j++) {
          codes.add(cases.getString(j));
        }
        presets.add(new DrillCasePreset(entry.getString(NAME), codes));
      }
    } catch (JSONException e) {
      return new ArrayList<DrillCasePreset>();
    }
    return presets;
  }
}
