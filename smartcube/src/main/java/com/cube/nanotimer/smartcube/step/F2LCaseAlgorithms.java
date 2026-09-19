package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The algorithms F2L cases are usually solved with, for every case {@link F2LCases} names: the 41
 * basic ones and the advanced ones with a piece trapped in another slot. A pair turned with none of
 * them is one worth pointing out.
 *
 * <p>Gathered by hand from published algorithm sheets and committed, never fetched. Every algorithm
 * is written for the pair going into front right with the cross down: a sheet's back-slot algorithm
 * is kept behind the rotation that brings its slot there, since it is often not the front one
 * mirrored.
 *
 * <p><b>Each row holds the community votes of every spelling of it</b>, taken once with the table,
 * and a case's rows run most voted first. That is what says which algorithm to recommend: the table
 * lists what people are taught, and the votes say which of it they turn. A row no sheet counted
 * votes for holds none and sits at the back.
 *
 * <p>Some basic ones only work with another slot still empty, and say which: they break that slot's
 * pair, so they are usual for the case but not something to show a solver whose slot is filled.
 *
 * <p>{@code F2LCaseAlgorithmsTest} checks that every row solves the case it is filed under and that
 * a basic one disturbs no slot but the one it names, which is what makes taking a table off a
 * website safe.
 */
public final class F2LCaseAlgorithms {

  /** One row per algorithm: the case, the algorithm, the slot it needs empty ("" for none), votes. */
  private static final String[][] ALGORITHMS = {
    {"1", "U R U' R'", "", "219"},
    {"1", "R' F R F'", "", "89"},
    {"1", "y' r' U' R U M'", "", "40"},
    {"1", "y U F' L F L2 U L", "", "11"},
    {"1", "U2 R U2 R'", "", "2"},
    {"1", "M' U R U' r'", "", "0"},
    {"1", "y2 U' r U B' U' B r'", "", "0"},
    {"2", "U' F' U F", "", "136"},
    {"2", "F R' F' R", "", "69"},
    {"2", "y2 l U L' U' M'", "", "15"},
    {"2", "y U r' U' F U F' r", "", "1"},
    {"2", "y' U2 R' U2 R", "", "1"},
    {"3", "F' U' F", "", "174"},
    {"3", "S U R U' R' S'", "", "6"},
    {"3", "y U2 R' F R U R' F' R", "", "3"},
    {"3", "y2 U' R U B' U' B R'", "", "1"},
    {"3", "y U S' F U' F' U S", "", "0"},
    {"3", "y' U2 r' R' F R F' r", "", "0"},
    {"4", "R U R'", "", "154"},
    {"4", "y S' L F' L' f", "", "2"},
    {"4", "y U' M L' U L U' M'", "", "2"},
    {"4", "y2 U f R U R' U2 f' r x'", "", "0"},
    {"5", "U' R U R' U2 R U' R'", "", "87"},
    {"5", "y U R' F r U' r' F' R", "", "41"},
    {"5", "F2 L' U' L U F2", "", "23"},
    {"5", "y U2 F R U R' U2 F'", "", "21"},
    {"5", "y' U' R' F R U R' U' F' R", "", "19"},
    {"5", "U' R U R' U' R U2 R'", "", "17"},
    {"5", "U' R U R' U R' F R F'", "", "5"},
    {"5", "U F' R' F' R F", "fl", "0"},
    {"5", "U R F R' F' R'", "br", "0"},
    {"6", "d R' U' R U2 R' U R", "", "69"},
    {"6", "U' r U' R' U R U r'", "", "54"},
    {"6", "y2 U r U' r' U' L U F L'", "", "12"},
    {"6", "y U L' U' L U L' U2 L", "", "8"},
    {"6", "y F2 R U R' U' F2", "", "2"},
    {"6", "y R' F2 R U R' U' F2 U R", "", "1"},
    {"6", "U' F' R' F R F", "fl", "0"},
    {"6", "U' R F R F' R'", "br", "0"},
    {"6", "y' U2 R' F' U' F U2 R", "", "0"},
    {"7", "U' R U2 R' U' R U2 R'", "", "42"},
    {"7", "U' R U2 R' U2 R U' R'", "", "33"},
    {"7", "M' U' M U2 r U' r'", "", "31"},
    {"7", "y F U R U2 R' U F'", "", "20"},
    {"7", "y l U2 L2 U' L2 U' l'", "", "15"},
    {"7", "U' R U2 R' U R' F R F'", "", "5"},
    {"7", "y' F R U R2 U' R F'", "", "3"},
    {"7", "y U' F U2 R U' R' U F'", "", "2"},
    {"8", "r' U2 R2 U R2 U r", "", "48"},
    {"8", "d R' U2 R U R' U2 R", "", "47"},
    {"8", "d R' U2 R U2 R' U R", "", "29"},
    {"8", "y U' R' U2 R U R' U R U2 L' U L", "", "1"},
    {"8", "U R' F R F' R' F R F' R U' R'", "", "0"},
    {"8", "y U L' U2 L U' L F' L' F", "", "0"},
    {"8", "y2 f' L' U' L2 U L' f", "", "0"},
    {"9", "U' R U' R' d R' U' R", "", "55"},
    {"9", "d R' U' R U' R' U' R", "", "46"},
    {"9", "F R U R' U' F' R U' R'", "", "14"},
    {"9", "y2 y U2 R' U R U R' U' R", "", "2"},
    {"9", "y F2 U R U' R' F2", "", "1"},
    {"9", "R' U' R F' U' F", "br", "0"},
    {"9", "F U' F2 U' F", "fl", "0"},
    {"9", "y L' U L U' L' U' L U2 L' U L", "", "0"},
    {"9", "y' U2 r U R' U R' U' R2 U' r'", "", "0"},
    {"10", "U' R U R' U R U R'", "", "61"},
    {"10", "U F' U F U' R U R'", "", "20"},
    {"10", "U2 R U' R' U' R U R'", "", "15"},
    {"10", "R d' R U R' U2 F'", "", "7"},
    {"10", "y2 B2 U' R' U R B2", "", "3"},
    {"10", "F U F' R U R'", "fl", "0"},
    {"10", "R' U R2 U R'", "br", "0"},
    {"10", "y2 L U' L' U L U L' U2 L U' L'", "", "0"},
    {"11", "U' R U2 R' d R' U' R", "", "66"},
    {"11", "y' R U2 R2 U' R2 U' R'", "", "57"},
    {"11", "y L' U L U' L' U L U2 L' U L", "", "22"},
    {"11", "F' U L' U2 L U2 F", "", "9"},
    {"11", "y F U R U' R' U R U' R' U F'", "", "2"},
    {"11", "y2 L U2 L' U' l U' l' U2 l U l'", "", "2"},
    {"11", "y' R' U R U' R' U R U R' U2 R", "", "1"},
    {"12", "R U' R' U R U' R' U2 R U' R'", "", "35"},
    {"12", "R' U2 R2 U R2 U R", "", "32"},
    {"12", "U F' U2 F U' R U R'", "", "28"},
    {"12", "U R U' R' U' R U R' U' R U R'", "", "5"},
    {"12", "y' f R' U R2 U' R2 f'", "", "2"},
    {"12", "y L' U2 L U l' U l U2 l' U' l", "", "1"},
    {"12", "y2 U2 R' U2 R U2 L U L' U2 R' U R", "", "1"},
    {"12", "y' U R' U2 R M U2 R' U R U M'", "", "1"},
    {"12", "R U2 R' U2 R U2 R' U2 R U' R'", "", "0"},
    {"12", "R U R' U R' F R F' R U R'", "", "0"},
    {"12", "R' U2 R2 U R'", "br", "0"},
    {"13", "d R' U R U' R' U' R", "", "105"},
    {"13", "M' U' R U R' U2 R U' r'", "", "16"},
    {"13", "R U' R' U R' F R F' R U' R'", "", "12"},
    {"13", "y U L' U L U' L' U L U L' U L", "", "2"},
    {"13", "y y' R U' R' U2 R U' R' U F' U F", "", "1"},
    {"13", "y2 U L U' L F' L2 U' L U F U L'", "", "1"},
    {"13", "y U L F' L2 U' L U L F L'", "", "0"},
    {"14", "U' R U' R' U R U R'", "", "95"},
    {"14", "R U2 R' U2 R U R' U2 R U' R'", "", "4"},
    {"14", "y M' U L' U' L U2 L' U l", "", "4"},
    {"14", "U' R2 D R' U R D' R2", "", "1"},
    {"14", "U2 R2 U R' U R U2 R2", "", "1"},
    {"14", "y2 U' L U' L' U L U' L' U' L U' L'", "", "1"},
    {"14", "F U' F' R U R'", "fl", "0"},
    {"15", "F' U F U2 R U R'", "", "53"},
    {"15", "R U R' U2 R U' R' U R U' R'", "", "48"},
    {"15", "M U r U' r' U' M'", "", "46"},
    {"15", "R' D' R U' R' D R U R U' R'", "", "37"},
    {"15", "y F U2 R U R' U F'", "", "9"},
    {"15", "R U2 R' U R U R' U R U' R'", "", "5"},
    {"15", "y' R2 F R F' R U R' U2 R", "", "5"},
    {"15", "y' R2 F R F' R U2 R' U R", "", "2"},
    {"15", "y U L' l U' l' U l U l' U L", "", "1"},
    {"15", "U R' F R F' U R U R'", "", "0"},
    {"15", "R U' R U2 R2 U' R2 U' R2", "", "0"},
    {"15", "U' R2 U2 R U' R' U R' U2 R2", "", "0"},
    {"15", "U2 R U R' U' R U2 R' U R U R'", "", "0"},
    {"15", "U' R' U2 R' U R' U' R U2 R", "", "0"},
    {"15", "U' R' U R U' R U R'", "br", "0"},
    {"15", "U R U' R' D R U' R' D'", "fl", "0"},
    {"15", "R F U F' U' R'", "fl", "0"},
    {"16", "R U' R' U d R' U' R", "", "62"},
    {"16", "y L' U' L U2 L' U L U' L' U L", "", "14"},
    {"16", "y F U' R U' R' U2 F'", "", "10"},
    {"16", "y M U' l' U l U M'", "", "9"},
    {"16", "U M' U R U' r' U' R U R'", "", "6"},
    {"16", "U F U R U' R' F' R U R'", "", "4"},
    {"16", "y L' U2 L U' L' U' L U' L' U L", "", "4"},
    {"16", "y2 L2 F' L' F L' U2 L U' L'", "", "4"},
    {"16", "y' U2 R' U' R U R D R' U' R D' R'", "", "4"},
    {"16", "y' F R' F' R U2 R' U' R2 U' R'", "", "3"},
    {"16", "U R F R U R' U' F' R'", "", "0"},
    {"16", "y' U R U2 R U' R U R' U2 R'", "", "0"},
    {"16", "F' R' U' R U F", "br", "0"},
    {"17", "R U2 R' U' R U R'", "", "68"},
    {"17", "y L F' L' F L' U L U' L' U L", "", "7"},
    {"17", "y' R' U2 F R U R' U' F' R", "", "5"},
    {"17", "y L' U2 L U2 l' U L U' L' U' l", "", "3"},
    {"17", "R U R' U' R U2 R' U2 R U R'", "", "2"},
    {"18", "F' U2 F U F' U' F", "", "69"},
    {"18", "y2 U F U R U' R' F' L U L'", "", "7"},
    {"18", "R' F R F' R U' R' U R U' R'", "", "4"},
    {"18", "y2 L U2 F' L' U' L U F L'", "", "3"},
    {"18", "y2 L U2 L' U2 l U' L' U L U l'", "", "2"},
    {"18", "R U R' U' R U R' U' F R' F' R", "", "0"},
    {"19", "U R U2 R' U R U' R'", "", "73"},
    {"19", "y U L' U L2 F' L' F L' U L", "", "7"},
    {"19", "y' U R' F' U2 F R U R' U' R", "", "7"},
    {"19", "R U' R' U R U' R' U R U R'", "", "3"},
    {"19", "U R U2 R2 F R F'", "", "3"},
    {"19", "y' U2 f R2 U R2 U' R f'", "", "3"},
    {"20", "U' F' U2 F U' F' U F", "", "72"},
    {"20", "U' R U' R2 F R F' R U' R'", "", "20"},
    {"20", "y2 U' L F U2 F' L' U' L U L'", "", "4"},
    {"20", "y' R' U R U' R' U R U' R' U' R", "", "2"},
    {"20", "y2 U' L U L' U l U' l' U2 l U l'", "", "2"},
    {"20", "y U' L' U2 L2 F' L' F", "", "0"},
    {"21", "U2 R U R' U R U' R'", "", "37"},
    {"21", "R U' R' U2 R U R'", "", "24"},
    {"21", "y l' U l U2 l' U' l", "", "17"},
    {"21", "R B U2 B' R'", "", "15"},
    {"21", "y2 l U' L' U2 L U l'", "", "1"},
    {"21", "y' U2 R' U' R S R f' U' F", "", "1"},
    {"21", "y' U f U R U' R f'", "", "1"},
    {"22", "r U' r' U2 r U r'", "", "37"},
    {"22", "y' U2 R' U' R U' R' U R", "", "33"},
    {"22", "F' L' U2 L F", "", "26"},
    {"22", "y' R' U R U2 R' U' R", "", "14"},
    {"22", "y U' L' U L U2 L' U L U' L' U L", "", "2"},
    {"23", "U R U' R' U' R U' R' U R U' R'", "", "45"},
    {"23", "R U R' U' U' R U R' U' R U R'", "", "24"},
    {"23", "y F' U' L' U L F L' U L", "", "14"},
    {"23", "U2 R2 U2 R' U' R U' R2", "", "13"},
    {"23", "y' U R' F R' F' R2 U' R' U R", "", "11"},
    {"23", "y U L' U' L2 F' L' F L' U L", "", "8"},
    {"23", "R U' R2 D' R U2 R' D R", "", "7"},
    {"23", "y F U' R U R' U R U2 R' F'", "", "5"},
    {"23", "y' U2 l' U' L U' L' U2 B' l", "", "5"},
    {"23", "y U' F R U' R' F' L' U' L", "", "4"},
    {"23", "y' R' F' U' F U2 R U' R' U' R", "", "4"},
    {"23", "y2 L' U' L U' L' U2 L2 U2 L'", "", "3"},
    {"23", "y' U R' U' F' U F R U' R' U R", "", "3"},
    {"23", "U F R' F' R U R U R'", "", "0"},
    {"23", "R U' R' U' R U R' U2 R U R'", "", "0"},
    {"23", "R2 U R' U R U2 R' U' R'", "", "0"},
    {"24", "F U R U' R' F' R U' R'", "", "37"},
    {"24", "y' R' U' R U U R' U' R U R' U' R", "", "16"},
    {"24", "y U' L' U L U L' U L U' L' U L", "", "13"},
    {"24", "y2 U2 r U R' U R U2 B r'", "", "11"},
    {"24", "U' R U R2 F R F' R U' R'", "", "7"},
    {"24", "y U' F' r U r' U' L' U' L", "", "5"},
    {"24", "y' U2 R2 U2 R U R' U R2", "", "4"},
    {"24", "U F' L' U L F R U R'", "", "4"},
    {"24", "y L' U L U L' U' L U2 L' U' L", "", "4"},
    {"24", "y' R U R' U R U2 R2 U2 R", "", "3"},
    {"24", "y F U' R U2 R' U R U2 R' F'", "", "2"},
    {"24", "y2 U' L F' L F L2 U L U' L'", "", "2"},
    {"24", "y2 U2 F U R U' R' F' U2 L U' L'", "", "1"},
    {"24", "R U R' d R' U R U' R' U R", "", "0"},
    {"24", "R U R' U R U R' U' F R' F' R", "", "0"},
    {"24", "R U R' U R U2 R' F' U2 F", "", "0"},
    {"25", "U' R' F R F' R U R'", "", "30"},
    {"25", "U' F' U F U R U' R'", "", "17"},
    {"25", "R' F' R U R U' R' F", "", "17"},
    {"25", "y U' L' U L F' r U r'", "", "14"},
    {"25", "y2 R D' R' U' R D R' L U L'", "", "11"},
    {"25", "y' U' R' U M U' R U M'", "", "11"},
    {"25", "R U' R' U' R U' R' U R U R'", "", "10"},
    {"25", "y2 U' f' L' f U L U L'", "", "5"},
    {"25", "y' R' S' R U' R' S R", "", "5"},
    {"25", "R' U' R' U' R' U R U R", "", "1"},
    {"25", "U' F' R U R' U' R' F R", "", "0"},
    {"25", "U R' U' R' U' R2 U R U R", "", "0"},
    {"25", "U2 R' U' R' U' R U R U R", "", "0"},
    {"25", "R2 U' R' U R2", "br", "0"},
    {"25", "U D' R U' R' D", "br", "0"},
    {"26", "U R U' R' F R' F' R", "", "24"},
    {"26", "R S' R' U R S R'", "", "18"},
    {"26", "U R U R' U' y L' U' L", "", "17"},
    {"26", "y r U r' U' r' F r F'", "", "14"},
    {"26", "y' R' U R U R' U R U' R' U' R", "", "9"},
    {"26", "y' R U R U R U' R' U' R'", "", "7"},
    {"26", "d R B' R' B R' U' R", "", "6"},
    {"26", "y U F L' U' L U L F' L'", "", "5"},
    {"26", "U R U' R' U' F' U F", "", "4"},
    {"26", "y2 F R2 u R u' R2 F'", "", "3"},
    {"26", "y2 U' R u R' U R U' u' R'", "", "3"},
    {"26", "y2 R E' R' U R E R'", "", "3"},
    {"26", "y' U' R S2 R' U' R S2 R'", "", "3"},
    {"26", "y' U' R U R U R2 U' R' U' R'", "", "0"},
    {"26", "y' U2 R U R U R' U' R' U' R'", "", "0"},
    {"26", "r U r' U2 r U r' U2 r U' r'", "", "0"},
    {"27", "R U' R' U R U' R'", "", "47"},
    {"27", "y L' U' L U F' r U r'", "", "13"},
    {"27", "y' R' U2 R' F R F' R", "", "7"},
    {"27", "y' R' U' R U r' U' R U M'", "", "4"},
    {"27", "F' U' F U2 R U' R'", "", "3"},
    {"27", "y' f R' f' r' U' R U M'", "", "1"},
    {"27", "y U' F R U2 R' U F'", "", "1"},
    {"27", "R U' R2 F R F'", "", "0"},
    {"28", "F' U F U' F' U F", "", "43"},
    {"28", "R U R' U' F R' F' R", "", "23"},
    {"28", "y2 L U2 L F' L' F L'", "", "4"},
    {"28", "y L' U L2 F' L' F", "", "2"},
    {"28", "y2 L U L' U' l U L' U' M'", "", "2"},
    {"28", "y2 L U2 L' U f' L f", "", "2"},
    {"28", "R U R' d R' U2 R", "", "0"},
    {"29", "y' R' U' R U R' U' R", "", "63"},
    {"29", "R' F R F' U R U' R'", "", "22"},
    {"29", "R' F R F' R' F R F'", "", "6"},
    {"29", "y U L' U2 L U2 L' U' L", "", "4"},
    {"29", "U2 R U' R' y' R' U' R", "", "3"},
    {"29", "y U F' L F L2 U' L", "", "1"},
    {"30", "R U R' U' R U R'", "", "55"},
    {"30", "y L F' L' F U' L' U L", "", "6"},
    {"30", "U2 F' U F R U R'", "", "3"},
    {"30", "U' R U2 R' U2 R U R'", "", "3"},
    {"30", "U' F R' F' R2 U R'", "", "2"},
    {"30", "y U' F U' R U2 R' F'", "", "2"},
    {"31", "U' R' F R F' R U' R'", "", "29"},
    {"31", "R U' R' d R' U R", "", "11"},
    {"31", "y U L F' L' F L' U L", "", "10"},
    {"31", "F' U F R U2 R'", "", "9"},
    {"31", "y' R' U R' F R F' R", "", "7"},
    {"31", "y2 L U' L F' L' F L'", "", "6"},
    {"31", "y L' U L U' y L U' L'", "", "3"},
    {"31", "y2 L U2 L' U' l U L' U' M'", "", "1"},
    {"31", "R U2 R' U' F R' F' R", "", "0"},
    {"32", "R U R' U' R U R' U' R U R'", "", "80"},
    {"32", "y U' L' U L U' L' U L U' L' U L", "", "33"},
    {"32", "R2 U R2 U R2 U2 R2", "", "24"},
    {"32", "U' F R' F' R U' R U R'", "", "9"},
    {"32", "y U2 F U' R U R' U F'", "", "4"},
    {"32", "y U L' U L U' L' U2 L U L' U' L", "", "2"},
    {"32", "U' R U R' U' R U R' U R U' R'", "", "0"},
    {"32", "y2 U' L U' L' U L U2 L' U' L U L'", "", "0"},
    {"32", "y' R2 U' R2 U' R2 U2 R2", "", "0"},
    {"33", "U' R U' R' U2 R U' R'", "", "41"},
    {"33", "y R' D R U' R' D' R", "", "10"},
    {"33", "y U' L D L' U L D' L'", "", "9"},
    {"33", "U' R U' R' U' R U2 R'", "", "4"},
    {"33", "y U L' U2 L U' L' U' L", "", "4"},
    {"33", "R U R' U' R U' R' U R U' R'", "", "3"},
    {"33", "y U' L' U' L U2 L' U' L", "", "2"},
    {"33", "u R U' R' u'", "br", "0"},
    {"33", "E F' U' F u", "fl", "0"},
    {"33", "y2 D' R D R' U R D' R' D", "", "0"},
    {"33", "y' U R D R' U' R D' R'", "", "0"},
    {"34", "U R U R' U2 R U R'", "", "33"},
    {"34", "U' R U2 R' U R U R'", "", "22"},
    {"34", "U F' U F U2 F' U F", "", "12"},
    {"34", "U R' D' R U' R' D R", "", "12"},
    {"34", "y U L' U L U L' U2 L", "", "6"},
    {"34", "y2 U2 R D' R' U' R D R'", "", "1"},
    {"34", "U y F U2 R U2 R' F'", "", "0"},
    {"34", "E' R U R' E", "br", "0"},
    {"34", "u' F' U F u", "fl", "0"},
    {"34", "y L' U' L U L' U L U' L' U L", "", "0"},
    {"34", "y' U R2 F R F' R U' R' U R", "", "0"},
    {"35", "U' R U R' U F' U' F", "", "62"},
    {"35", "U2 R U R' F R' F' R", "", "12"},
    {"35", "y U2 F U F' U' L' U L", "", "11"},
    {"35", "y U2 L F' L' F U2 L' U' L", "", "4"},
    {"35", "y U' F R' F R F' U F'", "", "3"},
    {"35", "y' R' F R' F' R U R U' R' U' R", "", "3"},
    {"35", "y2 U2 L U L' U' L F U F' L'", "", "2"},
    {"35", "y2 L U L' y R' U' R U R' U' R", "", "1"},
    {"35", "y2 U2 L U M U L' U' M'", "", "1"},
    {"35", "y' U' R' F' U F U' R U R' U' R", "", "1"},
    {"35", "U2 R U' R' U' F' U' F", "", "0"},
    {"36", "U F' U' F U' R U R'", "", "49"},
    {"36", "U2 R' F R F' U2 R U R'", "", "13"},
    {"36", "R U R' U R U R' U' F' U' F", "", "3"},
    {"36", "R2 u R U R' U' u' R' U R'", "", "3"},
    {"36", "y U2 L' U L U F U F'", "", "3"},
    {"36", "y2 U2 f' L' f U L U' L'", "", "3"},
    {"36", "y' U2 R' U' R U R' F' U' F R", "", "3"},
    {"36", "y U2 L' U' L F' r U r'", "", "2"},
    {"36", "y2 L F' L F L' U' L' U L U L'", "", "2"},
    {"37", "R2 U2 F R2 F' U2 R' U R'", "", "37"},
    {"37", "R' F R F' R U' R' U R U' R' U2 R U' R'", "", "19"},
    {"37", "R U2 R' U R U2 R' U F' U' F", "", "11"},
    {"37", "R U R' U2 R U2 R' d R' U' R", "", "7"},
    {"37", "y L' U2 L U' L' U2 L U' F U F'", "", "5"},
    {"37", "y2 L U' L' l' U2 L2 U L2 U l", "", "5"},
    {"37", "y' R' U R r U2 R2 U' R2 U' r'", "", "5"},
    {"37", "y L2 U2 F' L2 F U2 L U' L", "", "4"},
    {"37", "y L' U' L U2 L' U2 L U' y' R U R'", "", "3"},
    {"37", "y R' F R L' U' L U' R' F R L' U' L", "", "1"},
    {"37", "y2 f' L f U' L U2 L' U2 L U' L'", "", "1"},
    {"37", "y2 L' f U f' L' U2 L2 U L2 U L", "", "1"},
    {"37", "y' R' U R f R U R2 U' R f'", "", "1"},
    {"37", "R U' R' d R' U2 R U2 R' U R", "", "0"},
    {"37", "R U R' U' R U2 R' d R' U' R U' R' U R", "", "0"},
    {"37", "R U' R2 U2 R U' F' U F", "br", "0"},
    {"37", "R U' R2 U2 R F R' F' R", "br", "0"},
    {"37", "R U' R2 U2 R U2 F' U2 F", "br", "0"},
    {"38", "R U' R' U' R U R' U2 R U' R'", "", "23"},
    {"38", "R U R' U' R U2 R' U' R U R'", "", "17"},
    {"38", "R2 U2 R' U' R U' R' U2 R'", "", "9"},
    {"38", "y L' U L U' L' U2 L U' L' U L", "", "7"},
    {"38", "R U' R' U' R U R' U' R U2 R'", "", "5"},
    {"38", "y' R' U' R U2 R' U R U' R' U' R", "", "4"},
    {"38", "y F R U2 R' U' R U R' U2 F'", "", "1"},
    {"38", "y L' U2 L' U' L U' L' U2 L2", "", "0"},
    {"38", "y F U' R U2 R' U' R U2 R' F'", "", "0"},
    {"39", "R U' R' U R U2 R' U R U' R'", "", "31"},
    {"39", "R U R' U2 R U' R' U R U R'", "", "12"},
    {"39", "y L' U' L U L' U2 L U L' U' L", "", "9"},
    {"39", "R U2 R U R' U R U2 R2", "", "6"},
    {"39", "R U2 R' U R U' R' U R U R'", "", "4"},
    {"39", "y L' U L U L' U' L U2 L' U L", "", "4"},
    {"39", "y F' L F L2 U2 L U L' U' L", "", "1"},
    {"39", "y F U2 R U' R' U R U2 R' F'", "", "1"},
    {"39", "y' f R2 U R' U' F R' f' U F'", "", "1"},
    {"39", "y' R2 U2 R U R' U R U2 R", "", "0"},
    {"40", "r U' r' U2 r U r' R U R'", "", "33"},
    {"40", "F' L' U2 L F R U R'", "", "17"},
    {"40", "y L' U L l' U l U2 l' U' l", "", "11"},
    {"40", "R U' R' F R U R' U' F' R U' R'", "", "10"},
    {"40", "R U' R' U' R U' R' d R' U' R", "", "9"},
    {"40", "y L' U L F R U2 R' F'", "", "9"},
    {"40", "y L' U L U2 y L U L' U L U' L'", "", "2"},
    {"40", "y2 f' L f U2 L U L' U2 L U2 L'", "", "1"},
    {"40", "y' R2 F' U' F U R U' R", "", "1"},
    {"40", "R U' R' d R' U' R U' R' U' R", "", "0"},
    {"40", "R F U R U' R' F' U' R'", "", "0"},
    {"40", "R U' R2 U' R y' R' U' R y", "br", "0"},
    {"41", "R U' R' r U' r' U2 r U r'", "", "24"},
    {"41", "y l' U l U2 l' U' l L' U' L", "", "13"},
    {"41", "R U' R' F' L' U2 L F", "", "9"},
    {"41", "R U R' U' y M U' R' F R U M'", "", "7"},
    {"41", "R U R' U' R U' R' U2 F' U' F", "", "6"},
    {"41", "R U' R' U d R' U' R U' R' U R", "", "2"},
    {"41", "y F R U2 R' F' L' U' L", "", "2"},
    {"41", "y2 f' L f U' L U L' U L U L'", "", "2"},
    {"41", "y L' U L U L' U L U' y' R U R'", "", "1"},
    {"41", "y2 L2 F U F' U' L' U L'", "", "1"},
    {"41", "y' R' U R' U' F' U F R2", "", "1"},
    {"41", "y' f R' f' U2 R' U' R U2 R' U2 R", "", "1"},
    {"41", "R U F R U R' U' F' R'", "", "0"},
    {"41", "y' R' U R2 U R' y R U R'", "fl", "0"},
    {"a1", "y' S R' S'", "", "58"},
    {"a1", "y' R U' R' y R U' R'", "", "22"},
    {"a1", "L' U' L U R U' R'", "", "11"},
    {"a1", "y' R U' R' U R2 F R F' R", "", "3"},
    {"a1", "y2 y R U' R2 F' U' F R", "", "0"},
    {"a2", "y2 L F' U2 F L'", "", "20"},
    {"a2", "y2 R U' R' L U2 L'", "", "18"},
    {"a2", "y' L' U' L y R U' R'", "", "7"},
    {"a2", "y2 F' L U2 L' F", "", "5"},
    {"a2", "y2 L R U' R' U2 L'", "", "2"},
    {"a2", "L U' L' U' R U' R'", "", "1"},
    {"a3", "y R U' R' U F' r U r'", "", "10"},
    {"a3", "y2 L' U' L2 U2 L'", "", "8"},
    {"a3", "y y' R' U2 R2 U' R'", "", "4"},
    {"a3", "y R U' F R' U R F' R'", "", "2"},
    {"a3", "y' y R' U' R U' R U' R'", "", "2"},
    {"a3", "y' L U' L' y' U2 L U' L'", "", "1"},
    {"a4", "y' F R' F' R U R' U2 R", "", "11"},
    {"a4", "y' R U R' U2 f R f'", "", "8"},
    {"a4", "y' y U2 L' U L U' R U R'", "", "3"},
    {"a4", "y' R U R' U2 S U' R' U R S'", "", "1"},
    {"a4", "U' F U' R U R2 F' R", "", "1"},
    {"a5", "y2 R U R' U L U L'", "", "12"},
    {"a5", "y' r U' r' F R' U2 R", "", "5"},
    {"a5", "y2 U' R U2 M' B r'", "", "2"},
    {"a5", "y2 y U2 L' U L U2 f R f'", "", "2"},
    {"a6", "y R U R' F U F'", "", "11"},
    {"a6", "y2 U2 L' U L U L U L'", "", "5"},
    {"a6", "y F R' F' R L' U L", "", "4"},
    {"a6", "y2 L' U L U L' U' L2 U L'", "", "0"},
    {"a7", "y' U' F' U' f R S'", "", "10"},
    {"a7", "U' L' U L R U' R'", "", "7"},
    {"a7", "y' y U' L' U' L R U R'", "", "6"},
    {"a7", "y' R U' R' U' R U' R' f R f'", "", "1"},
    {"a7", "y' F R' F' U R U' R' U' R", "", "0"},
    {"a8", "y2 U' F' U2 L U L' F", "", "6"},
    {"a8", "y2 y' U R' U2 R F U F'", "", "4"},
    {"a8", "y2 U' R' F R F' U2 L U L'", "", "3"},
    {"a8", "y2 R U' R' U L U' L' U L U L'", "", "2"},
    {"a8", "R D' R' U R U D R'", "", "2"},
    {"a8", "y' F U2 R U R2 U' R F'", "", "1"},
    {"a8", "y' U' L' U L y' L U2 L'", "", "1"},
    {"a8", "y' U' L' U' L d' R U R'", "", "1"},
    {"a8", "y U' R' U R d' L U' L'", "", "1"},
    {"a8", "y U' R' U R U2 F' r U r'", "", "1"},
    {"a9", "y y' U R' U2 R U' R U R'", "", "8"},
    {"a9", "y U R' F' R F' R' F R", "", "2"},
    {"a9", "y U R' F R F' R U' R' U' L' U' L", "", "1"},
    {"a9", "y2 U' L' U L U2 L U' L'", "", "0"},
    {"a9", "y2 y' U' R' F R F' U F U F'", "", "0"},
    {"a9", "y2 U' L' U L U' L U2 L'", "", "0"},
    {"a10", "y' U' R U R2 U' R", "", "18"},
    {"a10", "y' y L' U L R U' R' U R U R'", "", "3"},
    {"a10", "y' y U' L F2 L' F2", "", "1"},
    {"a10", "y' U2 R U R' U2 R' U R", "", "0"},
    {"a11", "y2 U' R U' R' L U' L'", "", "11"},
    {"a11", "y' U' L' U' L R' U' R", "", "2"},
    {"a11", "y2 U' R U' R' U L U2 L'", "", "0"},
    {"a11", "y' U L' U2 L U R' U' R", "", "0"},
    {"a11", "y' U' L' U' R' U' R L", "", "0"},
    {"a12", "y U2 R U R' L' U L", "", "10"},
    {"a12", "y2 U' L U L2 U' L2 U L'", "", "3"},
    {"a12", "y U' R U R2 F R U F'", "", "1"},
    {"a12", "y U' R U R' U2 L' U' L", "", "1"},
    {"a12", "y' U2 L U' L' R' U' R", "", "1"},
    {"a12", "U2 R U2 R2 U' R2 U R'", "", "1"},
    {"a12", "y F U' F' U2 R U' R' L' U' L", "", "0"},
    {"a12", "y2 L' U' L U' L' U L U' L U2 L'", "", "0"},
    {"a12", "y2 U' L S U2 S' L'", "", "0"},
    {"a12", "y2 y' U' R U R2 F R U R' F' R", "", "0"},
    {"a13", "y' U R' F R F' R' U' R", "", "11"},
    {"a13", "U' L F' L2 U L U2 F", "", "5"},
    {"a13", "y' U R U' R' U S R' S'", "", "4"},
    {"a13", "y' U R U' R' U R U' R' f R' f'", "", "1"},
    {"a13", "U' L F' L' F U2 R U R'", "", "1"},
    {"a13", "y2 U' R B' R2 U R U2 R B R'", "", "0"},
    {"a14", "y2 U2 r2 U B2 U' r2", "", "9"},
    {"a14", "y2 U' F R' F' R U L U L'", "", "8"},
    {"a14", "y' U2 R2 D' F2 D R2", "", "4"},
    {"a14", "y2 U R U' R' U R U' R' L U2 L'", "", "2"},
    {"a14", "y' U' L' U L U' L' U L R' U2 R", "", "2"},
    {"a14", "y' L' U' L U L' U' L U' R' U' R", "", "2"},
    {"a14", "L U L' U' L U L' U R U R'", "", "1"},
    {"a15", "y U R' F R2 U' R' U2 F'", "", "11"},
    {"a15", "y2 U' L F' L' F L U L'", "", "5"},
    {"a15", "y2 U' L' U L U' S' L S", "", "3"},
    {"a15", "y U R' F R F' U2 L' U' L", "", "2"},
    {"a15", "y R U R' U' R U R' F U F'", "", "1"},
    {"a15", "y U R' F R2 U' R' U2 R' F' R", "", "1"},
    {"a15", "y' U' R' U' R2 u R u' R'", "", "0"},
    {"a16", "y' U' F' R' U' R F", "", "8"},
    {"a16", "y' d' R' u' R' u R", "", "7"},
    {"a16", "y' U' R U' R' U f R' f'", "", "5"},
    {"a16", "y' U' R U' R' B' R B R'", "", "2"},
    {"a16", "y' U' R U' R' r' U' R U M'", "", "1"},
    {"a16", "y2 U' R' U' R y U R' U' R", "", "1"},
    {"a16", "U L' U2 L y U2 L' U' L", "", "0"},
    {"a17", "y2 U2 R B' U' B R'", "", "11"},
    {"a17", "y2 U' R U R' U' f' L' f", "", "2"},
    {"a17", "y' U2 F U' F' U R' U' R", "", "2"},
    {"a17", "U2 R d' R' U R F'", "", "1"},
    {"a17", "y' U2 L F' L' F R' U' R", "", "0"},
    {"a17", "y' U2 F U F' R' U2 R", "", "0"},
    {"a18", "y U F' U2 F L' U' L", "", "6"},
    {"a18", "y U' R U' R' U2 F' L F L'", "", "4"},
    {"a18", "y U r' F' D' F D r", "", "2"},
    {"a18", "y U' R U' R' F U2 F'", "", "2"},
    {"a18", "y2 U' L' U' L d' R' U' R", "", "2"},
    {"a18", "y' U' L U' L' d' R U' R'", "", "1"},
    {"a18", "y' U' L U' L' R2 F R F' R", "", "1"},
    {"a18", "y2 F U R U' R' U' R U' R' L U2 L' F'", "", "0"},
    {"a18", "y U' F' U2 L' U' L2 F L'", "", "0"},
    {"a19", "y' U' R U2 R' f R f'", "", "6"},
    {"a19", "y' R' F R F' R U2 R2 U R", "", "2"},
    {"a19", "U' R U F U' F' R'", "", "2"},
    {"a19", "y' U R U R' U y R U R'", "", "1"},
    {"a19", "U F U2 R U R2 F' R", "", "1"},
    {"a19", "y2 U R' U R d L' U L", "", "1"},
    {"a19", "U L' U L F' U2 F", "", "0"},
    {"a20", "y2 U R U R' L U L'", "", "6"},
    {"a20", "y2 U' R U2 R' U' L U L'", "", "2"},
    {"a20", "y' U L' U L R' U R", "", "0"},
    {"a21", "y U R F U F' R'", "", "9"},
    {"a21", "y U L u L u' L'", "", "7"},
    {"a21", "y U R U R' U' F U F'", "", "1"},
    {"a21", "y2 U L' U L R B L' B' M' x'", "", "0"},
    {"a21", "y2 U L' U L B L' B' L", "", "0"},
    {"a21", "y' U' L U2 L' y' U2 L U L'", "", "0"},
    {"a21", "U R' U R U' F' U F", "", "0"},
    {"a22", "y' R U' R2 U R", "", "7"},
    {"a22", "y' y M F2 M'", "", "6"},
    {"a22", "L' U L U2 R U' R'", "", "1"},
    {"a22", "y' R U2 R2 U2 R", "", "0"},
    {"a22", "F2 R' F2 R", "", "0"},
    {"a23", "y2 L F' U F L'", "", "7"},
    {"a23", "y' R' F U' F' R", "", "6"},
    {"a23", "y2 l F' U L' U' M'", "", "1"},
    {"a23", "y' r' F U' R U M'", "", "1"},
    {"a23", "y2 R U2 R' y' L' U L", "", "0"},
    {"a23", "y' L' U L y' U L U' L'", "", "0"},
    {"a23", "R u R' U R u' R'", "", "0"},
    {"a23", "L U' L' d' L' U L", "", "0"},
    {"a23", "y L' u' L U' L' u L", "", "0"},
    {"a24", "y R U' R' U2 L' U L", "", "6"},
    {"a24", "y M F2 M'", "", "4"},
    {"a24", "y F2 r U2 r'", "", "2"},
    {"a24", "y U F r U2 r' F'", "", "2"},
    {"a24", "y2 L' U L2 U' L'", "", "2"},
    {"a24", "R' U2 R2 U2 R'", "", "0"},
    {"a25", "y' R' F R F' U R' U2 R", "", "6"},
    {"a25", "y' F' U R' U2 R F", "", "6"},
    {"a25", "y' R U' R' U' f R f'", "", "3"},
    {"a25", "y' R U R' U' B U' B'", "", "2"},
    {"a25", "R' F R2 U' R2 F' R", "", "1"},
    {"a25", "R' F R2 U R' F' R U2 R'", "", "1"},
    {"a25", "F R U' R2 F' R", "", "1"},
    {"a25", "y2 R' U R d' R' U' R", "", "1"},
    {"a25", "L F' L' F R U' R'", "", "0"},
    {"a25", "y L U L' F U2 F'", "", "0"},
    {"a25", "y2 R' U' R l U L' U' M'", "", "0"},
    {"a26", "L R U2 R' L'", "", "5"},
    {"a26", "y2 R U' R' U2 L U L'", "", "0"},
    {"a26", "y2 R U2 R' U L U' L'", "", "0"},
    {"a26", "y2 R U R' U' L U2 L'", "", "0"},
    {"a26", "y' L' U' L U R' U2 R", "", "0"},
    {"a26", "y L' R' U2 R L", "", "0"},
    {"a26", "y R' U' R U2 L' U L", "", "0"},
    {"a27", "y R' F R2 U R' F'", "", "10"},
    {"a27", "y R U2 R' F U' F'", "", "3"},
    {"a27", "y R' F R2 U R2 F' R", "", "3"},
    {"a27", "y R U R' F' r U r'", "", "2"},
    {"a27", "y2 L' U L U f' L' f", "", "1"},
    {"a27", "y2 L' U' L U y' L' U L", "", "0"},
    {"a27", "y2 L' U' L y R' U2 R", "", "0"},
    {"a27", "y2 F U' L U2 L' F'", "", "0"},
    {"a27", "y' L U L' U y R U' R'", "", "0"},
    {"a27", "y' L U' L' U f R f'", "", "0"},
    {"a27", "F' R' U2 R U' F", "", "0"},
    {"a28", "y' R U' R2 U' R U' R' U' R", "", "1"},
    {"a28", "y' R U' R' U' R U' R2 U' R", "", "1"},
    {"a28", "y' r U' r' U2 r U r' d' R U R'", "", "0"},
    {"a28", "y' u R U R' u' R U' R2 U R", "", "0"},
    {"a28", "L' U L U R U R' U R U' R'", "", "0"},
    {"a28", "F U' R U R' U R U' R' F' R U' R'", "", "0"},
    {"a29", "y2 F' U L' U L U' L U L' F", "", "2"},
    {"a29", "y2 R U' R' U' R U' R' d' R' U' R", "", "2"},
    {"a29", "y2 R U' R' y R' U R U R' U' R", "", "1"},
    {"a29", "y2 F' U F L U L' U L U' L'", "", "1"},
    {"a29", "y' L' U L B L' B L B2", "", "1"},
    {"a29", "f' L f R U R2 F R F'", "", "1"},
    {"a29", "y' L' U' L U L' U L f R f'", "", "0"},
    {"a30", "y R U' R' U' L' U L U L' U' L", "", "1"},
    {"a30", "y R U' R' U' R U' R' U2 L' U' L", "", "1"},
    {"a30", "y M F M' U L' U L U' L' U L", "", "0"},
    {"a30", "y r U' r' U2 r U r' d R U R'", "", "0"},
    {"a30", "y2 L' U L U' L U L' U L U' L'", "", "0"},
    {"a31", "y' R U R' U' R U' R' U R' U' R", "", "5"},
    {"a31", "y' R U' R' U R' U' R U' R' U R", "", "1"},
    {"a31", "R' F R U' R' F' R2 U R' U R U R'", "", "0"},
    {"a31", "L' U L U L' U L U2 R U R'", "", "0"},
    {"a31", "L' U L U R U' R' U' R U R'", "", "0"},
    {"a32", "y2 R U' R' y R' U' R U' R' U R", "", "2"},
    {"a32", "y2 R U R' U' R U' R' f' L' f", "", "1"},
    {"a32", "y2 R U' R' U f' U' L' U L' f", "", "1"},
    {"a32", "y2 R U R' F R U R' U' F' L U2 L'", "", "0"},
    {"a32", "y' F U' R U' R' U R' U' R F'", "", "0"},
    {"a32", "y' L' U L y' L U' L' U' L U L'", "", "0"},
    {"a32", "y' L' U L U L' U L U f R f'", "", "0"},
    {"a32", "L U' L' F' L F' L' F2", "", "0"},
    {"a32", "y R' U R U' L' U L d' L U L'", "", "0"},
    {"a33", "y R U' R' U' L' U' L U' L' U L", "", "2"},
    {"a33", "y R U R' U' R U' R' U' L' U' L", "", "1"},
    {"a33", "y y' R' U R2 U R' U R U R'", "", "1"},
    {"a33", "y R U' R' F' r' F' r2 U r'", "", "0"},
    {"a33", "y2 L' U L U L' U L2 U L'", "", "0"},
    {"a34", "y' F' R' U R U' R' U' R F", "", "4"},
    {"a34", "y' R U' R' U' R U R' U f R' f'", "", "1"},
    {"a34", "y' R U' R' U' R U R' r' U' R U M'", "", "1"},
    {"a34", "y' R U' R2 f' U' f R", "", "1"},
    {"a34", "L' U' L U y' R' U R U' R' U' R", "", "0"},
    {"a34", "L' U L y' U2 R' U2 R U' R' U R", "", "0"},
    {"a34", "F U' R U' R' F' R U' R' U' R U R'", "", "0"},
    {"a34", "y L U L' y U2 L U2 L' U' L U L'", "", "0"},
    {"a34", "y L U' L' U' L U L' F' L F L'", "", "0"},
    {"a35", "y2 R U' R' U' R U R' L U' L'", "", "4"},
    {"a35", "y2 R U R' U' R U2 R' U L U L'", "", "0"},
    {"a35", "y2 R U2 R' L U2 L' U' L U L'", "", "0"},
    {"a35", "y' L' U' L R' U R U' R' U' R", "", "0"},
    {"a35", "y' L' U L U R' U2 R U' R' U R", "", "0"},
    {"a35", "L U L' U R U2 R' U' R U R'", "", "0"},
    {"a36", "y R' F R2 U' R' U' R U R' U2 F'", "", "2"},
    {"a36", "y R' F R F' U2 L' U L U' L' U' L", "", "1"},
    {"a36", "y R U' R' U' R U R' U' F U' F'", "", "1"},
    {"a36", "y R U' R' U' R U R' F U2 F'", "", "1"},
    {"a36", "y2 L' U' L d' R' U R U' R' U' R", "", "0"},
    {"a36", "y2 L' U L y R' U2 R U' R' U R", "", "0"},
    {"a36", "y2 F U L U2 L' U' L U L' F'", "", "0"},
    {"a36", "y' L U L' y' L U2 L' U' L U L'", "", "0"},
    {"a36", "y' L U' L' R' F R U R' U' F' R", "", "0"},
    {"a36", "R' U2 R y' R' U R U' R' U' R", "", "0"},
    {"a36", "R' U R U' R' U2 L F' R2 U' R2 U F L' R", "", "0"},
    {"a36", "R' U R2 U' R2 F R F' R U' R'", "", "0"},
    {"a37", "y' R U R' U2 R U' R' f R f'", "", "2"},
    {"a37", "y' F' U' R' U2 R U R' U' R F", "", "1"},
    {"a37", "y' R U' R' y R U2 R' U R U' R'", "", "0"},
    {"a37", "y' R U' R' U R U2 R' y R U' R'", "", "0"},
    {"a37", "L' U' L y' R' U2 R U R' U' R", "", "0"},
    {"a37", "L' U L U L' U' L y' R' U2 R", "", "0"},
    {"a37", "F U2 R U' R' F' U R U R'", "", "0"},
    {"a37", "y L U L' y U L U' L' U L U L'", "", "0"},
    {"a38", "y2 R U' R' U R U2 R' L U2 L'", "", "2"},
    {"a38", "y2 R U' R' U' L U2 L' U L U' L'", "", "1"},
    {"a38", "y2 R U R' L U' L' U L U L'", "", "1"},
    {"a38", "y2 R U' R' U R U2 R' U' L U' L'", "", "0"},
    {"a38", "y' L' U L U L' U' L R' U R", "", "0"},
    {"a38", "y' F U2 R U' R' f R f' F'", "", "0"},
    {"a38", "y' L' U2 L R' U2 R U R' U' R", "", "0"},
    {"a38", "y R' U' R U' L' U2 L U L' U' L", "", "0"},
    {"a39", "y R U R' d' L U' L' U L U L'", "", "2"},
    {"a39", "y R U R' L' U L U' L F' L' F L' U L", "", "0"},
    {"a39", "y R U' R' y' U2 R U2 R' U R U' R'", "", "0"},
    {"a39", "y2 F L U' L' U L U L' F'", "", "0"},
    {"a39", "y2 L' U L l U' L' U L U l'", "", "0"},
    {"a39", "y2 L' U' L y U2 R' U2 R U R' U' R", "", "0"},
    {"a39", "y' f' L f U2 R' U' R U R' U2 R", "", "0"},
    {"a40", "y' R U' R2 U2 R U R' U2 R", "", "2"},
    {"a40", "y' R U' R2 U2 R U2 R' U R", "", "1"},
    {"a40", "F2 R' F2 D' R U' R' D R", "", "1"},
    {"a40", "y' F R' F' R U' R U2 R2 U' R", "", "0"},
    {"a40", "y' R' F R F' R U' R' U R U' R' U y R U' R'", "", "0"},
    {"a40", "L' U' L R U' R' U R U' R' U2 R U' R'", "", "0"},
    {"a40", "L' U' L R' U2 R2 U R2 U R", "", "0"},
    {"a40", "L' U L U2 R U2 R' U2 R U' R'", "", "0"},
    {"a41", "y2 R U' R' d R' U2 R L' U L", "", "6"},
    {"a41", "y2 F' U2 L' U2 L2 U L' F", "", "2"},
    {"a41", "y2 R U' R' y U' R' U2 R U R' U2 R", "", "1"},
    {"a41", "L U L' R U2 R' y' U R' U' R", "", "1"},
    {"a41", "y' F U2 R U2 R2 U' R F'", "", "0"},
    {"a41", "y' L' U L U2 r U2 R2 U' R2 U' r'", "", "0"},
    {"a41", "y R' U R y' U R U2 R' U2 R U' R'", "", "0"},
    {"a41", "y R' U' R L' U2 L y U' L U L'", "", "0"},
    {"a42", "y R U' R' U2 L' U2 L U2 L' U L", "", "2"},
    {"a42", "y y' R' U R2 U2 R' U2 R U' R'", "", "1"},
    {"a42", "y y' R' U R2 U2 R' U' R U2 R'", "", "1"},
    {"a42", "y R' F R F' R U' R' U R U' R' d' L U' L'", "", "1"},
    {"a42", "y2 L' U' L F' U' L F' L' F L U L' F", "", "0"},
    {"a42", "y' L U L' R U2 R2 U' R2 U' R'", "", "0"},
    {"a42", "R' U R2 U2 R' U R' F R F'", "", "0"},
    {"a42", "R' U' R U2 R' U2 R2 U R'", "", "0"},
    {"a1a", "y' F' U' F R' U' R", "", "6"},
    {"a1a", "y' U2 R U' R' U' R' U' R", "", "2"},
    {"a1a", "y' U' R U R2 U' R U' R' U R", "", "1"},
    {"a1a", "y' R' F R F' y R U' R'", "", "0"},
    {"a1a", "U L' U' F' U' F L", "", "0"},
    {"a2a", "y2 R' F R F' L U2 L'", "", "2"},
    {"a2a", "y2 R' F R F' U' L U' L'", "", "1"},
    {"a2a", "y2 F' U' F U' y2 F' U' F", "", "1"},
    {"a2a", "y2 U2 R U' R' U2 B' U' B", "", "0"},
    {"a3a", "y R' F R2 U' R' U F'", "", "1"},
    {"a3a", "y R' F R F' d R' F R F'", "", "0"},
    {"a3a", "y F' U' F U2 L' U' L", "", "0"},
    {"a3a", "y U2 R U' R' U L' U' L", "", "0"},
    {"a3a", "y2 F' L F L' U2 L U' L'", "", "0"},
    {"a4a", "y' U R U R2 U2 R", "", "3"},
    {"a4a", "y2 U R' U R y U R' U2 R", "", "2"},
    {"a4a", "U L' U L y' U2 R' U R", "", "1"},
    {"a4a", "y' U R U' R' S R2 S'", "", "0"},
    {"a4a", "U L' U L U' F R' F' R", "", "0"},
    {"a5a", "y2 y U L' U L R' U2 R", "", "6"},
    {"a5a", "y2 U l U' F2 U l'", "", "2"},
    {"a5a", "y2 U R U R' y R' U R", "", "0"},
    {"a6a", "y U R L' U L R'", "", "6"},
    {"a6a", "y y U L' U L f' L f", "", "1"},
    {"a6a", "y U R U R' U' L' U L", "", "1"},
    {"a7a", "y' U2 R U' R' U2 R' U R", "", "7"},
    {"a7a", "y' U2 R U' R' U R' U2 R", "", "1"},
    {"a7a", "y' R U2 R' U R' U' R", "", "0"},
    {"a7a", "y' U2 R U R' U2 R' U' R", "", "0"},
    {"a7a", "L F L' F L F' L'", "", "0"},
    {"a7a", "U2 L F' L' F y' U' R' U' R", "", "0"},
    {"a8a", "y2 U2 R U' R' d L' U L", "", "2"},
    {"a8a", "y' U2 L F' L' F U2 R' U' R", "", "2"},
    {"a8a", "y2 R U2 R' f' L' f", "", "1"},
    {"a8a", "y2 U2 R U' R' y R' U2 R", "", "0"},
    {"a8a", "y' U R' D R U' R' U' D' R", "", "0"},
    {"a8a", "y' U2 F U2 R' U' R F'", "", "0"},
    {"a8a", "y U2 f R f' U L' U' L", "", "0"},
    {"a9a", "y U2 R U' R' L' U L", "", "2"},
    {"a9a", "y R U2 R' U' L' U' L", "", "0"},
  };

  /** The rotations tried in front of an execution, those keeping the cross down first. */
  private static final String[] TILTS = {"", "z2", "x", "x'", "z", "z'"};
  private static final String[] SPINS = {"", "y", "y'", "y2"};

  /** The slots other than front right, as their corner and their edge. */
  private static final int[][] OTHER_SLOTS = {
    {Cubies.DLF, Cubies.FL}, {Cubies.DBL, Cubies.BL}, {Cubies.DRB, Cubies.BR},
  };

  private F2LCaseAlgorithms() {
  }

  /** The algorithms for a case, as {@link F2LCases} names it, most voted first. */
  public static List<Algorithm> forCase(String pairCase) {
    int votes = 0;
    for (String[] row : ALGORITHMS) {
      if (row[0].equals(pairCase)) {
        votes += Integer.parseInt(row[3]);
      }
    }
    List<Algorithm> algorithms = new ArrayList<>();
    for (String[] row : ALGORITHMS) {
      if (row[0].equals(pairCase)) {
        int share = votes == 0 ? 0 : Math.round(Integer.parseInt(row[3]) * 100f / votes);
        algorithms.add(new Algorithm(row[1], row[2].isEmpty() ? null : row[2], share, false));
      }
    }
    return Collections.unmodifiableList(algorithms);
  }

  /**
   * What to show a solver for a case: the most voted algorithm, and beside it those with at least
   * {@link LastLayerCaseAlgorithms#DEFAULT_MIN_SHARE} of the votes, by the same rules as a last
   * layer case, including when the first is called the recommended one.
   */
  public static List<Algorithm> shownForCase(String pairCase) {
    List<Algorithm> shown = new ArrayList<>();
    for (Algorithm algorithm : forCase(pairCase)) {
      if (shown.isEmpty() || (algorithm.getShare() >= LastLayerCaseAlgorithms.DEFAULT_MIN_SHARE
          && shown.size() < LastLayerCaseAlgorithms.MOST_SHOWN)) {
        shown.add(algorithm);
      }
    }
    float lead = LastLayerCaseAlgorithms.CLEAR_LEAD;
    if (shown.size() > 1 && shown.get(0).getShare() >= shown.get(1).getShare() * lead) {
      Algorithm top = shown.get(0);
      shown.set(0, new Algorithm(top.getMoves(), top.getEmptySlot(), top.getShare(), true));
    }
    return Collections.unmodifiableList(shown);
  }

  /**
   * Which of the case's algorithms an execution was, or null for none of them. Compared as
   * {@link AlgorithmForm}s from every grip, so a back slot's pair, a regrip, the turn that set the
   * pair up and moves turned and taken back all read as the algorithm they surround.
   *
   * @param executedMoves the moves turned for the pair, in the solver's own frame
   */
  public static Algorithm matching(String pairCase, String executedMoves) {
    if (pairCase == null || executedMoves == null) {
      return null;
    }
    List<Algorithm> algorithms = forCase(pairCase);
    int at = indexOfTurning(pairCase, formsOf(algorithms), executedMoves);
    return at < 0 ? null : algorithms.get(at);
  }

  /**
   * Whether the execution is one hardly anybody turns, and how long it is beside the shortest
   * algorithm in use, by the same rules as {@link LastLayerCaseAlgorithms#read}. A pair built
   * through another unsolved slot in no more turns than that is not unusual, listed or not: see
   * {@link #usesFreeSlot}.
   */
  public static AlgorithmExecution read(String pairCase, String executedMoves) {
    if (pairCase == null || executedMoves == null || AlgorithmForm.key(executedMoves) == null) {
      return new AlgorithmExecution(false, 0, 0);
    }
    List<Algorithm> algorithms = forCase(pairCase);
    int at = indexOfTurning(pairCase, formsOf(algorithms), executedMoves);
    int shortest = 0;
    for (int i = 0; i < algorithms.size(); i++) {
      if (i > 0 && algorithms.get(i).getShare() < LastLayerCaseAlgorithms.UNUSUAL_SHARE) {
        continue;
      }
      int length = AlgorithmForm.comparable(algorithms.get(i).getMoves()).size();
      if (length > 0 && (shortest == 0 || length < shortest)) {
        shortest = length;
      }
    }
    int moves = AlgorithmForm.lengthAsDrawn(executedMoves, drawn(pairCase));
    boolean unusual = !algorithms.isEmpty() && (at < 0
        || (at > 0 && algorithms.get(at).getShare() < LastLayerCaseAlgorithms.UNUSUAL_SHARE));
    if (unusual && at < 0 && moves <= shortest && usesFreeSlot(pairCase, executedMoves)) {
      unusual = false;
    }
    return new AlgorithmExecution(unusual, moves, shortest);
  }

  /**
   * Whether a pair was turned with none of its case's algorithms, nor through a free slot in as few
   * turns: the pairs worth pointing out.
   */
  public static boolean isUnlisted(String pairCase, String executedMoves) {
    return matching(pairCase, executedMoves) == null
        && read(pairCase, executedMoves).isUnusual();
  }

  /**
   * Whether the execution leaves another slot changed, which it can only do by building the pair
   * through a slot not yet solved. Such tricks are as many as the free slots around a case, so no
   * sheet lists them all: one is read by what it does rather than looked up.
   */
  static boolean usesFreeSlot(String pairCase, String executedMoves) {
    List<String> stood = AlgorithmForm.asDrawn(executedMoves, drawn(pairCase));
    if (stood == null) {
      return false;
    }
    String state = Notation.caseState(AlgorithmForm.written(stood));
    for (int[] slot : OTHER_SLOTS) {
      if (!Cubies.inPlace(state, Cubies.CORNERS[slot[0]])
          || !Cubies.inPlace(state, Cubies.EDGES[slot[1]])) {
        return true;
      }
    }
    return false;
  }

  /**
   * The moves in the solver's own face letters, from the grip the pair was started in: the opening
   * rotation off and any regrip after it folded into the faces turned. Where that does not solve the
   * case as drawn, the one rotation that makes it is put in front, so a pair turned into another
   * slot reads as the turns the solver made rather than renamed for front right. Null where no
   * rotation does, and for notation nothing can read.
   */
  public static String asTurned(String pairCase, String moves) {
    if (moves == null) {
      return null;
    }
    String turned;
    try {
      turned = AlgorithmForm.written(AlgorithmForm.of(AlgorithmForm.withoutOpeningGrip(moves)));
    } catch (RuntimeException e) {
      return null;
    }
    if (turned.isEmpty()) {
      return null;
    }
    for (String tilt : TILTS) {
      for (String spin : SPINS) {
        String grip = (tilt + " " + spin).trim();
        String held = grip.isEmpty() ? turned : grip + " " + turned;
        if (solves(pairCase, held)) {
          return held;
        }
      }
    }
    return null;
  }

  /** One string naming what an execution turned, the same from whichever slot it was turned in. */
  public static String keyAsDrawn(String pairCase, String moves) {
    return AlgorithmForm.keyAsDrawn(moves, drawn(pairCase));
  }

  /** Whether two spellings are the same algorithm turned, from whichever slot. */
  public static boolean sameTurning(String pairCase, String moves, String other) {
    if (moves == null || other == null) {
      return false;
    }
    if (moves.equals(other)) {
      return true;
    }
    String key = keyAsDrawn(pairCase, moves);
    return key != null && key.equals(keyAsDrawn(pairCase, other));
  }

  /**
   * The case held with the cross up and the pair going into up-front-left, in URFDLB facelets:
   * turned over with a z2, which is how it is drawn, that is the cross down and the slot in front
   * right. Read off the most voted algorithm, the one the picture has to agree with.
   */
  public static String crossUpFacelets(String pairCase) {
    List<Algorithm> algorithms = forCase(pairCase);
    return algorithms.isEmpty() ? null
        : Notation.caseState("z2 " + algorithms.get(0).getMoves() + " z2");
  }

  private static AlgorithmForm.Drawn drawn(final String pairCase) {
    return new AlgorithmForm.Drawn() {
      @Override
      public boolean solves(String stood) {
        return F2LCaseAlgorithms.solves(pairCase, stood);
      }
    };
  }

  private static List<List<String>> formsOf(List<Algorithm> algorithms) {
    List<List<String>> forms = new ArrayList<>();
    for (Algorithm algorithm : algorithms) {
      forms.add(AlgorithmForm.comparable(algorithm.getMoves()));
    }
    return forms;
  }

  static int indexOfTurning(String pairCase, List<List<String>> forms, String moves) {
    return AlgorithmForm.indexOfTurning(forms, moves, drawn(pairCase));
  }

  /**
   * Whether moves put the pair of the given case into front right, with the cross down. Asked of
   * the table by its test, and of anything a user types in before it is kept against a case.
   */
  public static boolean solves(String pairCase, String moves) {
    if (pairCase == null || moves == null || moves.trim().isEmpty()) {
      return false;
    }
    try {
      return pairCase.equals(
          F2LCases.pairCase(Notation.caseState(moves), Cubies.D, Cubies.DFR, Cubies.FR));
    } catch (RuntimeException e) {
      return false; // unreadable, or a sequence that does not put the cube back down
    }
  }

  static String[][] rows() {
    return ALGORITHMS.clone();
  }

  public static final class Algorithm {

    private final String moves;
    private final String emptySlot;
    private final int share;
    private final boolean recommended;

    Algorithm(String moves, String emptySlot, int share, boolean recommended) {
      this.moves = moves;
      this.emptySlot = emptySlot;
      this.share = share;
      this.recommended = recommended;
    }

    public String getMoves() {
      return moves;
    }

    /** The slot ("fl", "br") that has to be empty for this algorithm, or null if none does. */
    public String getEmptySlot() {
      return emptySlot;
    }

    /** The share of its case's votes this algorithm holds, in percent. */
    public int getShare() {
      return share;
    }

    /** The one to learn first, said only where the vote is not close. */
    public boolean isRecommended() {
      return recommended;
    }
  }
}
