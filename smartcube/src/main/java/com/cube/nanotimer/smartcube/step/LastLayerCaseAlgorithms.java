package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The algorithms worth showing for a last-layer case, and how many of the people who voted on them
 * use each.
 *
 * <p>Most cases have several good algorithms and no way to choose between them from first
 * principles, so the tie is broken by what people actually do: the counts come from SpeedCubeDB's
 * community votes, taken once by hand and committed, never fetched at run time. They rank the top
 * few algorithms a case is usually taught with rather than every algorithm that exists, so a share
 * here is a share of those, not of the whole world.
 *
 * <p><b>The share is what decides how many are shown.</b> A case where one algorithm has nine votes
 * in ten has one answer and showing three would invent a choice nobody is making; a case split three
 * ways genuinely has three, and the one in front is the one to learn first. So everything at or above
 * {@link #DEFAULT_MIN_SHARE} is shown, and the most used always is, whatever its share.
 *
 * <p>Every algorithm of a case is written for the cube held the way the case is drawn. The most
 * used one turns it nowhere first and {@link LastLayerDiagram} reads the picture off that, so the
 * rest carry whatever rotation picks the cube up the same way. Without that a listed algorithm can
 * be a quarter turn from the picture beside it and still be a correct algorithm for the case, which
 * is the one kind of wrong the case check below cannot see.
 *
 * <p>Every row is checked by {@code LastLayerCaseAlgorithmsTest}: an algorithm that does not solve
 * the case it is filed under fails the build, which is what makes taking a table off a website safe
 * at all. A vote count cannot be checked that way and is only ever used for ordering.
 */
public final class LastLayerCaseAlgorithms {

  /**
   * The share of a case's votes an algorithm needs before it is shown beside the most used one, in
   * percent. Twenty rather than something finer: below about a fifth an algorithm is a minority
   * habit rather than a real alternative, and a list of five is not a recommendation.
   */
  public static final int DEFAULT_MIN_SHARE = 20;

  /** However flat the vote, a case is not a catalogue. */
  private static final int MOST_SHOWN = 4;

  /**
   * How far ahead the most used algorithm has to be before it is called the recommended one: half
   * again the next one's share. A case the world is split on has no recommendation to make, and
   * printing one on a 36-to-36 split would be inventing an answer out of a rounding difference.
   */
  private static final float CLEAR_LEAD = 1.5f;

  /** One row per algorithm: the case it solves, the algorithm, and the votes it holds. */
  private static final String[][] ALGORITHMS = {
    {"pll_aa", "x R' U R' D2 R U' R' D2 R2 x'", "195"},
    {"pll_aa", "y' x L2 D2 L' U' L D2 L' U L'", "47"},
    {"pll_aa", "l' U R' D2 R U' R' D2 R2 x'", "42"},
    {"pll_aa", "y x' R2 D2 R' U' R D2 R' U R' x", "42"},
    {"pll_ab", "x R2 D2 R U R' D2 R U' R x'", "164"},
    {"pll_ab", "y' x L U' L D2 L' U L D2 L2", "31"},
    {"pll_ab", "y x' R U' R D2 R' U R D2 R2 x", "25"},
    {"pll_ab", "R' B' R U' R D R' U R D' R2 B R", "18"},
    {"pll_e", "x' R U' R' D R U R' D' R U R' D R U' R' D' x", "163"},
    {"pll_e", "R' U' R' D' R U' R' D R U R' D' R U R' D R2", "46"},
    {"pll_e", "y R2 U F' R' U R U' R' U R U' R' U R U' F U' R2", "31"},
    {"pll_e", "x' L' U L D' L' U' L D L' U' L D' L' U L D", "17"},
    {"pll_f", "R' U' F' R U R' U' R' F R2 U' R' U' R U R' U R", "193"},
    {"pll_f", "R' F R f' R' F R2 U R' U' R' F' R2 U R' S", "55"},
    {"pll_f", "y' R' U R U' R2 F' U' F U R F R' F' R2", "42"},
    {"pll_f", "R2 F R F' R' U' F' U F R2 U R' U' R", "16"},
    {"pll_ga", "R2 U R' U R' U' R U' R2 D U' R' U R D'", "119"},
    {"pll_ga", "R2 u R' U R' U' R u' R2 F' U F", "24"},
    {"pll_ga", "y R U R' F' R U R' U' R' F R U' R' F R2 U' R' U' R U R' F'", "15"},
    {"pll_ga", "D' R2 U R' U R' U' R U' R2 U' D R' U R", "11"},
    {"pll_gb", "D R' U' R U D' R2 U R' U R U' R U' R2", "143"},
    {"pll_gb", "R' U' R U D' R2 U R' U R U' R U' R2 D", "109"},
    {"pll_gb", "y F' U' F R2 u R' U R U' R u' R2", "57"},
    {"pll_gb", "R' d' F R2 u R' U R U' R u' R2", "36"},
    {"pll_gc", "R2 U' R U' R U R' U R2 D' U R U' R' D", "98"},
    {"pll_gc", "y2 R2 F2 R U2 R U2 R' F R U R' U' R' F R2", "91"},
    {"pll_gc", "D R2 U' R U' R U R' U R2 D' U R U' R'", "75"},
    {"pll_gc", "R2 u' R U' R U R' u R2 f R' f'", "22"},
    {"pll_gd", "R U R' U' D R2 U' R U' R' U R' U R2 D'", "113"},
    {"pll_gd", "D' R U R' U' D R2 U' R U' R' U R' U R2", "35"},
    {"pll_gd", "R U R' y' R2 u' R U' R' U R' u R2", "16"},
    {"pll_gd", "y R2 F' R U R U' R' F' R U2 R' U2 R' F2 R2", "13"},
    {"pll_h", "M2 U M2 U2 M2 U M2", "140"},
    {"pll_h", "M2 U' M2 U2 M2 U' M2", "137"},
    {"pll_h", "R2 S2 R2 U' R2 S2 R2", "17"},
    {"pll_h", "M2 U2 M2 U M2 U2 M2", "14"},
    {"pll_ja", "x R2 F R F' R U2 r' U r U2 x'", "151"},
    {"pll_ja", "y' R' U L' U2 R U' R' U2 R L", "68"},
    {"pll_ja", "y2 L' U' L F L' U' L U L F' L2 U L", "64"},
    {"pll_ja", "y2 R U' L' U R' U2 L U' L' U2 L", "22"},
    {"pll_jb", "R U R' F' R U R' U' R' F R2 U' R'", "211"},
    {"pll_jb", "R U2 R' U' R U2 L' U R' U' L", "13"},
    {"pll_jb", "r' F R F' r U2 R' U R U2 R'", "8"},
    {"pll_jb", "L' U R U' L U2 R' U R U2 R'", "7"},
    {"pll_na", "R U R' U R U R' F' R U R' U' R' F R2 U' R' U2 R U' R'", "149"},
    {"pll_na", "F' R U R' U' R' F R2 F U' R' U' R U F' R'", "61"},
    {"pll_na", "R F U' R' U R U F' R2 F' R U R U' R' F", "37"},
    {"pll_na", "r' D r U2 r' D r U2 r' D r U2 r' D r U2 r' D r", "31"},
    {"pll_nb", "R' U R U' R' F' U' F R U R' F R' F' R U' R", "128"},
    {"pll_nb", "r' D' F r U' r' F' D r2 U r' U' r' F r F'", "110"},
    {"pll_nb", "R' U L' U2 R U' L R' U L' U2 R U' L", "33"},
    {"pll_nb", "R' U R U' R' F' U' F R U R' U' R U' f R f'", "23"},
    {"pll_ra", "R U' R' U' R U R D R' U' R D' R' U2 R'", "151"},
    {"pll_ra", "R U R' F' R U2 R' U2 R' F R U R U2 R'", "62"},
    {"pll_ra", "y' L U2 L' U2 L F' L' U' L U L F L2", "31"},
    {"pll_ra", "R U' R' U' R U R' U R' D' R U' R' D R2 U R'", "18"},
    {"pll_rb", "R' U2 R U2 R' F R U R' U' R' F' R2", "113"},
    {"pll_rb", "y R2 F R U R U' R' F' R U2 R' U2 R", "74"},
    {"pll_rb", "R' U2 R' D' R U' R' D R U R U' R' U' R", "49"},
    {"pll_rb", "y R' U R U R' U' R' D' R U R' D R U2 R", "8"},
    {"pll_t", "R U R' U' R' F R2 U' R' U' R U R' F'", "220"},
    {"pll_t", "R U R' U' R' F R2 U' R' U F' L' U L", "19"},
    {"pll_t", "R2 u R2 u' R2 F2 u' F2 u F2", "19"},
    {"pll_ua", "M2 U M U2 M' U M2", "137"},
    {"pll_ua", "y2 R U R' U R' U' R2 U' R' U R' U R", "120"},
    {"pll_ua", "y' R2 U' S' U2 S U' R2", "81"},
    {"pll_ua", "y2 R2 U' R' U' R U R U R U' R", "73"},
    {"pll_ub", "M2 U' M U2 M' U' M2", "129"},
    {"pll_ub", "y2 R' U R' U' R' U' R' U R U R2", "56"},
    {"pll_ub", "R2 U R U R' U' R' U' R' U R'", "42"},
    {"pll_v", "R' U R' U' R D' R' D R' U D' R2 U' R2 D R2", "106"},
    {"pll_v", "R' U R U' R' f' U' R U2 R' U' R U' R' f R", "95"},
    {"pll_v", "R' U R' U' y R' F' R2 U' R' U R' F R F", "82"},
    {"pll_v", "y R U' R U R' D R D' R U' D R2 U R2 D' R2", "81"},
    {"pll_y", "F R U' R' U' R U R' F' R U R' U' R' F R F'", "169"},
    {"pll_y", "F R' F R2 U' R' U' R U R' F' R U R' U' F'", "59"},
    {"pll_y", "R2 U' R2 U' R2 U F U F' R2 F U' F'", "14"},
    {"pll_y", "F R' F' R U R U' R2 U' R U R f' U' f", "9"},
    {"pll_z", "M' U' M2 U' M2 U' M' U2 M2", "90"},
    {"pll_z", "M2 U M2 U M' U2 M2 U2 M'", "83"},
    {"pll_z", "y M2 U' M2 U' M' U2 M2 U2 M'", "43"},
    {"pll_z", "y M' U M2 U M2 U M' U2 M2", "41"},
    {"oll_1", "R U2 R2 F R F' U2 R' F R F'", "149"},
    {"oll_1", "y R U' R2 D' r U' r' D R2 U R'", "34"},
    {"oll_1", "f R U R' U' R f' U' r' U' R U M'", "12"},
    {"oll_1", "L' U2 L2 F' L' F U2 L F' L' F", "6"},
    {"oll_2", "R U' R2 D' r U r' D R2 U R'", "89"},
    {"oll_2", "y F R U R' U' S R U R' U' f'", "64"},
    {"oll_2", "y F R U R' U' F' f R U R' U' f'", "38"},
    {"oll_2", "y2 r U r' U2 R U2 R' U2 r U' r'", "38"},
    {"oll_3", "f R U R' U' f' U' F R U R' U' F'", "44"},
    {"oll_3", "y2 R' F2 R2 U2 R' F R U2 R2 F2 R", "36"},
    {"oll_3", "y r' R2 U R' U r U2 r' U M'", "29"},
    {"oll_3", "y M R U R' U r U2 r' U M'", "19"},
    {"oll_4", "R' F2 R2 U2 R' F' R U2 R2 F2 R", "42"},
    {"oll_4", "f R U R' U' f' U F R U R' U' F'", "26"},
    {"oll_4", "y R' F R F' U' S R' U' R U R S'", "16"},
    {"oll_4", "y2 F U R U' R' F' U' F R U R' U' F'", "14"},
    {"oll_5", "r' U2 R U R' U r", "77"},
    {"oll_5", "y2 l' U2 L U L' U l", "35"},
    {"oll_5", "y2 R' F2 r U r' F R", "22"},
    {"oll_5", "y2 R' F2 L F L' F R", "2"},
    {"oll_6", "r U2 R' U' R U' r'", "85"},
    {"oll_6", "F U' R2 D R' U' R D' R2 U F'", "3"},
    {"oll_6", "y2 l U2 L' U' L U' l'", "3"},
    {"oll_6", "L F2 l' U' l F' L'", "2"},
    {"oll_7", "r U R' U R U2 r'", "77"},
    {"oll_7", "S' R U R' U R U2 R' U S", "8"},
    {"oll_7", "L' U2 L U2 L F' L' F", "6"},
    {"oll_8", "r' U' R U' R' U2 r", "51"},
    {"oll_8", "y2 l' U' L U' L' U2 l", "46"},
    {"oll_8", "y2 R U2 R' U2 R' F R F'", "26"},
    {"oll_8", "y2 R' F' r U' r' F2 R", "22"},
    {"oll_9", "R U R' U' R' F R2 U R' U' F'", "89"},
    {"oll_9", "y' R U2 R' U' S' R U' R' S", "22"},
    {"oll_9", "y F' U' F r U' r' U r U r'", "7"},
    {"oll_9", "y2 L' U' L U' L F' L' F L' U2 L", "7"},
    {"oll_10", "R U R' U R' F R F' R U2 R'", "58"},
    {"oll_10", "y F U F' R' F R U' R' F' R", "16"},
    {"oll_10", "y M' R' U2 R U R' U R U M", "9"},
    {"oll_10", "y2 L' U' L U L F' L2 U' L U F", "6"},
    {"oll_11", "r' R2 U R' U R U2 R' U M'", "45"},
    {"oll_11", "y2 r U R' U R' F R F' R U2 r'", "37"},
    {"oll_11", "S R U R' U R U2 R' U2 S'", "29"},
    {"oll_11", "M R U R' U R U2 R' U M'", "29"},
    {"oll_12", "M' R' U' R U' R' U2 R U' M", "37"},
    {"oll_12", "y F R U R' U' F' U F R U R' U' F'", "29"},
    {"oll_12", "S R' U' R U' R' U2 R U2 S'", "18"},
    {"oll_12", "y2 M L' U' L U' L' U2 L U' M'", "11"},
    {"oll_13", "F U R U2 R' U' R U R' F'", "43"},
    {"oll_13", "F U R U' R2 F' R U R U' R'", "43"},
    {"oll_13", "r U' r' U' r U r' F' U F", "28"},
    {"oll_13", "y2 f R U R2 U' R' U R U' f'", "7"},
    {"oll_14", "R' F R U R' F' R F U' F'", "56"},
    {"oll_14", "r U R' U' r' F R2 U R' U' F'", "30"},
    {"oll_14", "l' U l U l' U' l F U' F'", "9"},
    {"oll_14", "F' U' L' U L2 F L' U' L' U L", "6"},
    {"oll_15", "r' U' r R' U' R U r' U r", "40"},
    {"oll_15", "y2 l' U' l L' U' L U l' U l", "35"},
    {"oll_15", "r' U' M' U' R U r' U r", "14"},
    {"oll_15", "y2 R' F' R L' U' L U R' F R", "12"},
    {"oll_16", "r U r' R U R' U' r U' r'", "76"},
    {"oll_16", "r U M U R' U' r U' r'", "11"},
    {"oll_16", "y2 R' F R U R' U' F' R U' R' U2 R", "7"},
    {"oll_16", "y2 l U l' L U L' U' l U' l'", "4"},
    {"oll_17", "R U R' U R' F R F' U2 R' F R F'", "58"},
    {"oll_17", "y2 F R' F' R U S' R U' R' S", "51"},
    {"oll_17", "y2 F R' F' R2 r' U R U' R' U' M'", "12"},
    {"oll_17", "y' F' r U r' U' S r' F r S'", "8"},
    {"oll_18", "R U2 R2 F R F' U2 M' U R U' r'", "42"},
    {"oll_18", "y' r U R' U R U2 r2 U' R U' R' U2 r", "23"},
    {"oll_18", "F S' R U' R' S R U2 R' U' F'", "23"},
    {"oll_18", "y' R D r' U' r D' R' U' R2 F R F' R", "10"},
    {"oll_19", "S' R U R' S U' R' F R F'", "48"},
    {"oll_19", "y' M U R U R' U' M' R' F R F'", "36"},
    {"oll_19", "y' R' U2 F R U R' U' F2 U2 F R", "14"},
    {"oll_19", "y' r' R U R U R' U' r R2 F R F'", "8"},
    {"oll_20", "r U R' U' M2 U R U' R' U' M'", "56"},
    {"oll_20", "M' U2 M U2 M' U M U2 M' U2 M", "42"},
    {"oll_20", "S' R U R' S U' M' U R U' r'", "28"},
    {"oll_20", "S R' U' R U R U R U' R' S'", "24"},
    {"oll_21", "R U R' U R U' R' U R U2 R'", "77"},
    {"oll_21", "y R U2 R' U' R U R' U' R U' R'", "48"},
    {"oll_21", "y F R U R' U' R U R' U' R U R' U' F'", "34"},
    {"oll_21", "R' U' R U' R' U R U' R' U2 R", "13"},
    {"oll_22", "R U2 R2 U' R2 U' R2 U2 R", "98"},
    {"oll_22", "R' U2 R2 U R2 U R2 U2 R'", "18"},
    {"oll_22", "f R U R' U' S' R U R' U' F'", "17"},
    {"oll_22", "f R U R' U' f' F R U R' U' F'", "4"},
    {"oll_23", "R2 D R' U2 R D' R' U2 R'", "104"},
    {"oll_23", "y2 R2 D' R U2 R' D R U2 R", "40"},
    {"oll_23", "R U R' U R U2 R2 U' R U' R' U2 R", "13"},
    {"oll_23", "y R U R' U' R U' R' U2 R U' R' U2 R U R'", "5"},
    {"oll_24", "r U R' U' r' F R F'", "117"},
    {"oll_24", "y2 R' F' r U R U' r' F", "14"},
    {"oll_24", "y' x' R U R' D R U' R' D' x", "12"},
    {"oll_24", "y R U R D R' U' R D' R2", "12"},
    {"oll_25", "R U2 R D R' U2 R D' R2", "100"},
    {"oll_25", "y F' r U R' U' r' F R", "77"},
    {"oll_25", "F R' F' r U R U' r'", "63"},
    {"oll_25", "x R' U R D' R' U' R D x'", "13"},
    {"oll_26", "R U2 R' U' R U' R'", "83"},
    {"oll_26", "y' R' U' R U' R' U2 R", "42"},
    {"oll_26", "y L' U' L U' L' U2 L", "24"},
    {"oll_26", "y L' U R U' L U R'", "4"},
    {"oll_27", "R U R' U R U2 R'", "112"},
    {"oll_27", "y' R' U2 R U R' U R", "15"},
    {"oll_27", "y L' U2 L U L' U L", "8"},
    {"oll_27", "y2 L U L' U L U2 L'", "7"},
    {"oll_28", "r U R' U' M U R U' R'", "113"},
    {"oll_28", "r U R' U' r' R U R U' R'", "26"},
    {"oll_28", "R' F R S R' F' R S'", "25"},
    {"oll_28", "y2 M' U M U2 M' U M", "17"},
    {"oll_29", "r2 D' r U r' D r2 U' r' U' r", "57"},
    {"oll_29", "y R U R' U' R U' R' F' U' F R U R'", "40"},
    {"oll_29", "y S' R U R' U' R' F R F' U S", "25"},
    {"oll_29", "M U R U R' U' R' F R F' M'", "19"},
    {"oll_30", "r' D' r U' r' D r2 U' r' U r U r'", "46"},
    {"oll_30", "y' F U R U2 R' U' R U2 R' U' F'", "44"},
    {"oll_30", "y' F R' F R2 U' R' U' R U R' F2", "29"},
    {"oll_30", "y2 S' R' U' R f R' U R U' F'", "14"},
    {"oll_31", "R' U' F U R U' R' F' R", "77"},
    {"oll_31", "y2 S' L' U' L U L F' L' f", "13"},
    {"oll_31", "y S R U R' U' f' U' F", "12"},
    {"oll_31", "y' F R' F' R U R U R' U' R U' R'", "7"},
    {"oll_32", "S R U R' U' R' F R f'", "74"},
    {"oll_32", "y2 L U F' U' L' U L F L'", "20"},
    {"oll_32", "R U B' U' R' U R B R'", "14"},
    {"oll_32", "y' R' F R F' U' r U' r' U r U r'", "6"},
    {"oll_33", "R U R' U' R' F R F'", "116"},
    {"oll_33", "y2 L' U' L U L F' L' F", "5"},
    {"oll_33", "y2 r' F' r U r U' r' F", "3"},
    {"oll_33", "R U R' F' U' F R U' R'", "2"},
    {"oll_34", "f R f' U' r' U' R U M'", "80"},
    {"oll_34", "y R U R2 U' R' F R U R U' F'", "55"},
    {"oll_34", "y' F R U R' U' R' F' r U R U' r'", "27"},
    {"oll_34", "y R U R' U' B' R' F R F' B", "18"},
    {"oll_35", "R U2 R2 F R F' R U2 R'", "56"},
    {"oll_35", "f R U R' U' f' R U R' U R U2 R'", "6"},
    {"oll_35", "y L' U2 L2 F' L' F L' U2 L", "2"},
    {"oll_36", "R U R2 F' U' F U R2 U2 R'", "58"},
    {"oll_36", "y L' U' L U' L' U L U L F' L' F", "53"},
    {"oll_36", "y R U R' F' R U R' U' R' F R U' R' F R F'", "33"},
    {"oll_36", "y R' F' U' F2 U R U' R' F' R", "16"},
    {"oll_37", "F R' F' R U R U' R'", "77"},
    {"oll_37", "F R U' R' U' R U R' F'", "72"},
    {"oll_37", "y F' r U r' U' r' F r", "8"},
    {"oll_38", "R U R' U R U' R' U' R' F R F'", "91"},
    {"oll_38", "y F R U' R' S U' R U R' f'", "7"},
    {"oll_38", "r U R' U' r' F R U R U' R' F'", "2"},
    {"oll_38", "y2 L U L' U L U' L' U' L' B L B'", "2"},
    {"oll_39", "f' r U r' U' r' F r S", "46"},
    {"oll_39", "R U R' F' U' F U R U2 R'", "42"},
    {"oll_39", "y2 L F' L' U' L U F U' L'", "32"},
    {"oll_39", "f' L F L' U' L' U L S", "17"},
    {"oll_40", "R' F R U R' U' F' U R", "68"},
    {"oll_40", "y2 f R' F' R U R U' R' S'", "38"},
    {"oll_40", "y2 L' U' L F U F' U' L' U2 L", "1"},
    {"oll_41", "R U R' U R U2 R' F R U R' U' F'", "54"},
    {"oll_41", "F U R2 D R' U' R D' R2 F'", "21"},
    {"oll_41", "y S U' R' F' U' F U R S'", "12"},
    {"oll_41", "y2 M U' F' L' U' L U F M'", "10"},
    {"oll_42", "R' U' R U' R' U2 R F R U R' U' F'", "41"},
    {"oll_42", "y F S' R U R' U' F' U S", "26"},
    {"oll_42", "y R' F R F' R' F R F' R U R' U' R U R'", "23"},
    {"oll_43", "R' U' F' U F R", "62"},
    {"oll_43", "y F' U' L' U L F", "26"},
    {"oll_43", "y' f' L' U' L U f", "24"},
    {"oll_43", "y' B' U' R' U R B", "7"},
    {"oll_44", "f R U R' U' f'", "75"},
    {"oll_44", "y2 F U R U' R' F'", "68"},
    {"oll_44", "y R U B U' B' R'", "6"},
    {"oll_44", "y' L U F U' F' L'", "2"},
    {"oll_45", "F R U R' U' F'", "104"},
    {"oll_45", "y R' F' U' F U R", "17"},
    {"oll_45", "y2 f U R U' R' f'", "5"},
    {"oll_45", "y2 F' L' U' L U F", "4"},
    {"oll_46", "R' U' R' F R F' U R", "76"},
    {"oll_46", "R' F' U' F R U' R' U2 R", "6"},
    {"oll_46", "y F R U R' U' F' U' R U R' U R U2 R'", "3"},
    {"oll_46", "l' U2 L2 F' L' F U L' U l", "3"},
    {"oll_47", "F' L' U' L U L' U' L U F", "38"},
    {"oll_47", "R' U' R' F R F' R' F R F' U R", "28"},
    {"oll_47", "y' R' F' U' F U F' U' F U R", "9"},
    {"oll_48", "F R U R' U' R U R' U' F'", "69"},
    {"oll_48", "y2 f U R U' R' U R U' R' f'", "8"},
    {"oll_48", "R U2 R' U' R U R' U2 R' F R F'", "3"},
    {"oll_48", "F R' F' U2 R U R' U R2 U2 R'", "1"},
    {"oll_49", "r U' r2 U r2 U r2 U' r", "59"},
    {"oll_49", "y2 l U' l2 U l2 U l2 U' l", "14"},
    {"oll_49", "y2 R B' R2 F R2 B R2 F' R", "8"},
    {"oll_49", "R' F R' F' R2 U2 B' R B R'", "4"},
    {"oll_50", "r' U r2 U' r2 U' r2 U r'", "51"},
    {"oll_50", "y2 R' F R2 B' R2 F' R2 B R'", "11"},
    {"oll_50", "y' R U2 R' U' R U' R' F R U R' U' F'", "11"},
    {"oll_50", "y2 l' U l2 U' l2 U' l2 U l'", "11"},
    {"oll_51", "F U R U' R' U R U' R' F'", "64"},
    {"oll_51", "y2 f R U R' U' R U R' U' f'", "53"},
    {"oll_51", "y R' U' R' F R F' R U' R' U2 R", "5"},
    {"oll_51", "y' r' F' U' F U F' U' F U r", "3"},
    {"oll_52", "R' F' U' F U' R U R' U R", "56"},
    {"oll_52", "y2 R U R' U R U' B U' B' R'", "44"},
    {"oll_52", "y2 R U R' U R d' R U' R' F'", "16"},
    {"oll_52", "y2 R U R' U R U' y R U' R' F'", "12"},
    {"oll_53", "r' U' R U' R' U R U' R' U2 r", "33"},
    {"oll_53", "y2 l' U' L U' L' U L U' L' U2 l", "25"},
    {"oll_53", "y r' U2 R U R' U' R U R' U r", "24"},
    {"oll_53", "y' l' U2 L U L' U' L U L' U l", "13"},
    {"oll_54", "r U R' U R U' R' U R U2 r'", "45"},
    {"oll_54", "y' r U2 R' U' R U R' U' R U' r'", "26"},
    {"oll_54", "y' r U r' R U R' U' R U R' U' r U' r'", "4"},
    {"oll_54", "y2 l U L' U L U' L' U L U2 l'", "3"},
    {"oll_55", "R' F U R U' R2 F' R2 U R' U' R", "52"},
    {"oll_55", "y R U2 R2 U' R U' R' U2 F R F'", "34"},
    {"oll_55", "R' F R U R U' R2 F' R2 U' R' U R U R'", "34"},
    {"oll_55", "y r U2 R2 F R F' U2 r' F R F'", "19"},
    {"oll_56", "r U r' U R U' R' U R U' R' r U' r'", "33"},
    {"oll_56", "r U r' U R U' R' M' U R U2 r'", "31"},
    {"oll_56", "F R U R' U' R F' r U R' U' r'", "19"},
    {"oll_56", "r' U' r U' R' U R U' R' U R r' U r", "17"},
    {"oll_57", "R U R' U' M' U R U' r'", "96"},
    {"oll_57", "y R U' R' S' R U R' S", "12"},
    {"oll_57", "y R U R' S' R U' R' S", "8"},
    {"oll_57", "R U R' U' R' r U R U' r'", "7"},
  };

  private LastLayerCaseAlgorithms() {
  }

  /** What to show for a case, most used first, or an empty list for a case with no algorithms. */
  public static List<Algorithm> forCase(String caseCode) {
    return forCase(caseCode, DEFAULT_MIN_SHARE);
  }

  /** @param minShare the share of the case's votes an algorithm needs, in percent */
  public static List<Algorithm> forCase(String caseCode, int minShare) {
    List<Algorithm> all = new ArrayList<Algorithm>();
    int votes = 0;
    for (String[] row : ALGORITHMS) {
      if (row[0].equals(caseCode)) {
        votes += Integer.parseInt(row[2]);
      }
    }
    if (votes == 0) {
      return Collections.emptyList();
    }
    for (String[] row : ALGORITHMS) {
      if (!row[0].equals(caseCode)) {
        continue;
      }
      int share = Math.round(Integer.parseInt(row[2]) * 100f / votes);
      if (all.isEmpty() || (share >= minShare && all.size() < MOST_SHOWN)) {
        all.add(new Algorithm(row[1], share, false));
      }
    }
    if (all.size() > 1 && all.get(0).getShare() >= all.get(1).getShare() * CLEAR_LEAD) {
      all.set(0, new Algorithm(all.get(0).getMoves(), all.get(0).getShare(), true));
    }
    return Collections.unmodifiableList(all);
  }

  /**
   * Whether an algorithm solves a case, which is the only thing that makes one worth keeping. Asked
   * of the table by its test, and of anything a user types in before it is stored against a case.
   *
   * <p>An OLL is solved when the layer is one colour and a PLL when the cube is: that is all each
   * claims to do, and holding an OLL algorithm to a solved cube would reject every correct one but
   * the particular one the case was scrambled with.
   */
  public static boolean solves(String caseCode, String algorithm) {
    if (caseCode == null || algorithm == null || algorithm.trim().isEmpty()) {
      return false;
    }
    try {
      String state = Notation.caseState(algorithm);
      String name = caseCode.startsWith("oll_") ? LastLayerCases.orientation(state, Cubies.D)
          : LastLayerCases.permutation(state, Cubies.D);
      return name != null && caseCode.endsWith("_" + name);
    } catch (RuntimeException e) {
      return false; // unreadable notation, or a sequence that does not put the cube back down
    }
  }

  /** Every case there are algorithms for, in the order the table holds them. */
  static List<String[]> rows() {
    List<String[]> rows = new ArrayList<String[]>();
    for (String[] row : ALGORITHMS) {
      rows.add(row);
    }
    return rows;
  }

  /** One algorithm for a case, with how much of the case's vote it holds. */
  public static final class Algorithm {

    private final String moves;
    private final int share;
    private final boolean recommended;

    Algorithm(String moves, int share, boolean recommended) {
      this.moves = moves;
      this.share = share;
      this.recommended = recommended;
    }

    public String getMoves() {
      return moves;
    }

    /** How many of the case's votes this algorithm holds, in percent. */
    public int getShare() {
      return share;
    }

    /**
     * The one to learn first, said out loud only when the vote is not close. Everything here is in
     * most-used order whether or not anything carries this.
     */
    public boolean isRecommended() {
      return recommended;
    }
  }
}
