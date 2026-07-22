package com.cube.nanotimer.util.exportimport.csvexport;

import android.content.Context;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SolveMovesFormat;
import com.cube.nanotimer.cube.SolveStepsFormat;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.exportimport.CSVFormatException;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.ExportResult;
import com.cube.nanotimer.vo.SolveStep;

import java.util.ArrayList;
import java.util.List;

public class ExportResultConverter {

  static String encodeComment(String comment) {
    return comment
      .replace("\\", "\\\\")
      .replace("\n", "\\n")
      .replace("\"", "\\q");
  }

  static String decodeComment(String comment) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < comment.length(); i++) {
      char c = comment.charAt(i);
      if (c == '\\' && i + 1 < comment.length()) {
        char next = comment.charAt(++i);
        switch (next) {
          case 'n': sb.append('\n'); break;
          case 'q': sb.append('"'); break;
          case '\\': sb.append('\\'); break;
          default: sb.append('\\').append(next); break;
        }
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * @param withSmartcubeFields whether the file carries the four smart-cube columns; a whole file
   *     is one format or the other, so this follows the header the generator chose
   */
  public static String toCSVLine(ExportResult result, boolean withSmartcubeFields) {
    if (!withSmartcubeFields
        && (result.getSmartcubeMoves() != null || result.hasSmartcubeBreakdown())) {
      // Writing this solve without its smart-cube columns would silently drop recorded data.
      throw new IllegalArgumentException("Solve carries smart cube data the format cannot hold");
    }
    StringBuilder sb = new StringBuilder();
    sb.append(result.getCubeTypeName());
    sb.append(",");
    sb.append(escapeString(result.getSolveTypeName()));
    sb.append(",");
    sb.append(FormatterService.INSTANCE.formatSolveTime(result.getTime(), null, true));
    sb.append(",");
    sb.append(FormatterService.INSTANCE.formatExportDateTime(result.getTimestamp()));
    sb.append(",");
    if (result.hasSteps()) {
      sb.append(formatSteps(result.getStepsNames(), result.getStepsTimes()));
    }
    sb.append(",");
    sb.append(result.isPlusTwo() ? "y" : "n");
    sb.append(",");
    sb.append(result.isBlindType() ? "y" : "n");
    sb.append(",");
    if (result.getScrambleTypeName() != null) {
      sb.append(result.getScrambleTypeName());
    }
    sb.append(",");
    if (result.getScramble() != null) {
      sb.append(escapeString(result.getScramble()));
    }
    if (withSmartcubeFields) {
      sb.append(",");
      if (result.getSmartcubeMethod() != null) {
        sb.append(result.getSmartcubeMethod().getCode());
      }
      sb.append(",");
      if (result.getSmartcubeMoves() != null) {
        sb.append(result.getSmartcubeMoves());
      }
      sb.append(",");
      if (result.hasSmartcubeBreakdown()) {
        sb.append(SolveStepsFormat.format(result.getSmartcubeSteps()));
      }
      sb.append(",");
      if (result.getSmartcubeStoppedStep() != null) {
        sb.append(result.getSmartcubeStoppedStep());
      }
    }
    sb.append(",");
    if (result.getComment() != null) {
      String encodedComment = encodeComment(result.getComment());
      sb.append(encodedComment);
    }
    return sb.toString();
  }

  /**
   * @param maxFieldsCount how many fields the file's header announced
   *     ({@link ExportCSVGenerator#getMaxFieldsCount}) — commas beyond it belong to the comment
   */
  public static ExportResult fromCSVLine(Context context, String line, int maxFieldsCount) throws CSVFormatException {
    List<String> fields = getFieldsFromCSVLine(line, maxFieldsCount);
    // 8 fields is the oldest version, 9 adds the scramble type, 10 the comment, 14 the smart cube
    boolean smartcubeFormat = fields.size() == ExportCSVGenerator.SMARTCUBE_MAX_FIELDS_COUNT;
    if (fields.size() < 8 || (!smartcubeFormat && fields.size() > ExportCSVGenerator.MAX_FIELDS_COUNT)) {
      throw new CSVFormatException(context.getString(R.string.import_invalid_columns_count));
    }
    String cubeTypeName = fields.get(0);
    String solveTypeName = fields.get(1);
    Long time = FormatterService.INSTANCE.unformatSolveTime(fields.get(2));
    if (time == null) {
      throw new CSVFormatException(context.getString(R.string.could_not_convert_time, fields.get(2)));
    }
    Long timestamp = FormatterService.INSTANCE.unformatExportDateTime(fields.get(3));
    if (timestamp == null) {
      throw new CSVFormatException(context.getString(R.string.could_not_convert_date, fields.get(3)));
    }
    boolean plusTwo = (fields.get(5).equals("y"));
    boolean blindType = (fields.get(6).equals("y"));

    int scrambleFieldIndex;
    String scrambleTypeName = null;
    if (fields.size() == 8) {
      scrambleFieldIndex = 7;
    } else {
      scrambleTypeName = fields.get(7);
      scrambleFieldIndex = 8;
    }
    String scramble = fields.get(scrambleFieldIndex);
    if ("".equals(scramble.trim())) {
      scramble = null;
    }

    int commentFieldIndex = smartcubeFormat ? 13 : 9;
    String comment = null;
    if (fields.size() > commentFieldIndex) {
      comment = decodeComment(fields.get(commentFieldIndex));
    }

    ExportResult exportResult = new ExportResult(cubeTypeName, solveTypeName, time, timestamp, plusTwo, blindType, scrambleTypeName, scramble, comment);
    String stepsField = fields.get(4);
    exportResult.setStepsTimes(getStepsTimes(context, stepsField));
    exportResult.setStepsNames(getStepsNames(context, stepsField));
    if (smartcubeFormat) {
      try {
        applySmartcubeFields(exportResult, fields.get(9), fields.get(10), fields.get(11), fields.get(12));
      } catch (IllegalArgumentException e) {
        throw new CSVFormatException(context.getString(R.string.import_invalid_smartcube_data, e.getMessage()));
      }
    }
    return exportResult;
  }

  /**
   * Applies a new-format line's smart-cube fields, validating them as one record: the fields
   * reference each other (steps need their method, the stopped step points into the steps), so a
   * half-valid set is rejected whole rather than half-imported. Empty fields mean a solve no cube
   * drove and stay null.
   */
  static void applySmartcubeFields(ExportResult result, String method, String moves, String steps,
      String stoppedStep) {
    method = method.trim(); // tolerate hand-edited whitespace, in every field
    moves = moves.trim();
    steps = steps.trim();
    stoppedStep = stoppedStep.trim();
    if (!method.isEmpty()) {
      CubeMethod cubeMethod = CubeMethod.fromCode(method);
      if (cubeMethod == null) {
        throw new IllegalArgumentException("Unknown method: \"" + method + "\"");
      }
      result.setSmartcubeMethod(cubeMethod);
    }
    if (!moves.isEmpty()) {
      if (SolveMovesFormat.parse(moves).isEmpty()) { // the lenient parser found not one valid move
        throw new IllegalArgumentException("Unreadable moves: \"" + moves + "\"");
      }
      result.setSmartcubeMoves(moves);
    }
    List<SolveStep> parsedSteps = SolveStepsFormat.parse(steps);
    if (!parsedSteps.isEmpty()) {
      if (result.getSmartcubeMethod() == null) { // the persistence layer requires the pair
        throw new IllegalArgumentException("Steps without a method");
      }
      result.setSmartcubeSteps(parsedSteps);
    } else if (result.getSmartcubeMethod() != null) {
      throw new IllegalArgumentException("Method without its steps");
    }
    if (!stoppedStep.isEmpty()) {
      int stopped;
      try {
        stopped = Integer.parseInt(stoppedStep);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid stopped step: \"" + stoppedStep + "\"");
      }
      boolean known = false;
      for (SolveStep step : parsedSteps) {
        known = known || step.getStepIndex() == stopped;
      }
      if (!known) { // also rejects a stopped step on a line that has no steps at all
        throw new IllegalArgumentException("Stopped step " + stopped + " matches no step");
      }
      result.setSmartcubeStoppedStep(stopped);
    }
  }

  private static Long[] getStepsTimes(Context context, String stepsField) throws CSVFormatException {
    String[] stepsTimesStr = getStepsField(context, stepsField, 1);
    if (stepsTimesStr == null) {
      return null;
    }
    Long[] stepsTimes = new Long[stepsTimesStr.length];
    for (int i = 0; i < stepsTimesStr.length; i++) {
      Long stepTime = FormatterService.INSTANCE.unformatSolveTime(stepsTimesStr[i]);
      if (stepTime == null) {
        throw new CSVFormatException(context.getString(R.string.could_not_convert_step_time, stepsTimesStr[i]));
      }
      stepsTimes[i] = stepTime;
    }
    return stepsTimes;
  }

  private static String[] getStepsNames(Context context, String stepsField) throws CSVFormatException {
    return getStepsField(context, stepsField, 0);
  }

  private static String[] getStepsField(Context context, String stepsField, int fieldIndex) throws CSVFormatException {
    if (stepsField == null || stepsField.equals("")) {
      return null;
    }
    String[] split = stepsField.split("\\|");
    String[] stepNames = new String[split.length];
    for (int i = 0; i < split.length; i++) {
      String[] stepSplit = split[i].split("=");
      if (stepSplit.length != 2) {
        String stepName = (stepSplit.length > 0) ? stepSplit[0] : "";
        throw new CSVFormatException(context.getString(R.string.invalid_step_format, stepName));
      }
      stepNames[i] = stepSplit[fieldIndex];
    }
    return stepNames;
  }

  static List<String> getFieldsFromCSVLine(String line, int maxFieldsCount) {
    final char escapeChar = '"';
    boolean inEscapedString = false;
    List<String> fields = new ArrayList<String>();
    StringBuilder currentField = new StringBuilder();
    for (char c : line.toCharArray()) {
      if (c == escapeChar) {
        inEscapedString = !inEscapedString;
      } else {
        if (c == ',') {
          if (inEscapedString || fields.size() >= maxFieldsCount - 1) { // ignore ',' in last field (comment field)
            currentField.append(c);
          } else {
            fields.add(currentField.toString());
            currentField.delete(0, currentField.length());
          }
        } else {
          currentField.append(c);
        }
      }
    }
    fields.add(currentField.toString());
    return fields;
  }

  private static String formatSteps(String[] stepsNames, Long[] stepsTimes) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < stepsTimes.length; i++) {
      String stepName = stepsNames[i];
      for (char c : Utils.FORBIDDEN_NAME_CHARACTERS) {
        stepName = stepName.replace(c, ' ');
      }
      sb.append(stepName).append('=').append(FormatterService.INSTANCE.formatSolveTime(stepsTimes[i], null, true));
      if (i < stepsTimes.length - 1) {
        sb.append('|');
      }
    }
    return escapeString(sb.toString());
  }

  private static String escapeString(String content) {
    // Adds quotes around string to escape it for CSV export (mostly for scrambles like Megaminx containing "\n", or Square-1 containing ",")
    if (content == null || content.equals("")) {
      return content;
    }
    if (content.length() >= 2 && content.charAt(0) == '"' && content.charAt(content.length() - 1) == '"') {
      return content; // already escaped
    }
    return '"' + content + '"';
  }

}
