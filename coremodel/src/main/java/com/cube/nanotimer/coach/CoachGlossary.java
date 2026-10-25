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
 *
 * <p>A language can also list phrasings: turns of phrase a model carries over from English, each
 * with what that language's cubers write instead. Their wordings are banned like a term's.
 */
public final class CoachGlossary {

  /** A language the app is translated into. Anything else is drawn in English, so it is planned in English. */
  public enum Language {
    EN("en", "you"),
    FR("fr", "vous"),
    ES("es", "tú"),
    PT("pt", "você");

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

  /**
   * A turn of phrase a plan writes its own language's way: the wordings a model carries over from
   * English, which read as a translation, and what a cuber of that language writes instead.
   */
  public static final class Phrasing {
    private final List<String> avoided;
    private final String instead;

    private Phrasing(String instead, String... avoided) {
      this.instead = instead;
      this.avoided = Collections.unmodifiableList(Arrays.asList(avoided));
    }

    /** The wordings never written, lower case; empty when the phrasing is only an example. */
    public List<String> avoided() {
      return avoided;
    }

    /** What to write instead, with an example, as the prompt gives it to the model. */
    public String instead() {
      return instead;
    }
  }

  private static final List<Term> TERMS;
  private static final Map<Language, List<Phrasing>> PHRASINGS = new EnumMap<>(Language.class);

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
        .never(Language.FR, "cube connecté", "cube intelligent")
        .never(Language.ES, "cubo inteligente")
        .never(Language.PT, "cubo inteligente"));
    terms.add(kept("look-ahead")
        .never(Language.FR, "anticipation")
        .never(Language.ES, "anticipación")
        .never(Language.PT, "antecipação"));
    terms.add(kept("finger tricks"));
    terms.add(kept("regrip"));
    terms.add(kept("TPS"));
    terms.add(kept("slot"));
    terms.add(new Term("cross", "croix", "cruz", "cruz")
        .never(Language.FR, "cross")
        .never(Language.ES, "cross")
        .never(Language.PT, "cross"));
    terms.add(new Term("solve", "résolution", "resolución", "resolução")
        .never(Language.FR, "solve", "solves")
        .never(Language.ES, "solve", "solves")
        .never(Language.PT, "solve", "solves"));
    terms.add(new Term("pair", "paire", "par", "par"));
    terms.add(new Term("looking", "pause", "buscar", "procura")
        .never(Language.FR, "reconnaissance", "de recherche")
        .never(Language.ES, "reconocimiento")
        .never(Language.PT, "reconhecimento"));
    terms.add(new Term("turning", "exécution", "girar", "execução"));
    terms.add(new Term("drill", "drill", "drill", "treino")
        .never(Language.FR, "entraînement", "entraînements")
        .never(Language.ES, "entrenamiento", "entrenamientos")
        .never(Language.PT, "drill", "drills"));
    terms.add(new Term("reps", "essais", "repeticiones", "repetições"));
    terms.add(new Term("scramble", "mélange", "mezcla", "scramble")
        .never(Language.FR, "scramble", "scrambles")
        .never(Language.ES, "scramble", "scrambles")
        .never(Language.PT, "embaralhamento", "embaralhamentos"));
    terms.add(new Term("freezes", "blocages", "bloqueos", "bloqueios"));
    terms.add(new Term("last layer", "dernière couche", "última capa", "última camada"));
    terms.add(new Term("focus", "point", "punto", "ponto")
        .never(Language.FR, "axe de travail"));
    TERMS = Collections.unmodifiableList(terms);

    for (Language language : Language.values()) {
      PHRASINGS.put(language, Collections.<Phrasing>emptyList());
    }
    // Ruled by the owner on reading a French plan, 2026-10-25.
    PHRASINGS.put(Language.FR, Collections.unmodifiableList(Arrays.asList(
        new Phrasing("F2L is plural, the first two layers: \"les F2L\", \"dans les F2L\", \"vos F2L\"",
            "le f2l", "du f2l", "au f2l", "votre f2l", "ce f2l"),
        new Phrasing("a pair or a case is résolu or placé, never rentré: \"résoudre chaque paire à un"
            + " rythme régulier\", \"20 OLL n'ont encore jamais été résolus en un seul algorithme\"",
            "rentrer", "rentre", "rentrent", "rentré", "rentrés", "rentrée", "rentrées"),
        new Phrasing("turning stops, not the hands: \"l'exécution s'arrête\", \"les mouvements"
            + " s'arrêtent\", \"les deux tiers du temps de la croix se passent sans aucun mouvement\"",
            "les mains s'arrêtent", "mains arrêtées"),
        new Phrasing("time stopped set against time turning: \"la croix passe plus de temps à l'arrêt"
            + " qu'en mouvement\"; turning never tourne", "qu'elle ne tourne", "l'exécution tourne"),
        new Phrasing("what a freeze reveals: \"ces arrêts trahissent une planification inachevée pendant"
            + " l'inspection\"", "sont de la planification", "n'a pas été finie"),
        new Phrasing("point names a focus of the plan only, never a step without one: \"la seule étape"
            + " qui n'a pas besoin de travail cette semaine\"", "sans point"),
        new Phrasing("time to win back: \"il y a beaucoup de temps à gagner\"", "marge à récupérer"),
        new Phrasing("a focus's drill is just named, never attaché: \"utilisez le drill de croix\"",
            "attaché", "attachée", "attachés", "attachées"),
        new Phrasing("time without turning: \"plus d'un quart de l'exécution se passe sans aucun"
            + " mouvement\"; turning is never immobile", "immobile"),
        new Phrasing("a part of a time: \"6,91 secondes d'exécution, dont 1,88 seconde de blocages\"",
            "de celles-là", "de ceux-là"),
        new Phrasing("œ is written as the ligature: \"coup d'œil\", \"cœur\"", "oeil", "coeur"),
        new Phrasing("a step set against what it usually takes: \"un peu moins que la moyenne pour"
            + " cette étape\"", "part attendue", "part habituelle"),
        new Phrasing("a step prend its time, never tient: \"l'étape prend 4,77 secondes\"",
            "tient", "tiennent"),
        new Phrasing("OLL, PLL and F2L are masculine: \"un OLL\", \"aucun PLL\", \"l'OLL 7 appris juste"
            + " avant\", \"vos F2L\"", "une oll", "une pll", "aucune oll", "aucune pll",
            "cette oll", "cette pll", "la oll", "la pll"),
        new Phrasing("a step is résolu or fait in two algorithms, never pris: \"aucun PLL fait en deux"
            + " algorithmes\", \"44 ont résolu l'orientation de la dernière couche en plus d'un"
            + " algorithme\"", "pris en plus d'un algorithme", "prise en plus d'un algorithme",
            "prises en plus d'un algorithme", "ont pris l'orientation", "ont pris la permutation"),
        new Phrasing("the solver turns, the cube never turns itself: \"la croix doit être résolue de"
            + " mémoire\"", "se tourne", "se tournent"),
        new Phrasing("the drill dealing F2L pairs is the \"drill de F2L\"", "drill de paires"),
        new Phrasing("look-ahead running out: \"la paire en cours est exécutée trop vite pour trouver"
            + " la suivante pendant son exécution\""))));
  }

  private CoachGlossary() {
  }

  private static Term kept(String english) {
    return new Term(english, english, english, english);
  }

  /** The phrasings a plan in this language writes its own way; empty for English. */
  public static List<Phrasing> phrasings(Language language) {
    return PHRASINGS.get(language);
  }

  /** Every word or wording never written in a language: each term's, then each phrasing's. */
  public static List<String> never(Language language) {
    List<String> words = new ArrayList<>();
    for (Term term : TERMS) {
      words.addAll(term.never(language));
    }
    for (Phrasing phrasing : PHRASINGS.get(language)) {
      words.addAll(phrasing.avoided());
    }
    return Collections.unmodifiableList(words);
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
