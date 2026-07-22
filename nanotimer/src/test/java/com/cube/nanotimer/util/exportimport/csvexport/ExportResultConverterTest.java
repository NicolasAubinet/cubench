package com.cube.nanotimer.util.exportimport.csvexport;

import com.cube.nanotimer.cube.SolveStepsFormat;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.exportimport.csvimport.workers.CSVLineGrouper;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.ExportResult;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(csvLine, 10);
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
    Assert.assertEquals(comment, ExportResultConverter.decodeComment(fields.get(9)));
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

  // ---- Smart cube section (14-column format) -------------------------------------------------

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

  // The line layout is the contract: 14 columns, smart cube fields in 9-12, comment last.
  @Test
  public void testSmartcubeFieldsSitBeforeTheComment() {
    String csvLine = ExportResultConverter.toCSVLine(cubeResult("a comment"), true);
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(csvLine, ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT);
    Assert.assertEquals(ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT, fields.size());
    Assert.assertEquals("CFOP", fields.get(9));
    Assert.assertEquals(MOVES, fields.get(10));
    Assert.assertEquals(STEPS, fields.get(11));
    Assert.assertEquals("2", fields.get(12));
    Assert.assertEquals("a comment", fields.get(13));
  }

  // The header must announce as many columns as the lines carry, or the comment fold desyncs.
  @Test
  public void testHeaderColumnCountMatchesTheLineLayout() {
    Assert.assertEquals(ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT,
        ExportCSVGenerator.SMARTCUBE_CSV_HEADER_LINE.split(",").length);
    Assert.assertEquals(ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT,
        ExportCSVGenerator.getMaxFieldsCount(ExportCSVGenerator.SMARTCUBE_CSV_HEADER_LINE));
    Assert.assertEquals(ExportCSVGenerator.MAX_FIELDS_COUNT,
        ExportCSVGenerator.CSV_HEADER_LINE.split(",").length);
    Assert.assertEquals(ExportCSVGenerator.MAX_FIELDS_COUNT,
        ExportCSVGenerator.getMaxFieldsCount(ExportCSVGenerator.CSV_HEADER_LINE));
    Assert.assertTrue(ExportCSVGenerator.isHeaderLegit(ExportCSVGenerator.CSV_HEADER_LINE));
    for (String oldHeader : ExportCSVGenerator.OLD_CSV_HEADER_LINES) {
      Assert.assertEquals(ExportCSVGenerator.MAX_FIELDS_COUNT,
          ExportCSVGenerator.getMaxFieldsCount(oldHeader));
      Assert.assertTrue(ExportCSVGenerator.isHeaderLegit(oldHeader)); // old exports must stay importable
    }
    Assert.assertTrue(ExportCSVGenerator.isHeaderLegit(ExportCSVGenerator.SMARTCUBE_CSV_HEADER_LINE));
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
    Assert.assertEquals("PB, with a comma", ExportResultConverter.decodeComment(fields.get(13)));
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
    Assert.assertEquals("note", fields.get(13));
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

  // Old 10-column lines keep parsing under their own header's fold count — the smart cube
  // columns must not have shifted anything for them.
  @Test
  public void testLegacyLinesKeepTheirLayout() {
    ExportResult result = new ExportResult("3x3x3", "Default", 5000, 1700000000000L, false, false, null, "R U R'", "old, style");
    String legacyLine = ExportResultConverter.toCSVLine(result, false);
    List<String> fields = ExportResultConverter.getFieldsFromCSVLine(legacyLine, ExportCSVGenerator.MAX_FIELDS_COUNT);
    Assert.assertEquals(ExportCSVGenerator.MAX_FIELDS_COUNT, fields.size());
    Assert.assertEquals("old, style", ExportResultConverter.decodeComment(fields.get(9)));
  }

  // A history no cube ever touched must export exactly the previous format: legacy header,
  // 10-column lines. Non-smart-cube users see no change at all.
  @Test
  public void testExportWithoutSmartcubeDataKeepsTheLegacyFormat() {
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

  // ---- Importing the older formats through the new parser ------------------------------------
  // fromCSVLine's happy path runs without Android: the numeric time parse is context-free (the
  // context is only needed for localized DNF/NA times and to word error messages).

  @Test
  public void testOldestEightColumnLinesStillImport() throws Exception {
    String date = FormatterService.INSTANCE.formatExportDateTime(1700000000000L);
    ExportResult result = ExportResultConverter.fromCSVLine(null,
        "3x3x3,Default,12.345," + date + ",,n,n,R U R' F2", ExportCSVGenerator.MAX_FIELDS_COUNT);
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
        "3x3x3,OH,1:05.120," + date + ",,y,n,,\"R U R'\"", ExportCSVGenerator.MAX_FIELDS_COUNT);
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
        ExportCSVGenerator.MAX_FIELDS_COUNT);
    Assert.assertTrue(result.isBlindType());
    Assert.assertEquals(2, result.getStepsNames().length);
    Assert.assertEquals("cross", result.getStepsNames()[0]);
    Assert.assertEquals(Long.valueOf(2000), result.getStepsTimes()[0]);
    Assert.assertEquals(Long.valueOf(8000), result.getStepsTimes()[1]);
    Assert.assertEquals("a note, with commas", result.getComment());
    assertNoSmartcubeData(result);
  }

  // The definitive backward-compatibility proof: a line built exactly as the pre-update app
  // exported it (no smart cube columns) imports identically through the new parser.
  @Test
  public void testPreUpdateExportImportsIdentically() throws Exception {
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
}
