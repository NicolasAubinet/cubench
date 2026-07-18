package com.cube.nanotimer.util.helper;

import android.content.Context;
import com.cube.nanotimer.util.exportimport.csvexport.CSVGenerator;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FileUtils {

  public static List<String> readLinesFromFile(Context context, String fileName) {
    return readLinesFromFile(context, fileName, -1);
  }

  public static List<String> readLinesFromFile(File file, int linesLimit) {
    List<String> lines = new ArrayList<String>();
    try {
      FileInputStream fis = new FileInputStream(file);
      lines = readLinesFromFile(fis, linesLimit);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    return lines;
  }

  public static List<String> readLinesFromFile(Context context, String fileName, int linesLimit) {
    List<String> lines = new ArrayList<String>();
    FileInputStream fis;
    try {
      fis = context.openFileInput(fileName);
      lines = readLinesFromFile(fis, linesLimit);
    } catch (FileNotFoundException e) {
      // file does not exist, it will be created later
    }
    return lines;
  }

  private static List<String> readLinesFromFile(FileInputStream fis, int linesLimit) {
    List<String> lines = new ArrayList<String>();
    try {
      Scanner fileScanner = new Scanner(fis);
      int i = 0;
      while (fileScanner.hasNextLine() && (linesLimit <= 0 || i < linesLimit)) {
        lines.add(fileScanner.nextLine());
        i++;
      }
      fileScanner.close();
      fis.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return lines;
  }

  public static void appendLinesToFile(Context context, String fileName, String[] linesToSave) {
    try {
      FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_APPEND);
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos));
      for (String l : linesToSave) {
        writer.write(l);
        writer.newLine();
      }
      writer.close();
      fos.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void removeFirstLineFromFile(Context context, String fileName) {
    String tmpFileName = fileName + "_tmp";
    try {
      InputStream is = context.openFileInput(fileName);
      Scanner fileScanner = new Scanner(is);
      if (!fileScanner.hasNextLine()) {
        return; // file is empty
      }
      fileScanner.nextLine();

      FileOutputStream fos = context.openFileOutput(tmpFileName, Context.MODE_PRIVATE);
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos));
      while (fileScanner.hasNextLine()) {
        writer.write(fileScanner.nextLine());
        writer.newLine();
      }
      writer.close();
      fos.close();

      renameFile(context, tmpFileName, fileName);

      fileScanner.close();
      is.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void renameFile(Context context, String oldName, String newName) {
    File tmpFile = new File(context.getFilesDir(), oldName);
    File destFile = new File(context.getFilesDir(), newName);
    tmpFile.renameTo(destFile);
  }

  public static int getFileLinesCount(Context context, String fileName) {
    int linesCount = 0;
    try {
      InputStream is;
      try {
        is = context.openFileInput(fileName);
      } catch (FileNotFoundException e) {
        return 0;
      }
      byte[] c = new byte[1024];
      int count = 0;
      int readChars = 0;
      boolean empty = true;
      while ((readChars = is.read(c)) != -1) {
        empty = false;
        for (int i = 0; i < readChars; ++i) {
          if (c[i] == '\n') {
            count++;
          }
        }
      }
      is.close();
      linesCount = (count == 0 && !empty) ? 1 : count;
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return linesCount;
  }

  public static void deleteFile(Context context, String fileName) {
    File f = new File(context.getFilesDir(), fileName);
    f.delete();
  }

  public static File createCSVFile(Context context, String fileName, CSVGenerator generator) {
    File file = new File(context.getCacheDir(), fileName);
    try {
      FileOutputStream fos = new FileOutputStream(file);
      BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(fos));

      if (generator.getExportLine(0) != null) {
        bufferedWriter.write(generator.getHeaderLine());
        String line;
        for (int i = 0; (line = generator.getExportLine(i)) != null; i++) {
          bufferedWriter.newLine();
          bufferedWriter.write(line);
        }
      }

      bufferedWriter.close();
      fos.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return file;
  }

  // copies the file to the given stream, which is always closed
  public static void copyFileTo(File source, OutputStream os) throws IOException {
    if (os == null) {
      throw new IOException("Could not open the destination stream");
    }
    InputStream is = new FileInputStream(source);
    try {
      byte[] buffer = new byte[4096];
      int read;
      while ((read = is.read(buffer)) != -1) {
        os.write(buffer, 0, read);
      }
      os.flush();
    } finally {
      try { is.close(); } catch (IOException ignored) { }
      try { os.close(); } catch (IOException ignored) { }
    }
  }

  public static byte[] loadCompressedGzip(String fileName) throws IOException, DataFormatException {
    RandomAccessFile f = new RandomAccessFile(fileName, "r");
    byte[] data = new byte[(int)f.length()];
    f.readFully(data);
    return decompressGzip(data);
  }

  public static byte[] loadCompressedGzip(InputStream is, int length) throws IOException, DataFormatException {
    byte[] data = new byte[length];
    is.read(data);
    return decompressGzip(data);
  }

  private static byte[] decompressGzip(byte[] data) throws IOException, DataFormatException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    ByteArrayInputStream in = new ByteArrayInputStream(data);
    InputStream inflater = new GZIPInputStream(in);
    try {
      byte[] bbuf = new byte[256];
      while (true) {
        int r = inflater.read(bbuf);
        if (r < 0) {
          break;
        }
        buffer.write(bbuf, 0, r);
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } finally {
      inflater.close();
      in.close();
      buffer.close();
    }
    return buffer.toByteArray();
  }

  public static Object loadCompressedGzipSerializable(InputStream is) throws ClassNotFoundException {
    Object serializable = null;
    try {
//      FileInputStream fis = new FileInputStream(fileName);
      GZIPInputStream gz = new GZIPInputStream(is);
      ObjectInputStream ois = new ObjectInputStream(gz);
      serializable = ois.readObject();
      ois.close();
      gz.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return serializable;
  }

  // compression used by test classes (like RandomStateSquare1Test)
  public static void compressToGzipAndPersist(byte[] bytes, String fileName) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      GZIPOutputStream zos = new GZIPOutputStream(baos);
      zos.write(bytes);
      zos.close();

      OutputStream os = new FileOutputStream(fileName);
      baos.writeTo(os);
      os.close();
    } finally {
      baos.close();
    }
  }

  public static void compressToGzipAndPersist(Serializable serializable, String fileName) {
    try {
      FileOutputStream fos = new FileOutputStream(fileName);
      GZIPOutputStream gz = new GZIPOutputStream(fos);
      ObjectOutputStream oos = new ObjectOutputStream(gz);
      oos.writeObject(serializable);
      oos.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
