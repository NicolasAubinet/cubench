package com.cube.nanotimer.coach;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The cubing words the app's strings and the coach's plans share, in each language the app is
 * translated into.
 *
 * <p>A plan is drawn among the app's own labels, so its sentences have to name things the way the
 * screen around them does: a French plan that writes "cross" under a title reading "Croix" reads as
 * two different things. This table is that agreement. The coach service gives it to the model, and
 * an app test checks the translated strings against it, so neither side can drift alone.
 *
 * <p>Two kinds of term. Some stay in English in every language, because that is what cubers say
 * everywhere (F2L, 2-look, smart cube). The others are translated, and the word here is the one the
 * app already uses. Each term can also list words never to use for it: the literal translations a
 * model reaches for, or words the app used to say.
 */
public final class CoachGlossary {

  /** A language the app is translated into. Anything else is drawn in English, so it is planned in English. */
  public enum Language {
    EN("en", "you"),
    FR("fr", "vous"),
    ES("es", "t\u00fa"),
    PT("pt", "voc\u00ea");

    private final String code;
    private final String address;

    Language(String code, String address) {
      this.code = code;
      this.address = address;
    }

    /** The ISO 639-1 code, as the app's language setting stores it. */
    public String code() {
      return code;
    }

    /** How the solver is addressed, as the app's own strings do. Portuguese is Brazilian. */
    public String address() {
      return address;
    }

    /**
     * The language for a code such as "fr", "pt-BR" or "es_ES".
     *
     * @return {@link #EN} for a null, empty or unknown code, as the app then shows English
     */
    public static Language of(String code) {
      if (code == null) {
        return EN;
      }
      String bare = code.trim().toLowerCase(Locale.ROOT);
      int cut = bare.indexOf('-') >= 0 ? bare.indexOf('-') : bare.indexOf('_');
      if (cut >= 0) {
        bare = bare.substring(0, cut);
      }
      for (Language language : values()) {
        if (language.code.equals(bare)) {
          return language;
        }
      }
      return EN;
    }
  }

  /** One cubing term, the word for it in each language, and the words never to use for it. */
  public static final class Term {
    private final String english;
    private final Map<Language, String> said = new EnumMap<>(Language.class);
    private final Map<Language, List<String>> never = new EnumMap<>(Language.class);

    private Term(String english, String fr, String es, String pt) {
      this.english = english;
      said.put(Language.EN, english);
      said.put(Language.FR, fr);
      said.put(Language.ES, es);
      said.put(Language.PT, pt);
      for (Language language : Language.values()) {
        never.put(language, Collections.<String>emptyList());
      }
    }

    private Term never(Language language, String... words) {
      never.put(language, Collections.unmodifiableList(Arrays.asList(words)));
      return this;
    }

    /** The term in English, which is also how the coach's prompt names it. */
    public String english() {
      return english;
    }

    /** The word to write for it in a language, lower case. */
    public String said(Language language) {
      return said.get(language);
    }

    /** True when every language writes it in English. */
    public boolean keptInEnglish() {
      for (String word : said.values()) {
        if (!word.equals(english)) {
          return false;
        }
      }
      return true;
    }

    /** Words never to write for it in a language, lower case; empty when there are none. */
    public List<String> never(Language language) {
      return never.get(language);
    }
  }

  private static final List<Term> TERMS;

  static {
    List<Term> terms = new ArrayList<>();
    terms.add(kept("F2L"));
    terms.add(kept("OLL"));
    terms.add(kept("PLL"));
    terms.add(kept("CMLL"));
    terms.add(kept("AUF"));
    terms.add(kept("2-look")
        .never(Language.FR, "deux regards", "en deux temps")
        .never(Language.ES, "dos miradas")
        .never(Language.PT, "dois olhares"));
    terms.add(kept("smart cube")
        .never(Language.FR, "cube connect\u00e9", "cube intelligent")
        .never(Language.ES, "cubo inteligente")
        .never(Language.PT, "cubo inteligente"));
    terms.add(kept("look-ahead")
        .never(Language.FR, "anticipation")
        .never(Language.ES, "anticipaci\u00f3n")
        .never(Language.PT, "antecipa\u00e7\u00e3o"));
    terms.add(kept("finger tricks"));
    terms.add(kept("regrip"));
    terms.add(kept("TPS"));
    terms.add(kept("slot"));
    terms.add(new Term("cross", "croix", "cruz", "cruz")
        .never(Language.FR, "cross")
        .never(Language.ES, "cross")
        .never(Language.PT, "cross"));
    terms.add(new Term("pair", "paire", "par", "par"));
    terms.add(new Term("looking", "recherche", "buscar", "procura")
        .never(Language.FR, "reconnaissance")
        .never(Language.ES, "reconocimiento")
        .never(Language.PT, "reconhecimento"));
    terms.add(new Term("turning", "ex\u00e9cution", "girar", "execu\u00e7\u00e3o"));
    terms.add(new Term("drill", "drill", "drill", "treino")
        .never(Language.FR, "entra\u00eenement", "entra\u00eenements")
        .never(Language.ES, "entrenamiento", "entrenamientos")
        .never(Language.PT, "drill", "drills"));
    terms.add(new Term("reps", "essais", "repeticiones", "repeti\u00e7\u00f5es"));
    terms.add(new Term("scramble", "m\u00e9lange", "mezcla", "scramble")
        .never(Language.FR, "scramble", "scrambles")
        .never(Language.ES, "scramble", "scrambles")
        .never(Language.PT, "embaralhamento", "embaralhamentos"));
    terms.add(new Term("freezes", "blocages", "bloqueos", "bloqueios"));
    terms.add(new Term("last layer", "derni\u00e8re couche", "\u00faltima capa", "\u00faltima camada"));
    terms.add(new Term("focus", "point", "punto", "ponto")
        .never(Language.FR, "axe de travail"));
    TERMS = Collections.unmodifiableList(terms);
  }

  private CoachGlossary() {
  }

  private static Term kept(String english) {
    return new Term(english, english, english, english);
  }

  /** Every term, those kept in English first. */
  public static List<Term> terms() {
    return TERMS;
  }

  /**
   * The term with this English name.
   *
   * @throws IllegalArgumentException when there is none
   */
  public static Term term(String english) {
    for (Term term : TERMS) {
      if (term.english.equals(english)) {
        return term;
      }
    }
    throw new IllegalArgumentException("No glossary term " + english);
  }
}
