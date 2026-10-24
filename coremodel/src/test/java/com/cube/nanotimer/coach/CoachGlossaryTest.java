package com.cube.nanotimer.coach;

import com.cube.nanotimer.coach.CoachGlossary.Language;
import com.cube.nanotimer.coach.CoachGlossary.Term;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@RunWith(JUnit4.class)
public class CoachGlossaryTest {

  @Test
  public void readsBareAndRegionalCodes() {
    Assert.assertEquals(Language.FR, Language.of("fr"));
    Assert.assertEquals(Language.PT, Language.of("pt-BR"));
    Assert.assertEquals(Language.ES, Language.of("es_ES"));
    Assert.assertEquals(Language.PT, Language.of(" PT "));
  }

  /** The app shows English for a language it has no strings for, so the plan follows. */
  @Test
  public void unknownCodesAreEnglish() {
    Assert.assertEquals(Language.EN, Language.of(null));
    Assert.assertEquals(Language.EN, Language.of(""));
    Assert.assertEquals(Language.EN, Language.of("de"));
  }

  @Test
  public void everyTermHasAWordInEveryLanguage() {
    for (Term term : CoachGlossary.terms()) {
      for (Language language : Language.values()) {
        String word = term.said(language);
        Assert.assertNotNull(term.english() + " " + language, word);
        Assert.assertFalse(term.english() + " " + language, word.trim().isEmpty());
      }
    }
  }

  @Test
  public void aWordIsNeverAlsoBanned() {
    for (Term term : CoachGlossary.terms()) {
      for (Language language : Language.values()) {
        for (String banned : term.never(language)) {
          Assert.assertEquals(banned, banned.toLowerCase(Locale.ROOT), banned);
          Assert.assertNotEquals(term.english() + " " + language, term.said(language), banned);
        }
      }
    }
  }

  @Test
  public void termNamesAreUnique() {
    Set<String> seen = new HashSet<>();
    for (Term term : CoachGlossary.terms()) {
      Assert.assertTrue(term.english(), seen.add(term.english()));
    }
    Assert.assertSame(CoachGlossary.terms().get(0), CoachGlossary.term("F2L"));
  }

  @Test
  public void keptTermsAreEnglishEverywhere() {
    Assert.assertTrue(CoachGlossary.term("2-look").keptInEnglish());
    Assert.assertFalse(CoachGlossary.term("cross").keptInEnglish());
    Assert.assertFalse(CoachGlossary.term("scramble").keptInEnglish());
  }
}
