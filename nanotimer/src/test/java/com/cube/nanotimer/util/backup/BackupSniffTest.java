package com.cube.nanotimer.util.backup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The one decision the import path makes on its own: a picked file either goes down the
 * replace-everything route or the add-some-solves route, and it is decided on the file's first four
 * bytes. Getting it wrong either way is a bad afternoon, so it is pinned here rather than left to
 * the emulator.
 */
public class BackupSniffTest {

  @Test
  public void testARealZipIsRecognised() throws IOException {
    assertTrue(BackupRestorer.startsWithZipMagic(new ByteArrayInputStream(zipBytes())));
  }

  @Test
  public void testACsvIsNot() throws IOException {
    String csv = "cubetype,solvetype,time,date\n3x3x3,\"Default\",9.870,Aug 15 2026 - 09:00:00\n";
    assertFalse(BackupRestorer.startsWithZipMagic(new ByteArrayInputStream(csv.getBytes("UTF-8"))));
  }

  @Test
  public void testAnEmptyFileIsNot() throws IOException {
    assertFalse(BackupRestorer.startsWithZipMagic(new ByteArrayInputStream(new byte[0])));
  }

  /** Shorter than the magic itself, which the read loop has to survive rather than run off. */
  @Test
  public void testAFileShorterThanTheMagicIsNot() throws IOException {
    byte[] head = { 0x50, 0x4B };
    assertFalse(BackupRestorer.startsWithZipMagic(new ByteArrayInputStream(head)));
  }

  /** The last of the four bytes is what tells a zip from something that merely opens like one. */
  @Test
  public void testAFileThatOnlyStartsLikeAZipIsNot() throws IOException {
    byte[] nearly = { 0x50, 0x4B, 0x03, 0x05, 0x00 };
    assertFalse(BackupRestorer.startsWithZipMagic(new ByteArrayInputStream(nearly)));
  }

  /** A provider is free to hand the bytes over a few at a time; the magic still has to be read. */
  @Test
  public void testAStreamThatDribblesOneByteAtATimeIsStillRecognised() throws IOException {
    assertTrue(BackupRestorer.startsWithZipMagic(new DribblingStream(zipBytes())));
  }

  private static byte[] zipBytes() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ZipOutputStream zip = new ZipOutputStream(out);
    zip.putNextEntry(new ZipEntry(BackupFormat.MANIFEST_ENTRY));
    zip.write("{}".getBytes("UTF-8"));
    zip.closeEntry();
    zip.close();
    return out.toByteArray();
  }

  /** Hands back one byte per read, which a stream over a content Uri is entitled to do. */
  private static class DribblingStream extends InputStream {
    private final byte[] bytes;
    private int at;

    private DribblingStream(byte[] bytes) {
      this.bytes = bytes;
    }

    @Override
    public int read() {
      return at < bytes.length ? bytes[at++] & 0xFF : -1;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
      if (at >= bytes.length) {
        return -1;
      }
      buffer[offset] = bytes[at++];
      return 1;
    }
  }
}
