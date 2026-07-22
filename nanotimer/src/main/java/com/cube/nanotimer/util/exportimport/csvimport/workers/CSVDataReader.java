package com.cube.nanotimer.util.exportimport.csvimport.workers;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.exportimport.CSVFormatException;
import com.cube.nanotimer.util.exportimport.ErrorListener;
import com.cube.nanotimer.util.exportimport.csvexport.ExportCSVGenerator;
import com.cube.nanotimer.util.exportimport.csvexport.ExportResultConverter;
import com.cube.nanotimer.util.exportimport.csvimport.CSVImporter;
import com.cube.nanotimer.util.exportimport.csvimport.ImportResultListener;
import com.cube.nanotimer.util.exportimport.csvimport.ImportTimesData;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ExportResult;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.SolveTypeStep;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class CSVDataReader extends AsyncTask<InputStream, Void, ImportTimesData> {

  private Context context;
  private ProgressDialog progressDialog;
  private ErrorListener errorListener;
  private ImportResultListener dataListener;

  public CSVDataReader(Context context, ErrorListener errorListener, ImportResultListener dataListener) {
    this.context = context;
    this.errorListener = errorListener;
    this.dataListener = dataListener;
  }

  @Override
  protected ImportTimesData doInBackground(InputStream... inputStreams) {
    InputStream inputStream = inputStreams[0];
    ImportTimesData importData = null;
    try {
      importData = getImportData(inputStream);
    } catch (CSVFormatException e) {
      errorListener.onError(e.getMessage());
    }
    return importData;
  }

  @Override
  protected void onPreExecute() {
    progressDialog = new ProgressDialog(context);
    progressDialog.setIndeterminate(true);
    progressDialog.setCancelable(false);
    progressDialog.setMessage(context.getString(R.string.reading_import_file));
    progressDialog.show();
  }

  @Override
  protected void onPostExecute(ImportTimesData importData) {
    progressDialog.hide();
    progressDialog.dismiss();

    if (importData == null) {
      dataListener.onResult(CSVImporter.ERROR);
    } else if (!importData.isEmpty()) {
      new SolveTypesVerifier(context, errorListener, dataListener).execute(importData);
    }
  }

  private ImportTimesData getImportData(InputStream inputStream) throws CSVFormatException {
    List<String> lines = readCSVFile(inputStream);
    lines = CSVLineGrouper.group(lines);
    List<ExportResult> exportResults = getExportResults(lines);
    ImportTimesData importData = new ImportTimesData(context);
    if (exportResults.size() == 0) {
      dataListener.onResult(CSVImporter.NO_DATA);
      return importData;
    }

    for (ExportResult exportResult : exportResults) {
      CubeType cubeType = CubeType.getCubeTypeFromName(exportResult.getCubeTypeName());
      if (cubeType == null) {
        throw new CSVFormatException(context.getString(R.string.could_not_find_cube_type, exportResult.getCubeTypeName()));
      }

      ScrambleType scrambleType = null;
      String scrambleTypeName = exportResult.getScrambleTypeName();
      if (scrambleTypeName != null && !scrambleTypeName.trim().isEmpty()) {
        scrambleType = cubeType.getScrambleTypeFromString(scrambleTypeName);
        if (scrambleType == null) {
          throw new CSVFormatException(context.getString(R.string.could_not_find_scramble_type, scrambleTypeName, cubeType.getName()));
        }
      }

      SolveType solveType = new SolveType(exportResult.getSolveTypeName(), exportResult.isBlindType(), scrambleType, cubeType.getId());
      if (exportResult.hasSteps()) {
        SolveTypeStep[] steps = new SolveTypeStep[exportResult.getStepsNames().length];
        for (int i = 0; i < exportResult.getStepsNames().length; i++) {
          SolveTypeStep step = new SolveTypeStep();
          step.setName(exportResult.getStepsNames()[i]);
          steps[i] = step;
        }
        solveType.setSteps(steps);
      }
      solveType = importData.addSolveTypeIfNotExists(cubeType, solveType);

      SolveTime solveTime = new SolveTime(exportResult.getTimestamp(), exportResult.getTime(),
        exportResult.isPlusTwo(), exportResult.getScramble(), solveType);
      solveTime.setStepsTimes(exportResult.getStepsTimes());
      solveTime.setComment(exportResult.getComment());
      solveTime.setSmartcubeMethod(exportResult.getSmartcubeMethod());
      solveTime.setSmartcubeMoves(exportResult.getSmartcubeMoves());
      solveTime.setSmartcubeSteps(exportResult.getSmartcubeSteps());
      solveTime.setSmartcubeStoppedStep(exportResult.getSmartcubeStoppedStep());
      importData.addSolveTime(solveType, solveTime);
    }
    return importData;
  }

  private List<String> readCSVFile(InputStream inputStream) throws CSVFormatException {
    List<String> lines = new ArrayList<String>();
    try {
      Scanner fileScanner = new Scanner(inputStream);
      int i = 0;
      while (fileScanner.hasNextLine()) {
        String line = fileScanner.nextLine();
        if (i == 0 && !ExportCSVGenerator.isHeaderLegit(line)) {
          throw new CSVFormatException(context.getString(R.string.invalid_import_file_format));
        }
        lines.add(line);
        i++;
      }
      fileScanner.close();
      inputStream.close();
    } catch (FileNotFoundException e) {
      errorListener.onError(context.getString(R.string.error_reading_import_file, e.getMessage()));
    } catch (IOException e) {
      errorListener.onError(context.getString(R.string.error_reading_import_file, e.getMessage()));
    }
    return lines;
  }

  private List<ExportResult> getExportResults(List<String> lines) throws CSVFormatException {
    List<ExportResult> results = new ArrayList<ExportResult>();
    if (lines.isEmpty()) { // a file with no lines has no header line to read the format from
      return results;
    }
    int maxFieldsCount = ExportCSVGenerator.getMaxFieldsCount(lines.get(0));
    for (int i = 1; i < lines.size(); i++) {
      try {
        results.add(ExportResultConverter.fromCSVLine(context, lines.get(i), maxFieldsCount));
      } catch (CSVFormatException e) {
        throw new CSVFormatException(context.getString(R.string.error_in_line, (i+1)) + ": " + e.getMessage());
      }
    }
    Collections.reverse(results); // sort from oldest to newest
    return results;
  }

}
