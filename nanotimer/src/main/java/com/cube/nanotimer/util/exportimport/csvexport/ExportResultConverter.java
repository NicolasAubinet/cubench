package com.cube.nanotimer.util.exportimport.csvexport;

import android.content.Context;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.exportimport.CSVFormatException;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.ExportResult;

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

  static String toCSVLine(ExportResult result) {
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
    sb.append(",");
    if (result.getTimeBeforeDnf() != null) {
      sb.append(FormatterService.INSTANCE.formatSolveTime(result.getTimeBeforeDnf(), null, true));
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
    // The layout is the one the file's header announced, never a guess from the line's own field
    // count. Consecutive layouts differ by a single column, so a line that lost one would read as
    // the shorter layout and quietly shift every field after it — the damage has to be reported.
    boolean withTimeBeforeDnf = maxFieldsCount == ExportCSVGenerator.MAX_FIELDS_COUNT;
    if (!isFieldsCountValid(fields.size(), maxFieldsCount)) {
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

    // Without the pre-DNF field the comment simply sits where that field would have been.
    int timeBeforeDnfFieldIndex = 9;
    int commentFieldIndex = withTimeBeforeDnf ? timeBeforeDnfFieldIndex + 1 : timeBeforeDnfFieldIndex;
    String comment = null;
    if (fields.size() > commentFieldIndex) {
      comment = decodeComment(fields.get(commentFieldIndex));
    }

    ExportResult exportResult = new ExportResult(cubeTypeName, solveTypeName, time, timestamp, plusTwo, blindType, scrambleTypeName, scramble, comment);
    String stepsField = fields.get(4);
    exportResult.setStepsTimes(getStepsTimes(context, stepsField));
    exportResult.setStepsNames(getStepsNames(context, stepsField));
    if (withTimeBeforeDnf) {
      try {
        applyTimeBeforeDnf(exportResult, fields.get(timeBeforeDnfFieldIndex));
      } catch (IllegalArgumentException e) {
        throw new CSVFormatException(context.getString(R.string.import_invalid_time_before_dnf, e.getMessage()));
      }
    }
    return exportResult;
  }

  /**
   * Whether a line carries the number of fields the layout its header announced calls for.
   * Only the three layouts that fold at ten share a fold count, so only they let the line's own
   * count say which it is: 8 fields is the oldest, 9 adds the scramble type, 10 the comment. The
   * layout carrying the pre-DNF time is exact, so a line short of it is damaged rather than an
   * older one — reading it as the older one would shift every field past the missing column.
   */
  static boolean isFieldsCountValid(int fieldsCount, int maxFieldsCount) {
    if (maxFieldsCount == ExportCSVGenerator.LEGACY_MAX_FIELDS_COUNT) {
      return fieldsCount >= 8 && fieldsCount <= maxFieldsCount;
    }
    return fieldsCount == maxFieldsCount;
  }

  /**
   * Applies the time a DNF replaced. Only a DNF can carry one, and only a real time is worth
   * restoring, so anything else is rejected rather than half-imported. An empty field is the
   * ordinary case: a solve that is not a DNF, or a DNF with nothing to go back to.
   */
  static void applyTimeBeforeDnf(ExportResult result, String timeBeforeDnf) {
    timeBeforeDnf = timeBeforeDnf.trim(); // tolerate hand-edited whitespace
    if (timeBeforeDnf.isEmpty()) {
      return;
    }
    if (result.getTime() >= 0) {
      throw new IllegalArgumentException("A time to restore on a solve that is not a DNF");
    }
    // A plain time, never a sentinel: "DNF" here would leave nothing to restore.
    Long time = FormatterService.INSTANCE.unformatPlainSolveTime(timeBeforeDnf);
    if (time == null) {
      throw new IllegalArgumentException("Unreadable time: \"" + timeBeforeDnf + "\"");
    }
    if (time <= 0) {
      throw new IllegalArgumentException("Not a solve time: \"" + timeBeforeDnf + "\"");
    }
    result.setTimeBeforeDnf(time);
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
