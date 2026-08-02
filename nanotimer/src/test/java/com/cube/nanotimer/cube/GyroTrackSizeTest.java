package com.cube.nanotimer.cube;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.OrientationHistory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * What a real solve's track actually costs, measured on the two hardware captures rather than
 * estimated. The figures decide where the track can be stored: it is three to four orders of
 * magnitude bigger than the pick-up grip that rides in the move stream, and that is the whole
 * reason it does not ride there too.
 */
public class GyroTrackSizeTest {

  /** A 34 s CFOP solve and a 69 s Roux one — Roux is the worse case, its M slices never settling. */
  @Test
  public void aRealSolvesTrackIsKilobytes() {
    int cfop = storedChars("cfop159.txt");
    int roux = storedChars("roux140.txt");
    System.out.println("gyro track: cfop159 " + cfop + " chars, roux140 " + roux + " chars");

    // Sanity rather than a pin: the exact size moves with the threshold, the order does not.
    assertTrue("a solve's track is kilobytes, not bytes", cfop > 1000);
    assertTrue("and the longer solve's is bigger", roux > cfop);
  }

  private static int storedChars(String fixture) {
    OrientationHistory history = new OrientationHistory();
    long lastMs = 0;
    for (String line : lines(fixture)) {
      String[] parts = line.split(" ");
      if (parts[0].equals("sample")) {
        long atMs = Long.parseLong(parts[1]);
        history.onSample(new CubeOrientation(Double.parseDouble(parts[2]),
            Double.parseDouble(parts[3]), Double.parseDouble(parts[4]),
            Double.parseDouble(parts[5])), atMs);
        lastMs = Math.max(lastMs, atMs);
      }
    }
    String stored = GyroTrackFormat.format(history.between(0, lastMs), null, 0);
    assertNotNull(stored);
    assertNotNull(GyroTrackFormat.parse(stored));
    return stored.length();
  }

  private static Iterable<String> lines(String fixture) {
    java.util.List<String> lines = new java.util.ArrayList<String>();
    try (InputStream in = GyroTrackSizeTest.class.getResourceAsStream("/gyro/" + fixture)) {
      assertNotNull("no such capture fixture: " + fixture, in);
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        lines.add(line);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return lines;
  }
}
