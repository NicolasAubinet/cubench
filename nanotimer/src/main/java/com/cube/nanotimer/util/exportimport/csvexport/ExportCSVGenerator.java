package com.cube.nanotimer.util.exportimport.csvexport;

import com.cube.nanotimer.vo.ExportResult;

import java.util.Arrays;
import java.util.List;

public class ExportCSVGenerator implements CSVGenerator {

  // Written whenever no exported solve carries smart-cube data: a user without a smart cube
  // keeps producing files identical to previous app versions.
  public static final String CSV_HEADER_LINE = "cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble,comment";
  // Used once a solve carries smart-cube data. Its fields sit BEFORE the comment: the comment is
  // unquoted free text whose commas fold past the last separator, so it must stay the last field.
  public static final String SMARTCUBE_CSV_HEADER_LINE = "cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble,smartcubeMethod,smartcubeMoves,smartcubeSteps,smartcubeStopped,comment";
  public static final List<String> OLD_CSV_HEADER_LINES = Arrays.asList(
    "cubetype,solvetype,time,date,steps,plustwo,blind,scramble",
    "cubetype,solvetype,time,date,steps,plustwo,blind,scrambleType,scramble");

  public static final int SMARTCUBE_MAX_FIELDS_COUNT = 14;
  public static final int MAX_FIELDS_COUNT = 10;

  /** How many fields a line of this file may hold — commas beyond that belong to the comment. */
  public static int getMaxFieldsCount(String headerLine) {
    return headerLine.toLowerCase().equals(SMARTCUBE_CSV_HEADER_LINE.toLowerCase())
        ? SMARTCUBE_MAX_FIELDS_COUNT : MAX_FIELDS_COUNT;
  }

  private final List<ExportResult> results;
  private final boolean smartcubeFormat;

  public ExportCSVGenerator(List<ExportResult> results) {
    this.results = results;
    boolean hasSmartcubeData = false;
    for (ExportResult result : results) {
      hasSmartcubeData = hasSmartcubeData
          || result.getSmartcubeMoves() != null || result.hasSmartcubeBreakdown();
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
    boolean locFoundValidHeader = false;

    if (parHeaderLine.toLowerCase().equals(ExportCSVGenerator.CSV_HEADER_LINE.toLowerCase())
        || parHeaderLine.toLowerCase().equals(ExportCSVGenerator.SMARTCUBE_CSV_HEADER_LINE.toLowerCase())) {
      locFoundValidHeader = true;
    }

    if (!locFoundValidHeader) {
      for (String locOldCsvHeaderLine : OLD_CSV_HEADER_LINES) {
        if (locOldCsvHeaderLine.toLowerCase().equals(parHeaderLine.toLowerCase())) {
          locFoundValidHeader = true;
        }
      }
    }

    return locFoundValidHeader;
  }

}
