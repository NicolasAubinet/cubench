package com.cube.nanotimer.util.exportimport.csvexport;

import com.cube.nanotimer.cube.GyroTrackFormat;
import com.cube.nanotimer.cube.SolveMovesFormat;
import com.cube.nanotimer.cube.SolveStepsFormat;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.OrientationHistory;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.exportimport.csvimport.workers.CSVLineGrouper;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.ExportResult;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class ExportResultConverterTest {

  // The exported time must always keep full millisecond precision, regardless of the
  // "high precision timer" display setting. Otherwise an export/import round-trip rounds
  // times to centiseconds (every imported time ends in 0).
  @Test
  public void testExportKeepsMillisecondPrecision() {
    // 2005 ms displays as "2.01" with high precision off, but must export as "2.005".
    ExportResult result = new ExportResult("3x3", "Default", 2005, 0, false, false, null, null, null);
    String csvLine = ExportResultConverter.toCSVLine(result, false);
    String timeField = csvLine.split(",")[2];
    Assert.assertEquals("2.005", timeField);
  }

  // A comment must survive the encode/decode round-trip unchanged, including the
  // characters that used to corrupt the CSV: double-quotes, newlines, backslashes
  // and commas.
  @Test
  public void testCommentEncodingRoundTrips() {
    String comment = "He said \"hello\",\n then \\ DNFed";
    String decoded = ExportResultConverter.decodeComment(ExportResultConverter.encodeComment(comment));
    Assert.assertEquals(comment, decoded);
  }

  // The encoded comment must never contain a raw '"': the importer counts quotes to
  // stitch multi-line scrambles back together (CSVLineGrouper.group), so a quote in a
  // comment would desync that grouping. This is the root cause of the
  // "Nombre de colonnes invalide" import failure.
  @Test
  public void testEncodedCommentHasNoRawQuote() {
    String encoded = ExportResultConverter.encodeComment("odd \" quote \" count \"");
    Assert.assertEquals(-1, encoded.indexOf('"'));
  }

  // The importer (CSVLineGrouper.group) stitches multi-line scrambles back together by
  // counting '"' characters: a line with an odd number of quotes is treated as
  // "unterminated" and merged with the following line(s). An exported record must therefore
  // always have an even number of quotes, otherwise it swallows the next record and the
  // merged blob fails the column-count check.
  @Test
  public void testExportedLineHasEvenQuoteCount() {
    String comment = "odd \" quote count breaks line grouping";
    ExportResult result = new ExportResult("3x3x3", "Alex", 2005, 1700000000000L, false, false, null, "R U R'", comment);
    String csvLine = ExportResultConverter.toCSVLine(result, false);
    long quoteCount = csvLine.chars().filter(c -> c == '"').count();
    Assert.assertEquals("Exported line must contain an even number of quotes", 0, quoteCount % 2);
  }

  // A Square-1 scramble contains commas (e.g. "(1,0) / ..."). It is exported quoted, so the
  // field splitter must keep those commas inside the scramble field instead of treating them
  // as column separators.
  @Test
  public void testScrambleWithCommasKeepsColumnCount() {
    String scramble = "(1,0) / (-3,0) / (3,3) /";
    ExportResult result = new ExportResult("Square-1", "Default", 5000, 1700000000000L, false, false, null, scramble, null);
    String csvLine = ExportResultConverter.toCSVLine(result, false);
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(csvLine, ExportCSVGenerator.MAX_FIELDS_COUNT);
    Assert.assertEquals(scramble, fields.get(8)); // index 8 = scramble field
  }

  // Commas inside a comment must stay in the (last) comment column, not split it into extra
  // columns.
  @Test
  public void testCommentWithCommasKeepsColumnCount() {
    String comment = "PB single, lucky skip, very nice";
    ExportResult result = new ExportResult("3x3x3", "Alex", 5000, 1700000000000L, false, false, null, "R U R'", comment);
    String csvLine = ExportResultConverter.toCSVLine(result, false);
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(csvLine, ExportCSVGenerator.MAX_FIELDS_COUNT);
    Assert.assertEquals(ExportCSVGenerator.MAX_FIELDS_COUNT, fields.size());
    Assert.assertEquals(comment, ExportResultConverter.decodeComment(fields.get(ExportCSVGenerator.MAX_FIELDS_COUNT - 1)));
  }

  // A spread of awkward comments must survive the encode/decode round-trip byte-for-byte.
  @Test
  public void testCommentEdgeCasesRoundTrip() {
    String[] comments = {
      "\"",                          // a lone quote
      "\"wrapped\"",                 // wrapped in quotes
      "literal backslash-n: \\n",    // user typed a backslash followed by 'n'
      "trailing newlines\n\n\n",     // mirrors the comment in the file that failed
      "",                            // empty
      "accents éàü, 你好, 🧩",        // unicode + commas
      "all of it: \" , \\ \n end",   // quote, comma, backslash and newline together
    };
    for (String comment : comments) {
      String decoded = ExportResultConverter.decodeComment(ExportResultConverter.encodeComment(comment));
      Assert.assertEquals(comment, decoded);
    }
  }

  // End-to-end invariant over a spread of nasty comments: every exported record must keep an
  // even quote count, parse back to the full 10 columns, and yield the original comment. This
  // generalizes the regression so a future free-text field can't silently reintroduce the bug.
  @Test
  public void testExportedLineInvariantForNastyComments() {
    String[] comments = {
      "plain",
      "one \" quote",
      "two \"\" quotes",
      "comma, comma, comma",
      "quote \" and comma , together",
      "embedded\nnewline",
      "",
    };
    for (boolean withSmartcube : new boolean[] { false, true }) { // the invariant holds in both formats
      int maxFields = withSmartcube ? ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT : ExportCSVGenerator.MAX_FIELDS_COUNT;
      for (String comment : comments) {
        ExportResult result = new ExportResult("3x3x3", "Alex", 5000, 1700000000000L, false, false, null, "R U R'", comment);
        String csvLine = ExportResultConverter.toCSVLine(result, withSmartcube);

        long quotes = csvLine.chars().filter(c -> c == '"').count();
        Assert.assertEquals("even quote count for comment: " + comment, 0, quotes % 2);

        List<String> fields = ExportResultConverter.getFieldsFromCSVLine(csvLine, maxFields);
        Assert.assertEquals("column count for comment: " + comment, maxFields, fields.size());
        Assert.assertEquals("comment round-trip for comment: " + comment, comment, ExportResultConverter.decodeComment(fields.get(maxFields - 1)));
      }
    }
  }

  // ---- Smart cube section (15-column format) -------------------------------------------------

  private static final String MOVES = "F@0 F@126 U@564 R'@1833 y@8778 z2@8778 D@9135";
  private static final String STEPS = "0:cross:0:2449 1:f2l:3279:15495 1.0:pair_fl:1294:2158"
      + " 1.1:pair_lb:622:4711 2:oll:2092:2934 3:pll:1416:1711";

  private static ExportResult cubeResult(String comment) {
    ExportResult result = new ExportResult("3x3x3", "Default", 29376, 1700000000000L, false, false, null, "R U R' F2", comment);
    result.setSmartcubeMethod(CubeMethod.CFOP);
    result.setSmartcubeMoves(MOVES);
    result.setSmartcubeSteps(SolveStepsFormat.parse(STEPS));
    result.setSmartcubeStoppedStep(2);
    return result;
  }

  /** A real track out of the real encoder, so these carry what an export really would. */
  private static String gyroTrack() {
    CubeOrientation rest = new CubeOrientation(1, 0, 0, 0);
    OrientationHistory history = new OrientationHistory();
    for (int at = 0; at <= 2000; at += 50) {
      double radians = Math.toRadians(at / 20.0) / 2;
      history.onSample(new CubeOrientation(Math.cos(radians), 0, Math.sin(radians), 0), at);
    }
    return GyroTrackFormat.format(history.between(0, 3000), rest, 0);
  }

  private static ExportResult cubeResultWithGyro(String comment) {
    ExportResult result = cubeResult(comment);
    result.setSmartcubeGyroTrack(gyroTrack());
    return result;
  }

  // The line layout is the contract: 15 columns, smart cube fields in 9-12, comment last.
  @Test
  public void testSmartcubeFieldsSitBeforeTheComment() {
    String csvLine = ExportResultConverter.toCSVLine(cubeResult("a comment"), true);
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(csvLine, ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT);
    Assert.assertEquals(ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT, fields.size());
    Assert.assertEquals("CFOP", fields.get(9));
    Assert.assertEquals(MOVES, fields.get(10));
    Assert.assertEquals(STEPS, fields.get(11));
    Assert.assertEquals("2", fields.get(12));
    Assert.assertEquals("", fields.get(13)); // no DNF, so no time to restore
    Assert.assertEquals("a comment", fields.get(14));
  }

  // The headers are composed from column blocks rather than written out, so this is where the
  // file format is actually pinned down: every layout the app has ever written, spelled out in
  // full, oldest first. A header is what tells an importer where each field sits, so changing one
  // of these strings changes the format — if this test needs editing, that is the warning.
  @Test
  public void testEveryKnownHeaderIsExactlyWhatWasWritten() {
    Assert.assertEquals(Arrays.asList(
        "cubetype,solvetype,time,date,steps,plustwo,blind,scramble",
        "cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble",
        "cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble,comment",
        "cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble,smartcubeMethod,smartcubeMoves,smartcubeSteps,smartcubeStopped,comment",
        "cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble,timeBeforeDnf,comment",
        "cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble,smartcubeMethod,smartcubeMoves,smartcubeSteps,smartcubeStopped,timeBeforeDnf,comment"),
        ExportCSVGenerator.KNOWN_HEADER_LINES);

    // The two the app writes today, and the one directly behind them, are the layouts the parser
    // branches on by name.
    Assert.assertEquals("cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble,timeBeforeDnf,comment",
        ExportCSVGenerator.CSV_HEADER_LINE);
    Assert.assertEquals("cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble,smartcubeMethod,smartcubeMoves,smartcubeSteps,smartcubeStopped,timeBeforeDnf,comment",
        ExportCSVGenerator.SMARTCUBE_CSV_HEADER_LINE);
    Assert.assertEquals("cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble,smartcubeMethod,smartcubeMoves,smartcubeSteps,smartcubeStopped,comment",
        ExportCSVGenerator.LEGACY_SMARTCUBE_CSV_HEADER_LINE);
  }

  // The header must announce as many columns as the lines carry, or the comment fold desyncs.
  // Every layout the app ever wrote must still be recognized, each at its own column count.
  @Test
  public void testHeaderColumnCountMatchesTheLineLayout() {
    assertHeaderDeclares(ExportCSVGenerator.SMARTCUBE_CSV_HEADER_LINE, ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT);
    assertHeaderDeclares(ExportCSVGenerator.CSV_HEADER_LINE, ExportCSVGenerator.MAX_FIELDS_COUNT);
    assertHeaderDeclares(ExportCSVGenerator.LEGACY_SMARTCUBE_CSV_HEADER_LINE, ExportCSVGenerator.LEGACY_SMARTCUBE_MAX_FIELDS_COUNT);
    for (String knownHeader : ExportCSVGenerator.KNOWN_HEADER_LINES) {
      Assert.assertTrue(knownHeader, ExportCSVGenerator.isHeaderLegit(knownHeader)); // old exports stay importable
      Assert.assertTrue(knownHeader, ExportCSVGenerator.isHeaderLegit(knownHeader.toUpperCase()));
    }
    Assert.assertFalse(ExportCSVGenerator.isHeaderLegit("cubetype,solvetype,time"));

    // The two layouts that predate the comment column wrote fewer columns, but their files have
    // always been read with the comment folding at the tenth — that must not shift under them.
    Assert.assertEquals(ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT,
        ExportCSVGenerator.getMaxFieldsCount("cubetype,solvetype,time,date,steps,plustwo,blind,scramble"));
    Assert.assertEquals(ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT,
        ExportCSVGenerator.getMaxFieldsCount("cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble"));
    Assert.assertEquals(ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT,
        ExportCSVGenerator.getMaxFieldsCount("cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble,comment"));
  }

  // Each layout must be distinct, or getMaxFieldsCount answers for the wrong one.
  @Test
  public void testNoTwoLayoutsShareAColumnCount() {
    List<Integer> counts = new ArrayList<Integer>();
    for (String knownHeader : ExportCSVGenerator.KNOWN_HEADER_LINES) {
      Integer count = knownHeader.split(",").length;
      Assert.assertFalse("duplicate column count: " + count, counts.contains(count));
      counts.add(count);
    }
  }

  private void assertHeaderDeclares(String header, int fieldsCount) {
    Assert.assertEquals(header, fieldsCount, header.split(",").length);
    Assert.assertEquals(header, fieldsCount, ExportCSVGenerator.getMaxFieldsCount(header));
    Assert.assertTrue(header, ExportCSVGenerator.isHeaderLegit(header));
  }

  // Full field-level round-trip: export the record, split it back, re-apply the smart cube
  // fields, and compare everything they carry.
  @Test
  public void testSmartcubeRoundTripPreservesEveryField() {
    ExportResult original = cubeResult("PB, with a comma");
    String csvLine = ExportResultConverter.toCSVLine(original, true);
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(csvLine, ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT);

    ExportResult reimported = new ExportResult("3x3x3", "Default", 29376, 1700000000000L, false, false, null, null, null);
    ExportResultConverter.applySmartcubeFields(reimported, fields.get(9), fields.get(10), fields.get(11), fields.get(12));
    Assert.assertEquals(CubeMethod.CFOP, reimported.getSmartcubeMethod());
    Assert.assertEquals(MOVES, reimported.getSmartcubeMoves());
    Assert.assertEquals(STEPS, SolveStepsFormat.format(reimported.getSmartcubeSteps()));
    Assert.assertEquals(Integer.valueOf(2), reimported.getSmartcubeStoppedStep());
    Assert.assertEquals("PB, with a comma", ExportResultConverter.decodeComment(fields.get(14)));
  }

  // A solve no cube drove exports empty smart cube columns, and importing them stays null.
  @Test
  public void testNonCubeSolveRoundTripsWithEmptySmartcubeFields() {
    ExportResult result = new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, "R U R'", "note");
    String csvLine = ExportResultConverter.toCSVLine(result, true);
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(csvLine, ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT);
    Assert.assertEquals(ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT, fields.size());

    ExportResult reimported = new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, null, null);
    ExportResultConverter.applySmartcubeFields(reimported, fields.get(9), fields.get(10), fields.get(11), fields.get(12));
    Assert.assertNull(reimported.getSmartcubeMethod());
    Assert.assertNull(reimported.getSmartcubeMoves());
    Assert.assertNull(reimported.getSmartcubeSteps());
    Assert.assertNull(reimported.getSmartcubeStoppedStep());
    Assert.assertEquals("note", fields.get(14));
  }

  // Moves without a recognized method are a legitimate record (the solve matched no method) and
  // must round-trip alone.
  @Test
  public void testMovesAloneAreALegitimateRecord() {
    ExportResult reimported = new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, null, null);
    ExportResultConverter.applySmartcubeFields(reimported, "", MOVES, "", "");
    Assert.assertNull(reimported.getSmartcubeMethod());
    Assert.assertEquals(MOVES, reimported.getSmartcubeMoves());
    Assert.assertNull(reimported.getSmartcubeSteps());
  }

  // A record whose steps finished cleanly has no stopped step; the empty field must stay null.
  @Test
  public void testFinishedSolveHasNoStoppedStep() {
    ExportResult reimported = new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, null, null);
    ExportResultConverter.applySmartcubeFields(reimported, "CFOP", MOVES, STEPS, "");
    Assert.assertNull(reimported.getSmartcubeStoppedStep());
  }

  // The cross-field invariants: a half-valid smart cube record must be rejected whole.
  @Test
  public void testCorruptSmartcubeFieldsAreRejected() {
    assertSmartcubeRejected("ROUX", MOVES, STEPS, "");        // unknown method
    assertSmartcubeRejected("", MOVES, STEPS, "");            // steps without a method
    assertSmartcubeRejected("CFOP", MOVES, "", "");           // method without its steps
    assertSmartcubeRejected("CFOP", "hello world", STEPS, ""); // moves with not one valid token
    assertSmartcubeRejected("", MOVES, "", "2");              // stopped step without steps
    assertSmartcubeRejected("CFOP", MOVES, STEPS, "7");       // stopped step matching no step
    assertSmartcubeRejected("CFOP", MOVES, STEPS, "x");       // non-numeric stopped step
    assertSmartcubeRejected("CFOP", MOVES, "0:cross:ten:20", ""); // malformed steps
  }

  // The moves and steps fields must never need quoting or break the comma/quote invariants the
  // grouper and splitter rely on: no commas, no quotes, whatever the solve contained.
  @Test
  public void testSmartcubeFieldsRespectTheCsvInvariants() {
    Assert.assertEquals(-1, MOVES.indexOf(','));
    Assert.assertEquals(-1, STEPS.indexOf(','));
    String csvLine = ExportResultConverter.toCSVLine(cubeResult("multi\nline, \" comment"), true);
    long quotes = csvLine.chars().filter(c -> c == '"').count();
    Assert.assertEquals(0, quotes % 2);
  }

  // A multi-line scramble (e.g. Megaminx) next to the smart cube fields must still regroup into
  // one record and split into the right columns.
  @Test
  public void testMultiLineScrambleStillGroupsWithSmartcubeFields() {
    ExportResult result = cubeResult("note");
    result.setScramble("R++ D--\nR-- D++\nU'");
    String csvLine = ExportResultConverter.toCSVLine(result, true);
    List<String> physicalLines = Arrays.asList(csvLine.split("\n"));
    Assert.assertTrue(physicalLines.size() > 1); // the scramble did span lines

    List<String> grouped = CSVLineGrouper.group(physicalLines);
    Assert.assertEquals(1, grouped.size());
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(grouped.get(0), ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT);
    Assert.assertEquals(ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT, fields.size());
    Assert.assertEquals("R++ D--\nR-- D++\nU'", fields.get(8));
    Assert.assertEquals(STEPS, fields.get(11));
  }

  // A line without the smart cube columns keeps its own layout, comment last.
  @Test
  public void testNonCubeLinesKeepTheirLayout() {
    ExportResult result = new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, "R U R'", "old, style");
    String plainLine = ExportResultConverter.toCSVLine(result, false);
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(plainLine, ExportCSVGenerator.MAX_FIELDS_COUNT);
    Assert.assertEquals(ExportCSVGenerator.MAX_FIELDS_COUNT, fields.size());
    Assert.assertEquals("", fields.get(9)); // no DNF, so no time to restore
    Assert.assertEquals("old, style", ExportResultConverter.decodeComment(fields.get(10)));
  }

  // A history no cube ever touched must stay on the short header: the smart cube columns are
  // only written when something fills them.
  @Test
  public void testExportWithoutSmartcubeDataKeepsTheShortFormat() {
    ExportCSVGenerator generator = new ExportCSVGenerator(Arrays.asList(
        new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, "R U R'", "note, with comma"),
        new ExportResult("3x3x3", "Default", 6000, 1700000001000L, true, false, null, "F2 U", null)));
    Assert.assertEquals(ExportCSVGenerator.CSV_HEADER_LINE, generator.getHeaderLine());
    for (int i = 0; i < 2; i++) {
      List<String> fields = ExportResultConverter.getFieldsFromCSVLine(generator.getExportLine(i),
          ExportCSVGenerator.getMaxFieldsCount(generator.getHeaderLine()));
      Assert.assertEquals(ExportCSVGenerator.MAX_FIELDS_COUNT, fields.size());
    }
  }

  // One cube-recorded solve switches the whole file to the new format, non-cube lines included —
  // a file is one format, announced by its header.
  @Test
  public void testExportWithAnySmartcubeSolveUsesTheNewFormatThroughout() {
    ExportCSVGenerator generator = new ExportCSVGenerator(Arrays.asList(
        new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, "R U R'", null),
        cubeResult("cube solve")));
    Assert.assertEquals(ExportCSVGenerator.SMARTCUBE_CSV_HEADER_LINE, generator.getHeaderLine());
    for (int i = 0; i < 2; i++) {
      List<String> fields = ExportResultConverter.getFieldsFromCSVLine(generator.getExportLine(i),
          ExportCSVGenerator.getMaxFieldsCount(generator.getHeaderLine()));
      Assert.assertEquals(ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT, fields.size());
    }
    // The moves-only case (no recognized method) must also trigger the new format.
    ExportResult movesOnly = new ExportResult("3x3x3", "Default", 7000, 1700000002000L, false, false, null, null, null);
    movesOnly.setSmartcubeMoves(MOVES);
    Assert.assertEquals(ExportCSVGenerator.SMARTCUBE_CSV_HEADER_LINE,
        new ExportCSVGenerator(Arrays.asList(movesOnly)).getHeaderLine());
  }

  // Writing a cube-recorded solve into the legacy layout would silently drop its data — the
  // converter must refuse instead; the generator's format choice makes this unreachable.
  @Test
  public void testWritingSmartcubeDataInTheLegacyFormatIsRefused() {
    try {
      ExportResultConverter.toCSVLine(cubeResult("x"), false);
      Assert.fail("Expected the legacy format to refuse smart cube data");
    } catch (IllegalArgumentException expected) {
    }
  }

  // ---- The gyro track ------------------------------------------------------------------------
  // It rides at the end of the moves field rather than in a column of its own, so no header changes.

  // The layout is the contract, and adding the track must not move a single field.
  @Test
  public void testTheGyroTrackRidesInTheMovesFieldAndMovesNothing() {
    String track = gyroTrack();
    String csvLine = ExportResultConverter.toCSVLine(cubeResultWithGyro("a comment"), true);
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(csvLine, ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT);
    Assert.assertEquals(ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT, fields.size());
    Assert.assertEquals("CFOP", fields.get(9));
    Assert.assertEquals(MOVES + " gyro:" + track, fields.get(10));
    Assert.assertEquals(STEPS, fields.get(11));
    Assert.assertEquals("2", fields.get(12));
    Assert.assertEquals("", fields.get(13));
    Assert.assertEquals("a comment", fields.get(14));
  }

  // The point of the work: a track lost in a round trip cannot be recorded again after the fact.
  @Test
  public void testTheGyroTrackSurvivesAFullRoundTrip() throws Exception {
    ExportResult original = cubeResultWithGyro("full, record");
    ExportResult imported = ExportResultConverter.fromCSVLine(null,
        ExportResultConverter.toCSVLine(original, true), ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT);
    Assert.assertEquals(MOVES, imported.getSmartcubeMoves());
    Assert.assertEquals(original.getSmartcubeGyroTrack(), imported.getSmartcubeGyroTrack());
    Assert.assertEquals(CubeMethod.CFOP, imported.getSmartcubeMethod());
    Assert.assertEquals(STEPS, SolveStepsFormat.format(imported.getSmartcubeSteps()));
    Assert.assertEquals("full, record", imported.getComment());
  }

  // What lets the track share the field: a reader that knows nothing of it still sees the same moves.
  @Test
  public void testTheTrackIsInvisibleToTheMovesParser() {
    String packed = ExportResultConverter.packMoves("[y] " + MOVES, gyroTrack());
    Assert.assertEquals(SolveMovesFormat.parse(MOVES).size(), SolveMovesFormat.parse(packed).size());
    Assert.assertEquals("y", SolveMovesFormat.pickupOf(packed)); // and the grip still reads
  }

  // A track is smart-cube data: it picks the layout on its own, and the short one must refuse it.
  @Test
  public void testAGyroTrackIsSmartcubeDataForTheFormatChoice() {
    Assert.assertEquals(ExportCSVGenerator.SMARTCUBE_CSV_HEADER_LINE,
        new ExportCSVGenerator(Arrays.asList(cubeResultWithGyro(null))).getHeaderLine());
    try {
      ExportResultConverter.toCSVLine(cubeResultWithGyro("x"), false);
      Assert.fail("Expected the legacy format to refuse a gyro track");
    } catch (IllegalArgumentException expected) {
    }
  }

  // Base64 has no comma and no quote, so the packed field cannot desync the splitter or the grouper.
  @Test
  public void testThePackedMovesFieldRespectsTheCsvInvariants() {
    String csvLine = ExportResultConverter.toCSVLine(cubeResultWithGyro("multi\nline, \" comment"), true);
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(csvLine, ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT);
    Assert.assertEquals(ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT, fields.size());
    Assert.assertEquals(-1, gyroTrack().indexOf(','));
    Assert.assertEquals(-1, gyroTrack().indexOf('"'));
  }

  // A bare moves field is every file written before the track, and imports as a solve without one.
  @Test
  public void testFilesPredatingTheTrackImportWithNone() {
    ExportResult result = new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, null, null);
    ExportResultConverter.applySmartcubeFields(result, "CFOP", MOVES, STEPS, "2");
    Assert.assertEquals(MOVES, result.getSmartcubeMoves());
    Assert.assertNull(result.getSmartcubeGyroTrack());
  }

  // Half a record is rejected whole, as everywhere else in this converter.
  @Test
  public void testCorruptGyroTracksAreRejected() {
    assertSmartcubeRejected("CFOP", MOVES + " gyro:not a track", STEPS, "");        // unreadable
    assertSmartcubeRejected("CFOP", MOVES + " gyro:" + gyroTrack().substring(0, 8), STEPS, ""); // truncated
    assertSmartcubeRejected("", "gyro:" + gyroTrack(), "", "");                     // no solution to describe
  }

  // ---- Importing the older formats through the new parser ------------------------------------
  // fromCSVLine's happy path runs without Android: the numeric time parse is context-free (the
  // context is only needed for localized DNF/NA times and to word error messages).

  @Test
  public void testOldestEightColumnLinesStillImport() throws Exception {
    String date = FormatterService.INSTANCE.formatExportDateTime(1700000000000L);
    ExportResult result = ExportResultConverter.fromCSVLine(null,
        "3x3x3,Default,12.345," + date + ",,n,n,R U R' F2", ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT);
    Assert.assertEquals("3x3x3", result.getCubeTypeName());
    Assert.assertEquals("Default", result.getSolveTypeName());
    Assert.assertEquals(12345, result.getTime());
    Assert.assertEquals(1700000000000L, result.getTimestamp());
    Assert.assertEquals("R U R' F2", result.getScramble());
    Assert.assertNull(result.getScrambleTypeName());
    Assert.assertNull(result.getComment());
    assertNoSmartcubeData(result);
  }

  @Test
  public void testNineColumnLinesStillImport() throws Exception {
    String date = FormatterService.INSTANCE.formatExportDateTime(1700000000000L);
    ExportResult result = ExportResultConverter.fromCSVLine(null,
        "3x3x3,OH,1:05.120," + date + ",,y,n,,\"R U R'\"", ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT);
    Assert.assertEquals(65120, result.getTime());
    Assert.assertTrue(result.isPlusTwo());
    Assert.assertEquals("R U R'", result.getScramble());
    Assert.assertNull(result.getComment());
    assertNoSmartcubeData(result);
  }

  @Test
  public void testTenColumnLinesWithStepsAndCommentStillImport() throws Exception {
    String date = FormatterService.INSTANCE.formatExportDateTime(1700000000000L);
    ExportResult result = ExportResultConverter.fromCSVLine(null,
        "3x3x3,Steps,10.000," + date + ",\"cross=2.000|rest=8.000\",n,y,,\"R U R'\",a note, with commas",
        ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT);
    Assert.assertTrue(result.isBlindType());
    Assert.assertEquals(2, result.getStepsNames().length);
    Assert.assertEquals("cross", result.getStepsNames()[0]);
    Assert.assertEquals(Long.valueOf(2000), result.getStepsTimes()[0]);
    Assert.assertEquals(Long.valueOf(8000), result.getStepsTimes()[1]);
    Assert.assertEquals("a note, with commas", result.getComment());
    assertNoSmartcubeData(result);
  }

  // A solve no cube drove must survive a full write/read round-trip through the short format,
  // awkward scramble and comment included.
  @Test
  public void testNonCubeExportImportsIdentically() throws Exception {
    ExportResult original = new ExportResult("Square-1", "Default", 65120, 1700000000000L, true, false,
        null, "(1,0) / (-3,0) /", "note, with \"quote\"\nand a newline");
    ExportResult imported = ExportResultConverter.fromCSVLine(null,
        ExportResultConverter.toCSVLine(original, false), ExportCSVGenerator.MAX_FIELDS_COUNT);
    Assert.assertEquals(original.getCubeTypeName(), imported.getCubeTypeName());
    Assert.assertEquals(original.getSolveTypeName(), imported.getSolveTypeName());
    Assert.assertEquals(original.getTime(), imported.getTime());
    Assert.assertEquals(original.getTimestamp(), imported.getTimestamp());
    Assert.assertEquals(original.isPlusTwo(), imported.isPlusTwo());
    Assert.assertEquals(original.getScramble(), imported.getScramble());
    Assert.assertEquals(original.getComment(), imported.getComment());
    assertNoSmartcubeData(imported);
  }

  // And the same proof for the new format, smart cube record included.
  @Test
  public void testSmartcubeExportImportsIdenticallyThroughFromCSVLine() throws Exception {
    ExportResult original = cubeResult("full, record");
    ExportResult imported = ExportResultConverter.fromCSVLine(null,
        ExportResultConverter.toCSVLine(original, true), ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT);
    Assert.assertEquals(original.getTime(), imported.getTime());
    Assert.assertEquals(original.getScramble(), imported.getScramble());
    Assert.assertEquals(original.getComment(), imported.getComment());
    Assert.assertEquals(CubeMethod.CFOP, imported.getSmartcubeMethod());
    Assert.assertEquals(MOVES, imported.getSmartcubeMoves());
    Assert.assertEquals(STEPS, SolveStepsFormat.format(imported.getSmartcubeSteps()));
    Assert.assertEquals(Integer.valueOf(2), imported.getSmartcubeStoppedStep());
  }

  private void assertNoSmartcubeData(ExportResult result) {
    Assert.assertNull(result.getSmartcubeMethod());
    Assert.assertNull(result.getSmartcubeMoves());
    Assert.assertNull(result.getSmartcubeSteps());
    Assert.assertNull(result.getSmartcubeStoppedStep());
  }

  // Hand-edited whitespace around the smart cube fields must not corrupt an import.
  @Test
  public void testWhitespaceAroundSmartcubeFieldsIsTolerated() {
    ExportResult reimported = new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, null, null);
    ExportResultConverter.applySmartcubeFields(reimported, " CFOP ", " " + MOVES + " ", " " + STEPS + " ", " 2 ");
    Assert.assertEquals(CubeMethod.CFOP, reimported.getSmartcubeMethod());
    Assert.assertEquals(MOVES, reimported.getSmartcubeMoves());
    Assert.assertEquals(Integer.valueOf(2), reimported.getSmartcubeStoppedStep());
  }

  private void assertSmartcubeRejected(String method, String moves, String steps, String stopped) {
    ExportResult result = new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, null, null);
    try {
      ExportResultConverter.applySmartcubeFields(result, method, moves, steps, stopped);
      Assert.fail("Expected rejection of method=" + method + " moves=" + moves + " steps=" + steps + " stopped=" + stopped);
    } catch (IllegalArgumentException expected) {
    }
  }

  // ---- The time a DNF replaced ---------------------------------------------------------------
  // A DNF now carries the time it took the place of, so the mark can be undone after a round-trip
  // through a file. The time column still reads "DNF"; this is the extra column beside it.

  // A DNF's own time column reads "DNF", a localized string the writer needs Android for, so
  // these exercise the pre-DNF column on solves whose time column is plain. Its own value is
  // always a real time, which is exactly why it stays context-free.
  private static ExportResult dnfResult() {
    return new ExportResult("3x3x3", "Default", -1, 1700000000000L, false, false, null, "R U R'", null);
  }

  private static String timeBeforeDnfFieldOf(ExportResult result, boolean withSmartcube) {
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(
        ExportResultConverter.toCSVLine(result, withSmartcube),
        withSmartcube ? ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT : ExportCSVGenerator.MAX_FIELDS_COUNT);
    return fields.get(withSmartcube ? 13 : 9);
  }

  // The column sits between the last smart cube field and the comment, in both layouts.
  @Test
  public void testTimeBeforeDnfSitsBeforeTheComment() {
    ExportResult plain = new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, "R U R'", "note");
    plain.setTimeBeforeDnf(12345L);
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(
        ExportResultConverter.toCSVLine(plain, false), ExportCSVGenerator.MAX_FIELDS_COUNT);
    Assert.assertEquals(ExportCSVGenerator.MAX_FIELDS_COUNT, fields.size());
    Assert.assertEquals("12.345", fields.get(9));
    Assert.assertEquals("note", fields.get(10));

    ExportResult withCube = cubeResult("note");
    withCube.setTimeBeforeDnf(12345L);
    fields = ExportResultConverter.getFieldsFromCSVLine(
        ExportResultConverter.toCSVLine(withCube, true), ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT);
    Assert.assertEquals(ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT, fields.size());
    Assert.assertEquals("12.345", fields.get(13));
    Assert.assertEquals("note", fields.get(14));
  }

  // Full millisecond precision, same as the time column: restoring a rounded time would quietly
  // change the solve.
  @Test
  public void testTimeBeforeDnfKeepsMillisecondPrecision() {
    ExportResult result = new ExportResult("3x3x3", "Default", 5000, 0, false, false, null, null, null);
    result.setTimeBeforeDnf(2005L); // displays as "2.01" with high precision off
    Assert.assertEquals("2.005", timeBeforeDnfFieldOf(result, false));

    ExportResult reimported = dnfResult();
    ExportResultConverter.applyTimeBeforeDnf(reimported, "2.005");
    Assert.assertEquals(Long.valueOf(2005), reimported.getTimeBeforeDnf());
  }

  // Written by the exporter, read back by the importer, in both layouts.
  @Test
  public void testTimeBeforeDnfRoundTrips() {
    for (boolean withSmartcube : new boolean[] { false, true }) {
      ExportResult original = withSmartcube
          ? cubeResult("note")
          : new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, "R U R'", "note");
      original.setTimeBeforeDnf(65120L);

      ExportResult reimported = dnfResult();
      ExportResultConverter.applyTimeBeforeDnf(reimported, timeBeforeDnfFieldOf(original, withSmartcube));
      Assert.assertEquals(original.getTimeBeforeDnf(), reimported.getTimeBeforeDnf());
    }
  }

  // A minute-long time uses the same "1:05.120" shape as the time column.
  @Test
  public void testTimeBeforeDnfHandlesMinutes() {
    ExportResult result = dnfResult();
    ExportResultConverter.applyTimeBeforeDnf(result, "1:05.120");
    Assert.assertEquals(Long.valueOf(65120), result.getTimeBeforeDnf());
  }

  // The ordinary case: no DNF, or a DNF with nothing to restore. The empty field stays null.
  @Test
  public void testAnEmptyTimeBeforeDnfStaysNull() {
    ExportResult solved = new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, null, null);
    ExportResultConverter.applyTimeBeforeDnf(solved, "");
    Assert.assertNull(solved.getTimeBeforeDnf());

    ExportResult legacyDnf = new ExportResult("3x3x3", "Default", -1, 1700000000000L, false, false, null, null, null);
    ExportResultConverter.applyTimeBeforeDnf(legacyDnf, "  ");
    Assert.assertNull(legacyDnf.getTimeBeforeDnf()); // a DNF from before the column was kept
  }

  @Test
  public void testWhitespaceAroundTimeBeforeDnfIsTolerated() {
    ExportResult result = dnfResult();
    ExportResultConverter.applyTimeBeforeDnf(result, " 12.345 ");
    Assert.assertEquals(Long.valueOf(12345), result.getTimeBeforeDnf());
  }

  // The cross-field invariants: only a DNF can hold a time to restore, and only a real one.
  @Test
  public void testCorruptTimeBeforeDnfIsRejected() {
    assertTimeBeforeDnfRejected(5000, "12.345");  // a time to restore on a solve that finished
    assertTimeBeforeDnfRejected(-1, "hello");     // not a time at all
    assertTimeBeforeDnfRejected(-1, "0.000");     // nothing worth restoring
    assertTimeBeforeDnfRejected(-1, "-1");        // the sentinel itself
    assertTimeBeforeDnfRejected(-1, "12345");     // no decimals: not the exported shape
  }

  private void assertTimeBeforeDnfRejected(long time, String timeBeforeDnf) {
    ExportResult result = new ExportResult("3x3x3", "Default", time, 1700000000000L, false, false, null, null, null);
    try {
      ExportResultConverter.applyTimeBeforeDnf(result, timeBeforeDnf);
      Assert.fail("Expected rejection of time=" + time + " timeBeforeDnf=" + timeBeforeDnf);
    } catch (IllegalArgumentException expected) {
    }
  }

  // A file written before the column existed has no field to read it from, and its DNFs simply
  // have nothing to restore — importing one must not invent a time or shift the comment.
  @Test
  public void testFilesPredatingTheColumnImportWithNothingToRestore() throws Exception {
    String date = FormatterService.INSTANCE.formatExportDateTime(1700000000000L);
    ExportResult result = ExportResultConverter.fromCSVLine(null,
        "3x3x3,Default,12.345," + date + ",,n,n,,\"R U R'\",a note, with commas",
        ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT);
    Assert.assertNull(result.getTimeBeforeDnf());
    Assert.assertEquals("a note, with commas", result.getComment());

    // And the same for the 14-column smart cube layout, whose comment also sits one field earlier.
    result = ExportResultConverter.fromCSVLine(null,
        "3x3x3,Default,29.376," + date + ",,n,n,,\"R U R'\",CFOP," + MOVES + "," + STEPS + ",2,a note, with commas",
        ExportCSVGenerator.LEGACY_SMARTCUBE_MAX_FIELDS_COUNT);
    Assert.assertNull(result.getTimeBeforeDnf());
    Assert.assertEquals("a note, with commas", result.getComment());
    Assert.assertEquals(CubeMethod.CFOP, result.getSmartcubeMethod());
    Assert.assertEquals(Integer.valueOf(2), result.getSmartcubeStoppedStep());
  }

  // Every layout the app has written, read back through the current parser under its own header.
  // This is the back-compat matrix: a file exported by any past version must still import, with
  // every field landing where that version put it.
  @Test
  public void testEveryPastLayoutStillImports() throws Exception {
    String date = FormatterService.INSTANCE.formatExportDateTime(1700000000000L);
    String common = "3x3x3,Default,12.345," + date + ",,n,n";

    // 8 columns: no scramble type, no comment.
    ExportResult r = ExportResultConverter.fromCSVLine(null, common + ",\"R U R'\"",
        ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT);
    assertCommonFields(r);
    Assert.assertNull(r.getScrambleTypeName());
    Assert.assertNull(r.getComment());

    // 9 columns: the scramble type arrives.
    r = ExportResultConverter.fromCSVLine(null, common + ",last_layer,\"R U R'\"",
        ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT);
    assertCommonFields(r);
    Assert.assertEquals("last_layer", r.getScrambleTypeName());
    Assert.assertNull(r.getComment());

    // 10 columns: the comment arrives, and takes the commas with it.
    r = ExportResultConverter.fromCSVLine(null, common + ",,\"R U R'\",a note, with commas",
        ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT);
    assertCommonFields(r);
    Assert.assertEquals("a note, with commas", r.getComment());
    assertNoSmartcubeData(r);

    // 14 columns: the smart cube block arrives, comment still last.
    r = ExportResultConverter.fromCSVLine(null,
        common + ",,\"R U R'\",CFOP," + MOVES + "," + STEPS + ",2,a note, with commas",
        ExportCSVGenerator.LEGACY_SMARTCUBE_MAX_FIELDS_COUNT);
    assertCommonFields(r);
    Assert.assertEquals("a note, with commas", r.getComment());
    Assert.assertEquals(CubeMethod.CFOP, r.getSmartcubeMethod());
    Assert.assertEquals(MOVES, r.getSmartcubeMoves());
    Assert.assertEquals(STEPS, SolveStepsFormat.format(r.getSmartcubeSteps()));
    Assert.assertEquals(Integer.valueOf(2), r.getSmartcubeStoppedStep());
    Assert.assertNull(r.getTimeBeforeDnf()); // the column did not exist yet
  }

  private void assertCommonFields(ExportResult result) {
    Assert.assertEquals("3x3x3", result.getCubeTypeName());
    Assert.assertEquals("Default", result.getSolveTypeName());
    Assert.assertEquals(12345, result.getTime());
    Assert.assertEquals(1700000000000L, result.getTimestamp());
    Assert.assertEquals("R U R'", result.getScramble());
    Assert.assertFalse(result.isPlusTwo());
    Assert.assertFalse(result.isBlindType());
  }

  // A line must carry exactly the columns the layout its header announced calls for. Accepting a
  // short one and reading it as whichever layout happens to have that many columns would shift
  // every field past the missing one: a 15-column line short by one looks exactly like the
  // 14-column layout, and its pre-DNF time would be imported as the solve's comment.
  @Test
  public void testALineMustMatchTheLayoutItsHeaderAnnounced() {
    // The three oldest layouts share a fold count, so there the line's own count picks between them.
    for (int fieldsCount = 8; fieldsCount <= ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT; fieldsCount++) {
      Assert.assertTrue("legacy layout with " + fieldsCount + " fields",
          ExportResultConverter.isFieldsCountValid(fieldsCount, ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT));
    }
    Assert.assertFalse(ExportResultConverter.isFieldsCountValid(7, ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT));

    // Every layout since is exact — one field short is damage, not an older layout.
    for (int maxFieldsCount : new int[] { ExportCSVGenerator.MAX_FIELDS_COUNT,
        ExportCSVGenerator.LEGACY_SMARTCUBE_MAX_FIELDS_COUNT, ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT }) {
      Assert.assertTrue(ExportResultConverter.isFieldsCountValid(maxFieldsCount, maxFieldsCount));
      Assert.assertFalse(ExportResultConverter.isFieldsCountValid(maxFieldsCount - 1, maxFieldsCount));
      Assert.assertFalse(ExportResultConverter.isFieldsCountValid(ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT, maxFieldsCount));
    }
    // The case that motivates the rule: 14 fields is a whole valid layout, but not under a 15 header.
    Assert.assertFalse(ExportResultConverter.isFieldsCountValid(
        ExportCSVGenerator.LEGACY_SMARTCUBE_MAX_FIELDS_COUNT, ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT));
  }

  // And the parser applies that rule rather than reading the damaged line anyway.
  @Test
  public void testTheParserRejectsALineShortOfItsLayout() {
    String date = FormatterService.INSTANCE.formatExportDateTime(1700000000000L);
    String shortLine = "3x3x3,Default,12.345," + date + ",,n,n,,\"R U R'\",CFOP,"
        + MOVES + "," + STEPS + ",2,a note"; // 14 fields, under the 15-column header
    try {
      ExportResultConverter.fromCSVLine(null, shortLine, ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT);
      Assert.fail("Expected a line short of its layout to be rejected");
    } catch (Exception expected) {
      // CSVFormatException in the app; wording it needs the context this test does not have.
    }
  }

  // The new column must not disturb the fields around it: a full line still parses end to end.
  @Test
  public void testTheNewColumnLeavesEveryOtherFieldWhereItWas() throws Exception {
    String date = FormatterService.INSTANCE.formatExportDateTime(1700000000000L);
    // The time column says 29.376, so this solve is not a DNF and its pre-DNF field is empty —
    // the layout is what is under test, and everything after the new column must still line up.
    ExportResult result = ExportResultConverter.fromCSVLine(null,
        "3x3x3,Default,29.376," + date + ",,n,n,,\"R U R'\",CFOP," + MOVES + "," + STEPS + ",2,,a note, with commas",
        ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT);
    Assert.assertEquals(29376, result.getTime());
    Assert.assertEquals("R U R'", result.getScramble());
    Assert.assertEquals(CubeMethod.CFOP, result.getSmartcubeMethod());
    Assert.assertEquals(MOVES, result.getSmartcubeMoves());
    Assert.assertEquals(STEPS, SolveStepsFormat.format(result.getSmartcubeSteps()));
    Assert.assertEquals(Integer.valueOf(2), result.getSmartcubeStoppedStep());
    Assert.assertEquals("a note, with commas", result.getComment());
    Assert.assertNull(result.getTimeBeforeDnf());
  }
}
