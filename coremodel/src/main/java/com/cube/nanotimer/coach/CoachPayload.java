package com.cube.nanotimer.coach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Everything a coach is told about a solver, and the only thing that leaves the device.
 *
 * <p>It is numbers and vocabulary codes, and it is that on purpose. No user-authored text rides in
 * it at any version: solve type names, custom step names and drill names are all written by the
 * user, and a payload carrying them would be a prompt-injection channel straight into whatever
 * reads this. The same shape is what lets a plan be checked afterwards, since a claim citing a
 * number that is not in here was invented.
 *
 * <p>What is here has already been through the floors and the outlier rule: a figure too thin to
 * stand is absent rather than caveated, because nothing can be careful about a number it never saw.
 * Windows are counts of solves and drills rather than dates, so the payload says how much it looked
 * at without saying when the user was cubing.
 *
 * <p>Versioned from day one because old clients will be sending old payloads for years, and read
 * back through the same class it is written by, so the two sides cannot drift.
 */
public class CoachPayload {

  /** The newest payload this app writes and can read back. Version 2 added the two-look count and
   * the known set, 3 the cases to learn, 4 the F2L groups, 5 the F2L moves over the usual
   * algorithms and the window they were replayed from, 6 each step's moves and stalls and the last
   * layer's moves over the usual algorithms, 7 the OLL step split by one look and two, 8 the last
   * plan and the solves since it, 9 the two-look PLL count and the PLL step split the same way, 10
   * the language the plan is to be written in, 11 nothing new but that the app runs a look-ahead
   * drill ({@link #LOOKAHEAD_DRILL_VERSION}), 12 the F2L rotations of gyro solves; an older payload
   * is still read, and is simply missing them. */
  public static final int VERSION = 12;

  /** The first payload from an app that runs a look-ahead drill, which needs drill spec 6. */
  public static final int LOOKAHEAD_DRILL_VERSION = 11;

  private final int schemaVersion;
  private final String puzzle;
  private final String method;
  private final int familyWindow;
  private final int caseWindow;
  private final int drillWindow;
  private final Integer twoLookCount;
  private final StepFigure solve;
  private final List<StepFigure> families;
  private final List<StepFigure> parts;
  private final List<StepFigure> cases;
  private final List<StepFigure> drillCases;
  private final List<CaseComparison> comparisons;
  private final List<String> knownCases;
  private final List<String> learningCases;
  private final List<String> toLearnCases;
  private final List<StepFigure> f2lGroups;
  private final MoveFigure f2lMoves;
  private final List<MoveFigure> f2lHabits;
  private final Integer f2lMoveWindow;
  private final List<TurningFigure> turning;
  private final Integer turningWindow;
  private final List<MoveFigure> llMoves;
  private final List<MoveFigure> llHabits;
  private final Integer llMoveWindow;
  private final List<StepFigure> ollLooks;
  private final PreviousPlan previous;
  private final Integer twoLookPllCount;
  private final List<StepFigure> pllLooks;
  private final String language;
  private final F2lRotationFigure f2lRotations;

  /**
   * @param solve the whole solve's figures, or null when the window holds too few to quote
   * @param familyWindow how many solves the step figures were read from, {@code caseWindow} the
   *     same for the case figures, which need a longer look to say anything
   * @param drillWindow how many recorded drills the drill figures were read from
   * @param twoLookCount how many of those solves took the last layer in more than one algorithm,
   *     null in a payload written before it was counted — which is not the same as none
   * @param knownCases the cases the solver has been shown to execute in one algorithm unaided,
   *     {@code learningCases} the ones they have not. A case in neither has too little behind it to
   *     say so, which is its own answer and is never the same as not knowing it.
   * @param toLearnCases the subset of {@code learningCases} never once put in with a single
   *     algorithm, so a plan can say learn where it would otherwise say drill
   */
  public CoachPayload(int schemaVersion, String puzzle, String method, int familyWindow,
      int caseWindow, int drillWindow, Integer twoLookCount, StepFigure solve,
      List<StepFigure> families, List<StepFigure> parts, List<StepFigure> cases,
      List<StepFigure> drillCases, List<CaseComparison> comparisons, List<String> knownCases,
      List<String> learningCases, List<String> toLearnCases) {
    this(schemaVersion, puzzle, method, familyWindow, caseWindow, drillWindow, twoLookCount, solve,
        families, parts, cases, drillCases, comparisons, knownCases, learningCases, toLearnCases,
        null);
  }

  /**
   * As above, with the F2L pairs gathered into groups ({@link #getF2lGroups}), null or empty where
   * none cleared its floor.
   */
  public CoachPayload(int schemaVersion, String puzzle, String method, int familyWindow,
      int caseWindow, int drillWindow, Integer twoLookCount, StepFigure solve,
      List<StepFigure> families, List<StepFigure> parts, List<StepFigure> cases,
      List<StepFigure> drillCases, List<CaseComparison> comparisons, List<String> knownCases,
      List<String> learningCases, List<String> toLearnCases, List<StepFigure> f2lGroups) {
    this(schemaVersion, puzzle, method, familyWindow, caseWindow, drillWindow, twoLookCount, solve,
        families, parts, cases, drillCases, comparisons, knownCases, learningCases, toLearnCases,
        f2lGroups, null, null);
  }

  /**
   * As above, with the whole F2L's moves over the usual algorithms ({@link #getF2lMoves}), null
   * where too few solves could be read, and each case's beside it ({@link #getF2lHabits}), which
   * are dropped with it.
   */
  public CoachPayload(int schemaVersion, String puzzle, String method, int familyWindow,
      int caseWindow, int drillWindow, Integer twoLookCount, StepFigure solve,
      List<StepFigure> families, List<StepFigure> parts, List<StepFigure> cases,
      List<StepFigure> drillCases, List<CaseComparison> comparisons, List<String> knownCases,
      List<String> learningCases, List<String> toLearnCases, List<StepFigure> f2lGroups,
      MoveFigure f2lMoves, List<MoveFigure> f2lHabits) {
    this(schemaVersion, puzzle, method, familyWindow, caseWindow, drillWindow, twoLookCount, solve,
        families, parts, cases, drillCases, comparisons, knownCases, learningCases, toLearnCases,
        f2lGroups, f2lMoves, f2lHabits, null);
  }

  /**
   * As above, saying how many solves were replayed for the F2L move figures
   * ({@link CoachPayloadBuilder#F2L_MOVE_WINDOW}). It rides explicitly because the payload carries
   * an F2L figure read from the family window as well, and the two must not be read as one. Null
   * where no moves are sent.
   */
  public CoachPayload(int schemaVersion, String puzzle, String method, int familyWindow,
      int caseWindow, int drillWindow, Integer twoLookCount, StepFigure solve,
      List<StepFigure> families, List<StepFigure> parts, List<StepFigure> cases,
      List<StepFigure> drillCases, List<CaseComparison> comparisons, List<String> knownCases,
      List<String> learningCases, List<String> toLearnCases, List<StepFigure> f2lGroups,
      MoveFigure f2lMoves, List<MoveFigure> f2lHabits, Integer f2lMoveWindow) {
    this(schemaVersion, puzzle, method, familyWindow, caseWindow, drillWindow, twoLookCount, solve,
        families, parts, cases, drillCases, comparisons, knownCases, learningCases, toLearnCases,
        f2lGroups, f2lMoves, f2lHabits, f2lMoveWindow, null, null);
  }

  /**
   * As above, with how each step and the whole solve are turned ({@link #getTurning}) and how many
   * solves that was replayed from, null where nothing was.
   */
  public CoachPayload(int schemaVersion, String puzzle, String method, int familyWindow,
      int caseWindow, int drillWindow, Integer twoLookCount, StepFigure solve,
      List<StepFigure> families, List<StepFigure> parts, List<StepFigure> cases,
      List<StepFigure> drillCases, List<CaseComparison> comparisons, List<String> knownCases,
      List<String> learningCases, List<String> toLearnCases, List<StepFigure> f2lGroups,
      MoveFigure f2lMoves, List<MoveFigure> f2lHabits, Integer f2lMoveWindow,
      List<TurningFigure> turning, Integer turningWindow) {
    this(schemaVersion, puzzle, method, familyWindow, caseWindow, drillWindow, twoLookCount, solve,
        families, parts, cases, drillCases, comparisons, knownCases, learningCases, toLearnCases,
        f2lGroups, f2lMoves, f2lHabits, f2lMoveWindow, turning, turningWindow, null, null, null);
  }

  /**
   * As above, with each last layer step's moves over the usual algorithms ({@link #getLlMoves}) and
   * each of its cases' ({@link #getLlHabits}), read from the same replayed solves as the F2L moves,
   * and how many solves that was. The cases and the window are dropped where no step is sent.
   */
  public CoachPayload(int schemaVersion, String puzzle, String method, int familyWindow,
      int caseWindow, int drillWindow, Integer twoLookCount, StepFigure solve,
      List<StepFigure> families, List<StepFigure> parts, List<StepFigure> cases,
      List<StepFigure> drillCases, List<CaseComparison> comparisons, List<String> knownCases,
      List<String> learningCases, List<String> toLearnCases, List<StepFigure> f2lGroups,
      MoveFigure f2lMoves, List<MoveFigure> f2lHabits, Integer f2lMoveWindow,
      List<TurningFigure> turning, Integer turningWindow, List<MoveFigure> llMoves,
      List<MoveFigure> llHabits, Integer llMoveWindow) {
    this(schemaVersion, puzzle, method, familyWindow, caseWindow, drillWindow, twoLookCount, solve,
        families, parts, cases, drillCases, comparisons, knownCases, learningCases, toLearnCases,
        f2lGroups, f2lMoves, f2lHabits, f2lMoveWindow, turning, turningWindow, llMoves, llHabits,
        llMoveWindow, null);
  }

  /**
   * As above, with the OLL step split by the solves that took it in one look and in two
   * ({@link #getOllLooks}).
   */
  public CoachPayload(int schemaVersion, String puzzle, String method, int familyWindow,
      int caseWindow, int drillWindow, Integer twoLookCount, StepFigure solve,
      List<StepFigure> families, List<StepFigure> parts, List<StepFigure> cases,
      List<StepFigure> drillCases, List<CaseComparison> comparisons, List<String> knownCases,
      List<String> learningCases, List<String> toLearnCases, List<StepFigure> f2lGroups,
      MoveFigure f2lMoves, List<MoveFigure> f2lHabits, Integer f2lMoveWindow,
      List<TurningFigure> turning, Integer turningWindow, List<MoveFigure> llMoves,
      List<MoveFigure> llHabits, Integer llMoveWindow, List<StepFigure> ollLooks) {
    this(schemaVersion, puzzle, method, familyWindow, caseWindow, drillWindow, twoLookCount, solve,
        families, parts, cases, drillCases, comparisons, knownCases, learningCases, toLearnCases,
        f2lGroups, f2lMoves, f2lHabits, f2lMoveWindow, turning, turningWindow, llMoves, llHabits,
        llMoveWindow, ollLooks, null);
  }

  /**
   * As above, with the last plan and the solves done since it ({@link #getPrevious}), null where
   * there is none to send.
   */
  public CoachPayload(int schemaVersion, String puzzle, String method, int familyWindow,
      int caseWindow, int drillWindow, Integer twoLookCount, StepFigure solve,
      List<StepFigure> families, List<StepFigure> parts, List<StepFigure> cases,
      List<StepFigure> drillCases, List<CaseComparison> comparisons, List<String> knownCases,
      List<String> learningCases, List<String> toLearnCases, List<StepFigure> f2lGroups,
      MoveFigure f2lMoves, List<MoveFigure> f2lHabits, Integer f2lMoveWindow,
      List<TurningFigure> turning, Integer turningWindow, List<MoveFigure> llMoves,
      List<MoveFigure> llHabits, Integer llMoveWindow, List<StepFigure> ollLooks,
      PreviousPlan previous) {
    this(schemaVersion, puzzle, method, familyWindow, caseWindow, drillWindow, twoLookCount, solve,
        families, parts, cases, drillCases, comparisons, knownCases, learningCases, toLearnCases,
        f2lGroups, f2lMoves, f2lHabits, f2lMoveWindow, turning, turningWindow, llMoves, llHabits,
        llMoveWindow, ollLooks, previous, null, null);
  }

  /**
   * As above, with how many solves took the PLL in more than one algorithm
   * ({@link #getTwoLookPllCount}) and the PLL step split by them ({@link #getPllLooks}).
   */
  public CoachPayload(int schemaVersion, String puzzle, String method, int familyWindow,
      int caseWindow, int drillWindow, Integer twoLookCount, StepFigure solve,
      List<StepFigure> families, List<StepFigure> parts, List<StepFigure> cases,
      List<StepFigure> drillCases, List<CaseComparison> comparisons, List<String> knownCases,
      List<String> learningCases, List<String> toLearnCases, List<StepFigure> f2lGroups,
      MoveFigure f2lMoves, List<MoveFigure> f2lHabits, Integer f2lMoveWindow,
      List<TurningFigure> turning, Integer turningWindow, List<MoveFigure> llMoves,
      List<MoveFigure> llHabits, Integer llMoveWindow, List<StepFigure> ollLooks,
      PreviousPlan previous, Integer twoLookPllCount, List<StepFigure> pllLooks) {
    this(schemaVersion, puzzle, method, familyWindow, caseWindow, drillWindow, twoLookCount, solve,
        families, parts, cases, drillCases, comparisons, knownCases, learningCases, toLearnCases,
        f2lGroups, f2lMoves, f2lHabits, f2lMoveWindow, turning, turningWindow, llMoves, llHabits,
        llMoveWindow, ollLooks, previous, twoLookPllCount, pllLooks, null);
  }

  /**
   * As above, with the language the plan is to be written in ({@link #getLanguage}), a code such
   * as "fr", or null for English.
   */
  public CoachPayload(int schemaVersion, String puzzle, String method, int familyWindow,
      int caseWindow, int drillWindow, Integer twoLookCount, StepFigure solve,
      List<StepFigure> families, List<StepFigure> parts, List<StepFigure> cases,
      List<StepFigure> drillCases, List<CaseComparison> comparisons, List<String> knownCases,
      List<String> learningCases, List<String> toLearnCases, List<StepFigure> f2lGroups,
      MoveFigure f2lMoves, List<MoveFigure> f2lHabits, Integer f2lMoveWindow,
      List<TurningFigure> turning, Integer turningWindow, List<MoveFigure> llMoves,
      List<MoveFigure> llHabits, Integer llMoveWindow, List<StepFigure> ollLooks,
      PreviousPlan previous, Integer twoLookPllCount, List<StepFigure> pllLooks, String language) {
    this(schemaVersion, puzzle, method, familyWindow, caseWindow, drillWindow, twoLookCount, solve,
        families, parts, cases, drillCases, comparisons, knownCases, learningCases, toLearnCases,
        f2lGroups, f2lMoves, f2lHabits, f2lMoveWindow, turning, turningWindow, llMoves, llHabits,
        llMoveWindow, ollLooks, previous, twoLookPllCount, pllLooks, language, null);
  }

  /**
   * As above, with the F2L rotations of the window's gyro solves ({@link #getF2lRotations}), or null
   * where there were too few.
   */
  public CoachPayload(int schemaVersion, String puzzle, String method, int familyWindow,
      int caseWindow, int drillWindow, Integer twoLookCount, StepFigure solve,
      List<StepFigure> families, List<StepFigure> parts, List<StepFigure> cases,
      List<StepFigure> drillCases, List<CaseComparison> comparisons, List<String> knownCases,
      List<String> learningCases, List<String> toLearnCases, List<StepFigure> f2lGroups,
      MoveFigure f2lMoves, List<MoveFigure> f2lHabits, Integer f2lMoveWindow,
      List<TurningFigure> turning, Integer turningWindow, List<MoveFigure> llMoves,
      List<MoveFigure> llHabits, Integer llMoveWindow, List<StepFigure> ollLooks,
      PreviousPlan previous, Integer twoLookPllCount, List<StepFigure> pllLooks, String language,
      F2lRotationFigure f2lRotations) {
    this.f2lRotations = f2lRotations;
    this.language = language == null ? null : CoachGlossary.Language.of(language).code();
    this.twoLookPllCount = twoLookPllCount;
    this.pllLooks = unmodifiable(pllLooks);
    this.previous = previous;
    this.ollLooks = unmodifiable(ollLooks);
    this.llMoves = moves(llMoves);
    this.llMoveWindow = this.llMoves.isEmpty() ? null : llMoveWindow;
    this.llHabits = this.llMoves.isEmpty() ? Collections.<MoveFigure>emptyList() : moves(llHabits);
    this.f2lMoveWindow = f2lMoveWindow;
    this.turning = turning == null
        ? Collections.<TurningFigure>emptyList()
        : Collections.unmodifiableList(new ArrayList<TurningFigure>(turning));
    this.turningWindow = this.turning.isEmpty() ? null : turningWindow;
    this.schemaVersion = schemaVersion;
    this.puzzle = puzzle;
    this.method = method;
    this.familyWindow = familyWindow;
    this.caseWindow = caseWindow;
    this.drillWindow = drillWindow;
    this.twoLookCount = twoLookCount;
    this.solve = solve;
    this.families = unmodifiable(families);
    this.parts = unmodifiable(parts);
    this.cases = unmodifiable(cases);
    this.drillCases = unmodifiable(drillCases);
    this.comparisons = comparisons == null
        ? Collections.<CaseComparison>emptyList()
        : Collections.unmodifiableList(new ArrayList<CaseComparison>(comparisons));
    this.knownCases = codes(knownCases);
    this.learningCases = codes(learningCases);
    this.toLearnCases = codes(toLearnCases);
    this.f2lGroups = unmodifiable(f2lGroups);
    this.f2lMoves = f2lMoves;
    this.f2lHabits = f2lMoves == null || f2lHabits == null
        ? Collections.<MoveFigure>emptyList()
        : Collections.unmodifiableList(new ArrayList<MoveFigure>(f2lHabits));
  }

  private static List<String> codes(List<String> codes) {
    return codes == null ? Collections.<String>emptyList()
        : Collections.unmodifiableList(new ArrayList<String>(codes));
  }

  private static List<MoveFigure> moves(List<MoveFigure> figures) {
    return figures == null ? Collections.<MoveFigure>emptyList()
        : Collections.unmodifiableList(new ArrayList<MoveFigure>(figures));
  }

  private static List<StepFigure> unmodifiable(List<StepFigure> figures) {
    return figures == null
        ? Collections.<StepFigure>emptyList()
        : Collections.unmodifiableList(new ArrayList<StepFigure>(figures));
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  /** The puzzle in the vocabulary a drill spec uses, "3x3". */
  public String getPuzzle() {
    return puzzle;
  }

  /** The method the solves were read as, "cfop". */
  public String getMethod() {
    return method;
  }

  public int getFamilyWindow() {
    return familyWindow;
  }

  public int getCaseWindow() {
    return caseWindow;
  }

  public int getDrillWindow() {
    return drillWindow;
  }

  /**
   * How many solves the F2L move figures were replayed from, or null in a payload carrying no F2L
   * moves at all. Sent because the payload also carries an F2L figure read from the family window.
   */
  public Integer getF2lMoveWindow() {
    return f2lMoveWindow;
  }

  /**
   * How many solves the last layer move figures were replayed from, or null in a payload carrying
   * none. The same solves as the F2L moves, said again so neither has to be read off the other.
   */
  public Integer getLlMoveWindow() {
    return llMoveWindow;
  }

  /**
   * How many solves the turning figures were replayed from, or null in a payload carrying none.
   */
  public Integer getTurningWindow() {
    return turningWindow;
  }

  /**
   * How many of the family window's solves needed more than one algorithm for the last layer's
   * orientation, which is what a two-look OLL is. A technique rather than a speed, and the one level
   * difference in the splits that is real, so it is counted rather than read off a mean.
   *
   * <p>Null where the payload was written before this was counted, which no reader may take for a
   * count of none: a stored plan re-read that way would say the solver two-looks nothing.
   */
  public Integer getTwoLookCount() {
    return twoLookCount;
  }

  /**
   * How many of the family window's solves needed more than one algorithm for the last layer's
   * permutation, which is what a two-look PLL is. Null in a payload written before version 9, which
   * is not a count of none.
   */
  public Integer getTwoLookPllCount() {
    return twoLookPllCount;
  }

  /** The whole solve, or null when there were too few to quote one. */
  public StepFigure getSolve() {
    return solve;
  }

  /** The steps of the method, in solving order. */
  public List<StepFigure> getFamilies() {
    return families;
  }

  /** The codes a step was built in parts under, which are not steps of the method themselves. */
  public List<StepFigure> getParts() {
    return parts;
  }

  /** The named cases, worst cost first. */
  public List<StepFigure> getCases() {
    return cases;
  }

  /** The same cases as they run in a drill, which is a different question from how a solve asks them. */
  public List<StepFigure> getDrillCases() {
    return drillCases;
  }

  /** The cases where solve and drill are far enough apart, on samples thick enough, to mean something. */
  public List<CaseComparison> getComparisons() {
    return comparisons;
  }

  /**
   * The cases the solver has been shown to execute in one algorithm on their own. Codes only: it is
   * a status and not a figure, and how fast each of them runs is already here as a case.
   */
  public List<String> getKnownCases() {
    return knownCases;
  }

  /**
   * The cases they reach for a second algorithm on, or have had to be shown. Every other case is
   * absent because too little has been seen of it, never because it is not known.
   */
  public List<String> getLearningCases() {
    return learningCases;
  }

  /**
   * The cases of {@link #getLearningCases} that have never once gone in with a single algorithm, so
   * they have not been learnt rather than slipped. Empty in a payload written before they were told
   * apart, which is not the same as every learning case having been learnt once.
   */
  public List<String> getToLearnCases() {
    return toLearnCases;
  }

  /**
   * The F2L pairs gathered across cases, since no one F2L case has a figure worth sending: the basic
   * pairs and the ones handed with a piece stuck in another slot ({@code f2l_basic},
   * {@code f2l_stuck}, the second carrying its share of the two), and the first pair of each solve
   * against the later ones ({@code f2l_first}, {@code f2l_later}). A comparison is here whole or
   * not at all, and empty in a payload written before version 4, which is not measured and not zero.
   */
  public List<StepFigure> getF2lGroups() {
    return f2lGroups;
  }

  /**
   * The whole F2L's moves a solve against what its pairs' usual algorithms add up to, or null where
   * too few solves could be set against them, or the payload predates version 5: not measured, and
   * never zero.
   */
  public MoveFigure getF2lMoves() {
    return f2lMoves;
  }

  /**
   * Each F2L case's extra moves and how often it went in with none of its listed algorithms, most
   * extra moves first. Only ever sent beside {@link #getF2lMoves}: moves are charged to the pair
   * that finishes next, so a case's figure alone would punish look-ahead.
   */
  public List<MoveFigure> getF2lHabits() {
    return f2lHabits;
  }

  /**
   * Each last layer step's moves an execution against its cases' usual algorithms, {@code oll} then
   * {@code pll}, counting only the executions taken in one algorithm. Empty where too few could be
   * read, or the payload predates version 6: not measured, and never zero.
   */
  public List<MoveFigure> getLlMoves() {
    return llMoves;
  }

  /**
   * Each last layer case's extra moves and how often it went in with none of its listed algorithms,
   * most extra moves first. Only ever sent beside {@link #getLlMoves}.
   */
  public List<MoveFigure> getLlHabits() {
    return llHabits;
  }

  /**
   * The OLL step over the family window's solves that took it in one look ({@code one_look}) and in
   * more than one ({@code two_look}), a skip in neither. Both or neither, and empty in a payload
   * written before version 7: not measured, and never a gap of nothing.
   */
  public List<StepFigure> getOllLooks() {
    return ollLooks;
  }

  /** The PLL step split the same way ({@code pll_looks}), empty before version 9. */
  public List<StepFigure> getPllLooks() {
    return pllLooks;
  }

  /**
   * The language the plan is to be written in: the app's own language setting, the one the plan
   * will be drawn among. English for a payload written before version 10, or for a language the
   * app has no translation for, since the app then shows English.
   */
  public CoachGlossary.Language getLanguage() {
    return CoachGlossary.Language.of(language);
  }

  /**
   * This payload with the language the plan is to be written in, a code such as "fr" or "pt-BR".
   * Set where the request is sent rather than where the figures are read, which knows nothing of
   * the screen.
   */
  public CoachPayload withLanguage(String code) {
    return new CoachPayload(schemaVersion, puzzle, method, familyWindow, caseWindow, drillWindow,
        twoLookCount, solve, families, parts, cases, drillCases, comparisons, knownCases,
        learningCases, toLearnCases, f2lGroups, f2lMoves, f2lHabits, f2lMoveWindow, turning,
        turningWindow, llMoves, llHabits, llMoveWindow, ollLooks, previous, twoLookPllCount,
        pllLooks, code, f2lRotations);
  }

  /**
   * This payload with the F2L rotations of the move window's gyro solves, built by
   * {@link CoachPayloadBuilder#f2lRotations}. Added after the build because only the app can read a
   * gyro track, as with the pairs.
   */
  public CoachPayload withF2lRotations(F2lRotationFigure rotations) {
    return new CoachPayload(schemaVersion, puzzle, method, familyWindow, caseWindow, drillWindow,
        twoLookCount, solve, families, parts, cases, drillCases, comparisons, knownCases,
        learningCases, toLearnCases, f2lGroups, f2lMoves, f2lHabits, f2lMoveWindow, turning,
        turningWindow, llMoves, llHabits, llMoveWindow, ollLooks, previous, twoLookPllCount,
        pllLooks, language, rotations);
  }

  /**
   * How often the solver rotates during F2L and what it costs them, or null: too few gyro solves,
   * a cube with no gyro, or a payload written before version 12. Not measured, and never none.
   */
  public F2lRotationFigure getF2lRotations() {
    return f2lRotations;
  }

  /**
   * The last plan without its text and the step figures since it, or null: a first plan, another
   * method, too few solves since, or a payload written before version 8.
   */
  public PreviousPlan getPrevious() {
    return previous;
  }

  /**
   * Each step's moves and stalls a solve, in solving order, then the whole solve's under
   * {@code solve}. Empty where too few solves could be replayed, or the payload predates version 6:
   * not measured, and never zero.
   */
  public List<TurningFigure> getTurning() {
    return turning;
  }

  /**
   * The figure at a path, or null when the payload holds nothing there. Paths are how a plan cites
   * what it leans on: {@code solve.mean_ms}, {@code families.pll.recognition_ms},
   * {@code cases.pll_gb.time_lost_ms}, {@code comparisons.pll_gb.gap_ms}, {@code windows.cases},
   * {@code two_look_count}, {@code two_look_pll_count}, {@code to_learn_cases.count},
   * {@code to_learn_cases.pll.count}, {@code to_learn_cases.oll_53},
   * {@code f2l_groups.f2l_stuck.share}, {@code f2l_groups.f2l_first.recognition_ms},
   * {@code f2l_moves.extra_moves}, {@code f2l_habits.pair_27.off_list_rate},
   * {@code turning.cross.stall_ms}, {@code turning.solve.moves}, {@code ll_moves.pll.extra_moves},
   * {@code ll_habits.pll_rb.off_list_rate}, {@code oll_looks.two_look.mean_ms},
   * {@code pll_looks.one_look.mean_ms}, {@code f2l_rotations.rotated_pairs},
   * {@code f2l_rotation_cases.pair_27.rotated}, and under
   * {@code previous.} what {@link PreviousPlan#value} answers.
   *
   * <p>A case set answers how many cases are in it and whether a named one is among them, which is
   * what lets a plan saying "learn this case" be checked rather than believed. A case that is not in
   * the set answers nothing rather than zero: an absence is not evidence, and a payload written
   * before a set existed carries it empty, which would otherwise read as every case being outside
   * it.
   */
  public Double value(String path) {
    if (path.startsWith("previous.")) {
      return previous == null ? null : previous.value(path.substring("previous.".length()));
    }
    String[] segments = path.split("\\.");
    if (segments.length == 1) {
      Integer count = "two_look_count".equals(segments[0]) ? twoLookCount
          : "two_look_pll_count".equals(segments[0]) ? twoLookPllCount : null;
      return count == null ? null : Double.valueOf(count.intValue());
    }
    if (segments.length == 2 && "solve".equals(segments[0])) {
      return solve == null ? null : field(solve, segments[1]);
    }
    if (segments.length == 2 && "f2l_rotations".equals(segments[0])) {
      return f2lRotations == null ? null : f2lRotations.field(segments[1]);
    }
    if (segments.length == 2 && "f2l_moves".equals(segments[0])) {
      return f2lMoves == null ? null : f2lMoves.field(segments[1]);
    }
    if (segments.length == 2 && "windows".equals(segments[0])) {
      return window(segments[1]);
    }
    List<String> set = segments.length == 2 ? caseSet(segments[0]) : null;
    if (set != null) {
      return "count".equals(segments[1]) ? Double.valueOf(set.size())
          : set.contains(segments[1]) ? Double.valueOf(1) : null;
    }
    if (segments.length != 3) {
      return null;
    }
    set = caseSet(segments[0]);
    if (set != null) {
      return "count".equals(segments[2]) ? Double.valueOf(countIn(set, segments[1])) : null;
    }
    if ("comparisons".equals(segments[0])) {
      CaseComparison comparison = comparison(segments[1]);
      return comparison == null ? null : field(comparison, segments[2]);
    }
    if ("f2l_rotation_cases".equals(segments[0])) {
      F2lRotationFigure.Case rotated =
          f2lRotations == null ? null : f2lRotations.getCase(segments[1]);
      return rotated == null ? null : rotated.field(segments[2]);
    }
    if ("f2l_habits".equals(segments[0])) {
      MoveFigure habit = move(f2lHabits, segments[1]);
      return habit == null ? null : habit.field(segments[2]);
    }
    if ("ll_moves".equals(segments[0]) || "ll_habits".equals(segments[0])) {
      MoveFigure figure = move("ll_moves".equals(segments[0]) ? llMoves : llHabits, segments[1]);
      return figure == null ? null : figure.field(segments[2]);
    }
    if ("turning".equals(segments[0])) {
      TurningFigure figure = turning(segments[1]);
      return figure == null ? null : figure.field(segments[2]);
    }
    StepFigure figure = figure(list(segments[0]), segments[1]);
    return figure == null ? null : field(figure, segments[2]);
  }

  private Double window(String name) {
    if ("families".equals(name)) {
      return Double.valueOf(familyWindow);
    }
    if ("cases".equals(name)) {
      return Double.valueOf(caseWindow);
    }
    if ("f2l_moves".equals(name)) {
      return f2lMoveWindow == null ? null : Double.valueOf(f2lMoveWindow.intValue());
    }
    if ("ll_moves".equals(name)) {
      return llMoveWindow == null ? null : Double.valueOf(llMoveWindow.intValue());
    }
    if ("turning".equals(name)) {
      return turningWindow == null ? null : Double.valueOf(turningWindow.intValue());
    }
    return "drills".equals(name) ? Double.valueOf(drillWindow) : null;
  }

  private List<String> caseSet(String name) {
    if ("known_cases".equals(name)) {
      return knownCases;
    }
    if ("learning_cases".equals(name)) {
      return learningCases;
    }
    return "to_learn_cases".equals(name) ? toLearnCases : null;
  }

  private static int countIn(List<String> set, String family) {
    int count = 0;
    for (String code : set) {
      if (code.startsWith(family + "_")) {
        count++;
      }
    }
    return count;
  }

  private List<StepFigure> list(String name) {
    if ("families".equals(name)) {
      return families;
    }
    if ("parts".equals(name)) {
      return parts;
    }
    if ("cases".equals(name)) {
      return cases;
    }
    if ("f2l_groups".equals(name)) {
      return f2lGroups;
    }
    if ("oll_looks".equals(name)) {
      return ollLooks;
    }
    if ("pll_looks".equals(name)) {
      return pllLooks;
    }
    return "drill_cases".equals(name) ? drillCases : Collections.<StepFigure>emptyList();
  }

  private static StepFigure figure(List<StepFigure> figures, String code) {
    for (StepFigure figure : figures) {
      if (figure.getCode().equals(code)) {
        return figure;
      }
    }
    return null;
  }

  private static MoveFigure move(List<MoveFigure> figures, String code) {
    for (MoveFigure habit : figures) {
      if (habit.getCode().equals(code)) {
        return habit;
      }
    }
    return null;
  }

  private TurningFigure turning(String code) {
    for (TurningFigure figure : turning) {
      if (figure.getCode().equals(code)) {
        return figure;
      }
    }
    return null;
  }

  private CaseComparison comparison(String code) {
    for (CaseComparison comparison : comparisons) {
      if (comparison.getCode().equals(code)) {
        return comparison;
      }
    }
    return null;
  }

  static Double field(StepFigure figure, String name) {
    if ("count".equals(name)) {
      return Double.valueOf(figure.getCount());
    }
    if ("mean_ms".equals(name)) {
      return Double.valueOf(figure.getMeanMs());
    }
    if ("recognition_ms".equals(name)) {
      return figure.getRecognitionMs() == null ? null
          : Double.valueOf(figure.getRecognitionMs().longValue());
    }
    if ("std_dev_ms".equals(name)) {
      return Double.valueOf(figure.getStdDevMs());
    }
    if ("best_ms".equals(name)) {
      return Double.valueOf(figure.getBestMs());
    }
    if ("rejection_rate".equals(name)) {
      return Double.valueOf(figure.getRejectionRate());
    }
    if ("family_mean_ms".equals(name)) {
      return figure.getFamilyMeanMs() == null ? null
          : Double.valueOf(figure.getFamilyMeanMs().longValue());
    }
    if ("time_lost_ms".equals(name)) {
      return figure.getTimeLostMs() == null ? null
          : Double.valueOf(figure.getTimeLostMs().longValue());
    }
    if ("skip_rate".equals(name)) {
      return figure.getSkipRate() == null ? null : figure.getSkipRate();
    }
    return "share".equals(name) ? figure.getShare() : null;
  }

  private static Double field(CaseComparison comparison, String name) {
    if ("solve_count".equals(name)) {
      return Double.valueOf(comparison.getSolveCount());
    }
    if ("solve_mean_ms".equals(name)) {
      return Double.valueOf(comparison.getSolveMeanMs());
    }
    if ("drill_count".equals(name)) {
      return Double.valueOf(comparison.getDrillCount());
    }
    if ("drill_mean_ms".equals(name)) {
      return Double.valueOf(comparison.getDrillMeanMs());
    }
    return "gap_ms".equals(name) ? Double.valueOf(comparison.getGapMs()) : null;
  }

  /**
   * Is this a code the payload speaks of at all? A plan names what it is about in
   * {@link FocusArea#getCodes}, which is what the screen draws the card from, so a case nobody sent
   * has to be catchable there and not only in the figures ({@link CoachPlan#unknownCodes}). A case
   * with no timing behind it still counts as sent where a set holds it: that is exactly a case to
   * learn, and it is the one a coach has most reason to name.
   */
  public boolean holds(String code) {
    return figure(families, code) != null || figure(parts, code) != null
        || figure(cases, code) != null || figure(drillCases, code) != null
        || comparison(code) != null || knownCases.contains(code) || learningCases.contains(code)
        || toLearnCases.contains(code) || figure(f2lGroups, code) != null
        || move(f2lHabits, code) != null || move(llHabits, code) != null
        || f2lRotations != null && f2lRotations.getCase(code) != null;
  }

  /** Is there enough here to say anything at all? */
  public boolean isEmpty() {
    return solve == null && families.isEmpty() && cases.isEmpty() && drillCases.isEmpty();
  }

  public String toJson() {
    try {
      JSONObject json = new JSONObject();
      json.put("schema_version", schemaVersion);
      json.put("puzzle", puzzle);
      json.put("method", method);
      JSONObject windows = new JSONObject();
      windows.put("families", familyWindow);
      windows.put("cases", caseWindow);
      windows.put("drills", drillWindow);
      if (f2lMoveWindow != null) {
        windows.put("f2l_moves", f2lMoveWindow);
      }
      if (turningWindow != null) {
        windows.put("turning", turningWindow);
      }
      if (llMoveWindow != null) {
        windows.put("ll_moves", llMoveWindow);
      }
      json.put("windows", windows);
      if (twoLookCount != null) {
        json.put("two_look_count", twoLookCount);
      }
      if (solve != null) {
        json.put("solve", solve.toJson());
      }
      json.put("families", figures(families));
      json.put("parts", figures(parts));
      json.put("cases", figures(cases));
      json.put("drill_cases", figures(drillCases));
      JSONArray gaps = new JSONArray();
      for (CaseComparison comparison : comparisons) {
        gaps.put(comparison.toJson());
      }
      json.put("comparisons", gaps);
      json.put("known_cases", new JSONArray(knownCases));
      json.put("learning_cases", new JSONArray(learningCases));
      json.put("to_learn_cases", new JSONArray(toLearnCases));
      json.put("f2l_groups", figures(f2lGroups));
      if (f2lMoves != null) {
        json.put("f2l_moves", f2lMoves.toJson());
        JSONArray habits = new JSONArray();
        for (MoveFigure habit : f2lHabits) {
          habits.put(habit.toJson());
        }
        json.put("f2l_habits", habits);
      }
      if (!turning.isEmpty()) {
        JSONArray turns = new JSONArray();
        for (TurningFigure figure : turning) {
          turns.put(figure.toJson());
        }
        json.put("turning", turns);
      }
      if (!llMoves.isEmpty()) {
        json.put("ll_moves", moveArray(llMoves));
        json.put("ll_habits", moveArray(llHabits));
      }
      if (!ollLooks.isEmpty()) {
        json.put("oll_looks", figures(ollLooks));
      }
      if (twoLookPllCount != null) {
        json.put("two_look_pll_count", twoLookPllCount);
      }
      if (!pllLooks.isEmpty()) {
        json.put("pll_looks", figures(pllLooks));
      }
      if (previous != null) {
        json.put("previous", previous.toJson());
      }
      if (language != null) {
        json.put("language", language);
      }
      if (f2lRotations != null) {
        json.put("f2l_rotations", f2lRotations.toJson());
      }
      return json.toString();
    } catch (JSONException e) {
      throw new IllegalStateException("Cannot write coach payload", e);
    }
  }

  private static JSONArray moveArray(List<MoveFigure> figures) throws JSONException {
    JSONArray array = new JSONArray();
    for (MoveFigure figure : figures) {
      array.put(figure.toJson());
    }
    return array;
  }

  private static JSONArray figures(List<StepFigure> figures) throws JSONException {
    JSONArray array = new JSONArray();
    for (StepFigure figure : figures) {
      array.put(figure.toJson());
    }
    return array;
  }

  /**
   * @throws IllegalArgumentException if the text is not a payload, or is one this version is too old
   *     to read, which is refused rather than guessed at
   */
  public static CoachPayload parse(String text) {
    try {
      JSONObject json = new JSONObject(text);
      int version = json.getInt("schema_version");
      if (version > VERSION) {
        throw new IllegalArgumentException(
            "Coach payload version " + version + ", this reader knows " + VERSION);
      }
      JSONObject windows = json.optJSONObject("windows");
      return new CoachPayload(version, json.getString("puzzle"), json.getString("method"),
          windows == null ? 0 : windows.optInt("families"),
          windows == null ? 0 : windows.optInt("cases"),
          windows == null ? 0 : windows.optInt("drills"),
          json.has("two_look_count") ? Integer.valueOf(json.getInt("two_look_count")) : null,
          json.has("solve") ? StepFigure.fromJson(json.getJSONObject("solve")) : null,
          figures(json, "families"), figures(json, "parts"), figures(json, "cases"),
          figures(json, "drill_cases"), comparisons(json), codes(json, "known_cases"),
          codes(json, "learning_cases"), codes(json, "to_learn_cases"),
          figures(json, "f2l_groups"),
          json.has("f2l_moves") ? MoveFigure.fromJson(json.getJSONObject("f2l_moves")) : null,
          moves(json, "f2l_habits"),
          windows != null && windows.has("f2l_moves")
              ? Integer.valueOf(windows.getInt("f2l_moves")) : null,
          turning(json),
          windows != null && windows.has("turning")
              ? Integer.valueOf(windows.getInt("turning")) : null,
          moves(json, "ll_moves"), moves(json, "ll_habits"),
          windows != null && windows.has("ll_moves")
              ? Integer.valueOf(windows.getInt("ll_moves")) : null,
          figures(json, "oll_looks"),
          json.has("previous") ? PreviousPlan.fromJson(json.getJSONObject("previous")) : null,
          json.has("two_look_pll_count")
              ? Integer.valueOf(json.getInt("two_look_pll_count")) : null,
          figures(json, "pll_looks"), json.optString("language", null),
          json.has("f2l_rotations")
              ? F2lRotationFigure.fromJson(json.getJSONObject("f2l_rotations")) : null);
    } catch (JSONException e) {
      throw new IllegalArgumentException("Not a coach payload: " + e.getMessage(), e);
    }
  }

  private static List<StepFigure> figures(JSONObject json, String field) throws JSONException {
    List<StepFigure> figures = new ArrayList<StepFigure>();
    JSONArray array = json.optJSONArray(field);
    for (int i = 0; array != null && i < array.length(); i++) {
      figures.add(StepFigure.fromJson(array.getJSONObject(i)));
    }
    return figures;
  }

  private static List<MoveFigure> moves(JSONObject json, String field) throws JSONException {
    List<MoveFigure> habits = new ArrayList<MoveFigure>();
    JSONArray array = json.optJSONArray(field);
    for (int i = 0; array != null && i < array.length(); i++) {
      habits.add(MoveFigure.fromJson(array.getJSONObject(i)));
    }
    return habits;
  }

  private static List<TurningFigure> turning(JSONObject json) throws JSONException {
    List<TurningFigure> turning = new ArrayList<TurningFigure>();
    JSONArray array = json.optJSONArray("turning");
    for (int i = 0; array != null && i < array.length(); i++) {
      turning.add(TurningFigure.fromJson(array.getJSONObject(i)));
    }
    return turning;
  }

  private static List<String> codes(JSONObject json, String field) throws JSONException {
    List<String> codes = new ArrayList<String>();
    JSONArray array = json.optJSONArray(field);
    for (int i = 0; array != null && i < array.length(); i++) {
      codes.add(array.getString(i));
    }
    return codes;
  }

  private static List<CaseComparison> comparisons(JSONObject json) throws JSONException {
    List<CaseComparison> comparisons = new ArrayList<CaseComparison>();
    JSONArray array = json.optJSONArray("comparisons");
    for (int i = 0; array != null && i < array.length(); i++) {
      comparisons.add(CaseComparison.fromJson(array.getJSONObject(i)));
    }
    return comparisons;
  }
}
