package com.cube.nanotimer.coach;

import com.cube.nanotimer.coach.CoachGlossary.Language;
import com.cube.nanotimer.coach.CoachGlossary.Term;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the translated strings and {@link CoachGlossary} saying the same words, so a coach plan
 * names things the way the screen it is drawn on does.
 */
@RunWith(JUnit4.class)
public class CoachGlossaryStringsTest {

  private static final Language[] TRANSLATED = {Language.FR, Language.ES, Language.PT};
  private static final String[] RES_DIRS = {"src/main/res", "../datamodel/src/main/res"};
  private static final Pattern STRING =
      Pattern.compile("<(string|item)(?:\\s+name=\"([^\"]+)\")?[^>]*>([\\s\\S]*?)</\\1>");

  /** A string per translated term that must use the glossary's word in every translation. */
  private static final Map<String, String> BOUND = new LinkedHashMap<>();

  static {
    BOUND.put("2-look", "coach_fig_two_looks");
    BOUND.put("smart cube", "smart_cube_title");
    BOUND.put("cross", "smartcube_step_cross");
    BOUND.put("solve", "analysis_tab_solve");
    BOUND.put("pair", "case_title_f2l");
    BOUND.put("looking", "coach_split_looking");
    BOUND.put("turning", "coach_split_turning");
    BOUND.put("drill", "drill_title");
    BOUND.put("reps", "drill_reps");
    BOUND.put("scramble", "scramble");
    BOUND.put("freezes", "coach_reason_stalling");
    BOUND.put("last layer", "last_layer");
    BOUND.put("focus", "analysis_help_plan_drills_body");
  }

  @Test
  public void everyTranslatedTermIsBound() {
    for (Term term : CoachGlossary.terms()) {
      if (!term.keptInEnglish()) {
        Assert.assertTrue("No string bound to " + term.english(), BOUND.containsKey(term.english()));
      }
    }
  }

  @Test
  public void boundStringsUseTheGlossaryWord() throws IOException {
    for (Language language : TRANSLATED) {
      Map<String, String> strings = strings(language);
      for (Map.Entry<String, String> bound : BOUND.entrySet()) {
        String word = CoachGlossary.term(bound.getKey()).said(language);
        String text = strings.get(bound.getValue());
        Assert.assertNotNull(language + " has no " + bound.getValue(), text);
        Assert.assertTrue(language + " " + bound.getValue() + " should say \"" + word + "\": " + text,
            text.toLowerCase(Locale.ROOT).contains(word));
      }
    }
  }

  /** The old changelog is left as written, some of it never translated, so it is not read. */
  @Test
  public void noTranslationUsesABannedWord() throws IOException {
    List<String> found = new ArrayList<>();
    for (Language language : TRANSLATED) {
      for (Map.Entry<String, String> string : strings(language).entrySet()) {
        if (string.getKey().startsWith("release_notes")) {
          continue;
        }
        String text = string.getValue().toLowerCase(Locale.ROOT);
        for (Term term : CoachGlossary.terms()) {
          for (String banned : term.never(language)) {
            if (Pattern.compile("(?<![\\p{L}\\d-])" + Pattern.quote(banned) + "(?![\\p{L}\\d-])")
                .matcher(text).find()) {
              found.add(language + " " + string.getKey() + ": \"" + banned + "\"");
            }
          }
        }
      }
    }
    Assert.assertTrue(String.join("\n", found), found.isEmpty());
  }

  private static Map<String, String> strings(Language language) throws IOException {
    Map<String, String> strings = new LinkedHashMap<>();
    for (String dir : RES_DIRS) {
      File file = new File(dir, "values-" + language.code() + "/strings.xml");
      if (!file.exists()) {
        continue;
      }
      String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
          .replaceAll("<!--[\\s\\S]*?-->", "");
      Matcher m = STRING.matcher(xml);
      while (m.find()) {
        String name = m.group(2) != null ? m.group(2) : dir + "#" + m.start();
        strings.put(name, unescape(m.group(3)));
      }
    }
    Assert.assertFalse("No strings read for " + language, strings.isEmpty());
    return strings;
  }

  /** The escapes Android resolves in a string resource: quotes, and some letters as backslash-u. */
  private static String unescape(String text) {
    Matcher m = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(text);
    StringBuffer out = new StringBuffer();
    while (m.find()) {
      m.appendReplacement(out, Matcher.quoteReplacement(
          String.valueOf((char) Integer.parseInt(m.group(1), 16))));
    }
    m.appendTail(out);
    return out.toString().replace("\\n", "\n").replace("\\t", "\t").replace("\\'", "'")
        .replace("\\\"", "\"");
  }
}
