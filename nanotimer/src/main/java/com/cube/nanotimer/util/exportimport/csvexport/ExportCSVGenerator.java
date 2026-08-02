package com.cube.nanotimer.util.exportimport.csvexport;

import com.cube.nanotimer.vo.ExportResult;

import java.util.Arrays;
import java.util.List;

public class ExportCSVGenerator implements CSVGenerator {

  // The file's columns, as the blocks they were added in. A layout is the common columns, then
  // whichever optional blocks it carries, then the comment — which stays last because it is
  // unquoted free text whose commas fold past the final separator. The format has only ever grown
  // by adding a block before the comment, so a new column means extending a block here and adding
  // the layout it produces to KNOWN_HEADER_LINES; no layout is ever spelled out twice.
  private static final String COMMON_COLUMNS = "cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble";
  private static final String SMARTCUBE_COLUMNS = "smartcubeMethod,smartcubeMoves,smartcubeSteps,smartcubeStopped";
  private static final String TIME_BEFORE_DNF_COLUMN = "timeBeforeDnf";
  private static final String COMMENT_COLUMN = "comment";

  // Written whenever no exported solve carries smart-cube data.
  public static final String CSV_HEADER_LINE = header(COMMON_COLUMNS, TIME_BEFORE_DNF_COLUMN, COMMENT_COLUMN);
  // Written once a solve carries smart-cube data — a file is one layout throughout.
  public static final String SMARTCUBE_CSV_HEADER_LINE = header(COMMON_COLUMNS, SMARTCUBE_COLUMNS, TIME_BEFORE_DNF_COLUMN, COMMENT_COLUMN);
  // The layout that preceded the pre-DNF column, whose lines put the comment one field earlier.
  public static final String LEGACY_SMARTCUBE_CSV_HEADER_LINE = header(COMMON_COLUMNS, SMARTCUBE_COLUMNS, COMMENT_COLUMN);

  /**
   * Every layout the app has written, oldest first. The last two are what it writes now; the rest
   * are kept only so the files they describe stay importable.
   */
  public static final List<String> KNOWN_HEADER_LINES = Arrays.asList(
      "cubetype,solvetype,time,date,steps,plustwo,blind,scramble", // before a scramble had a type
      COMMON_COLUMNS,                                              // before a solve had a comment
      header(COMMON_COLUMNS, COMMENT_COLUMN),
      LEGACY_SMARTCUBE_CSV_HEADER_LINE,
      CSV_HEADER_LINE,
      SMARTCUBE_CSV_HEADER_LINE);

  public static final int SMARTCUBE_MAX_FIELDS_COUNT = columnsCount(SMARTCUBE_CSV_HEADER_LINE);
  public static final int MAX_FIELDS_COUNT = columnsCount(CSV_HEADER_LINE);
  public static final int LEGACY_SMARTCUBE_MAX_FIELDS_COUNT = columnsCount(LEGACY_SMARTCUBE_CSV_HEADER_LINE);
  public static final int LEGACY_MAX_FIELDS_COUNT = columnsCount(header(COMMON_COLUMNS, COMMENT_COLUMN));

  private static String header(String... columnBlocks) {
    StringBuilder sb = new StringBuilder();
    for (String block : columnBlocks) {
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append(block);
    }
    return sb.toString();
  }

  private static int columnsCount(String headerLine) {
    return headerLine.split(",").length;
  }

  /**
   * How many fields a line of this file may hold — commas beyond that belong to the comment.
   * A layout folds at the column count it declares, but never below the ten of the layout that
   * first carried a comment: the two older ones wrote fewer columns, yet files written by them
   * have always been read with the comment folding at the tenth.
   */
  public static int getMaxFieldsCount(String headerLine) {
    return Math.max(columnsCount(headerLine), LEGACY_MAX_FIELDS_COUNT);
  }

  private final List<ExportResult> results;
  private final boolean smartcubeFormat;

  public ExportCSVGenerator(List<ExportResult> results) {
    this.results = results;
    boolean hasSmartcubeData = false;
    for (ExportResult result : results) {
      hasSmartcubeData = hasSmartcubeData
          || result.getSmartcubeMoves() != null || result.getSmartcubeGyroTrack() != null
          || result.hasSmartcubeBreakdown();
    }
    this.smartcubeFormat = hasSmartcubeData;
  }

  @Override
  public String getHeaderLine() {
    return smartcubeFormat ? SMARTCUBE_CSV_HEADER_LINE : CSV_HEADER_LINE;
  }

  @Override
  public String getExportLine(int n) {
    if (n < 0 || n >= results.size()) {
      return null;
    }
    ExportResult line = results.get(n);
    return ExportResultConverter.toCSVLine(line, smartcubeFormat);
  }

  public static boolean isHeaderLegit(String parHeaderLine) {
    for (String knownHeaderLine : KNOWN_HEADER_LINES) {
      if (knownHeaderLine.equalsIgnoreCase(parHeaderLine)) {
        return true;
      }
    }
    return false;
  }

}
