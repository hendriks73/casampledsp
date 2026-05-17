/*
 * =================================================
 * Copyright 2011 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.casampledsp;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.junit.Test;

/**
 * TestCAFormatConversionProvider.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class TestCAFormatConversionProvider {

  @Test
  public void testGetTargetFormats() {
    final AudioFormat[] targetFormats =
        new CAFormatConversionProvider()
            .getTargetFormats(
                AudioFormat.Encoding.PCM_SIGNED,
                new AudioFormat(CAAudioFormat.CAEncoding.MP3, 22050f, 16, 2, -1, -1, true));
    System.out.println("Formats: " + targetFormats.length);
    assertEquals(8, targetFormats.length);
  }

  @Test
  public void testGetTargetFormatsUnsupportedEncoding() {
    final AudioFormat[] targetFormats =
        new CAFormatConversionProvider()
            .getTargetFormats(
                AudioFormat.Encoding.PCM_UNSIGNED,
                new AudioFormat(CAAudioFormat.CAEncoding.MP3, 22050f, 16, 2, -1, -1, true));
    System.out.println("Formats: " + targetFormats.length);
    assertEquals(0, targetFormats.length);
  }

  @Test
  public void testGetTargetEncodings() {
    final AudioFormat.Encoding[] targetEncodings =
        new CAFormatConversionProvider().getTargetEncodings();
    final List<AudioFormat.Encoding> list = Arrays.asList(targetEncodings);
    assertTrue(list.contains(CAAudioFormat.CAEncoding.PCM_SIGNED));
    assertTrue(list.contains(AudioFormat.Encoding.PCM_FLOAT));
    assertEquals(2, targetEncodings.length);
  }

  @Test
  public void testGetSourceEncodings() {
    final AudioFormat.Encoding[] sourceEncodings =
        new CAFormatConversionProvider().getSourceEncodings();
    final Set<CAAudioFormat.CAEncoding> supportedEncodings =
        CAAudioFormat.CAEncoding.getSupportedEncodings();
    for (final AudioFormat.Encoding encoding : sourceEncodings) {
      assertTrue(supportedEncodings.contains(encoding));
    }
  }

  @Test
  public void testIsConversionSupportedNonCAFormat() {
    final AudioFormat format = new AudioFormat(44100, 16, 2, true, true);
    assertFalse(new CAFormatConversionProvider().isConversionSupported(format, format));
  }

  @Test
  public void testIsConversionSupportedCAFormat() {
    final AudioFormat format = new AudioFormat(44100, 16, 2, true, true);
    final CAAudioFormat caAudioFormat =
        new CAAudioFormat(
            CAAudioFormat.CAEncoding.WAVE_BE.getDataFormat(),
            44100f,
            16,
            2,
            0,
            44100f,
            true,
            0,
            true);
    assertTrue(new CAFormatConversionProvider().isConversionSupported(format, caAudioFormat));
  }

  @Test
  public void testIsConversionSupportedEncodingCAFormat() {
    final CAAudioFormat caAudioFormat =
        new CAAudioFormat(
            CAAudioFormat.CAEncoding.WAVE_BE.getDataFormat(),
            44100f,
            16,
            2,
            0,
            44100f,
            true,
            0,
            true);
    assertTrue(
        new CAFormatConversionProvider()
            .isConversionSupported(AudioFormat.Encoding.PCM_SIGNED, caAudioFormat));
  }

  @Test
  public void testIsConversionSupportedEncodingNonCAFormat() {
    final AudioFormat format = new AudioFormat(44100, 16, 2, true, true);
    assertFalse(
        new CAFormatConversionProvider()
            .isConversionSupported(AudioFormat.Encoding.PCM_SIGNED, format));
  }

  @Test
  public void testGetTargetEncodingsIncludesFloat() {
    final AudioFormat.Encoding[] encodings = new CAFormatConversionProvider().getTargetEncodings();
    assertTrue(Arrays.asList(encodings).contains(AudioFormat.Encoding.PCM_FLOAT));
  }

  @Test
  public void testGetTargetFormatsFloat() {
    final AudioFormat sourceFormat =
        new AudioFormat(CAAudioFormat.CAEncoding.MP3, 44100f, 16, 2, -1, -1, false);
    final AudioFormat[] formats =
        new CAFormatConversionProvider()
            .getTargetFormats(AudioFormat.Encoding.PCM_FLOAT, sourceFormat);
    assertEquals(4, formats.length);
    // verify we have 32-bit and 64-bit, mono and stereo
    boolean has32Mono = false, has32Stereo = false, has64Mono = false, has64Stereo = false;
    for (final AudioFormat f : formats) {
      if (f.getSampleSizeInBits() == 32 && f.getChannels() == 1) has32Mono = true;
      if (f.getSampleSizeInBits() == 32 && f.getChannels() == 2) has32Stereo = true;
      if (f.getSampleSizeInBits() == 64 && f.getChannels() == 1) has64Mono = true;
      if (f.getSampleSizeInBits() == 64 && f.getChannels() == 2) has64Stereo = true;
    }
    assertTrue(has32Mono);
    assertTrue(has32Stereo);
    assertTrue(has64Mono);
    assertTrue(has64Stereo);
  }

  @Test
  public void testIsConversionSupportedPCMFloat() {
    final CAAudioFormat caSource =
        new CAAudioFormat(
            CAAudioFormat.CAEncoding.WAVE_LE.getDataFormat(),
            44100f,
            16,
            2,
            4,
            44100f,
            false,
            0,
            false);
    assertTrue(
        new CAFormatConversionProvider()
            .isConversionSupported(AudioFormat.Encoding.PCM_FLOAT, caSource));
  }

  @Test
  public void testGetTargetFormatsStandardPCMFloat() {
    final CAAudioFormat caSource =
        new CAAudioFormat(
            CAAudioFormat.CAEncoding.WAVE_LE.getDataFormat(),
            44100f,
            16,
            2,
            4,
            44100f,
            false,
            0,
            false);
    final AudioFormat[] formats =
        new CAFormatConversionProvider().getTargetFormats(AudioFormat.Encoding.PCM_FLOAT, caSource);
    assertEquals(4, formats.length);
  }

  @Test
  public void testConvertWavToFloat32ViaStandardEncoding()
      throws IOException, UnsupportedAudioFileException {
    final File file = File.createTempFile("testConvertWavToFloat32ViaStandardEncoding", ".wav");
    extractFile("test.wav", file);
    try {
      final AudioInputStream wavStream = new CAAudioFileReader().getAudioInputStream(file);
      final AudioFormat sourceFormat = wavStream.getFormat();
      final AudioFormat floatFormat =
          new AudioFormat(
              AudioFormat.Encoding.PCM_FLOAT,
              sourceFormat.getSampleRate(),
              32,
              sourceFormat.getChannels(),
              4 * sourceFormat.getChannels(),
              sourceFormat.getSampleRate(),
              false);
      final AudioInputStream floatStream =
          new CAFormatConversionProvider().getAudioInputStream(floatFormat, wavStream);

      final byte[] buf = new byte[4096];
      int totalBytes = 0;
      int justRead;
      final ByteBuffer bb = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
      final FloatBuffer fb = bb.asFloatBuffer();
      boolean allInRange = true;
      while ((justRead = floatStream.read(buf)) != -1) {
        totalBytes += justRead;
        // validate sample range for lossless source
        bb.clear();
        bb.put(buf, 0, justRead & ~3); // align to 4 bytes
        bb.flip();
        fb.clear();
        final int floatCount = (justRead & ~3) / 4;
        for (int i = 0; i < floatCount; i++) {
          final float sample = fb.get(i);
          if (sample < -1.0f || sample > 1.0f) {
            allInRange = false;
            break;
          }
        }
      }
      floatStream.close();
      wavStream.close();

      // test.wav → 16-bit stereo 44100 Hz → 534528 bytes as PCM_SIGNED 16-bit
      // as float32: 534528 / 4 frames × 8 bytes/frame = 1069056 bytes
      assertEquals(1069056, totalBytes);
      assertTrue("All PCM_FLOAT samples from lossless source must be in [-1, 1]", allInRange);
    } finally {
      file.delete();
    }
  }

  @Test
  public void testConvertWavToFloat64() throws IOException, UnsupportedAudioFileException {
    final File file = File.createTempFile("testConvertWavToFloat64", ".wav");
    extractFile("test.wav", file);
    try {
      final AudioInputStream wavStream = new CAAudioFileReader().getAudioInputStream(file);
      final AudioFormat sourceFormat = wavStream.getFormat();
      final AudioFormat floatFormat =
          new AudioFormat(
              AudioFormat.Encoding.PCM_FLOAT,
              sourceFormat.getSampleRate(),
              64,
              sourceFormat.getChannels(),
              8 * sourceFormat.getChannels(),
              sourceFormat.getSampleRate(),
              false);
      final AudioInputStream floatStream =
          new CAFormatConversionProvider().getAudioInputStream(floatFormat, wavStream);

      final byte[] buf = new byte[4096];
      int totalBytes = 0;
      int justRead;
      final ByteBuffer bb = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
      final DoubleBuffer db = bb.asDoubleBuffer();
      boolean allInRange = true;
      while ((justRead = floatStream.read(buf)) != -1) {
        totalBytes += justRead;
        bb.clear();
        bb.put(buf, 0, justRead & ~7); // align to 8 bytes
        bb.flip();
        db.clear();
        final int doubleCount = (justRead & ~7) / 8;
        for (int i = 0; i < doubleCount; i++) {
          final double sample = db.get(i);
          if (sample < -1.0 || sample > 1.0) {
            allInRange = false;
            break;
          }
        }
      }
      floatStream.close();
      wavStream.close();

      assertEquals(2138112, totalBytes);
      assertTrue(
          "All PCM_FLOAT 64-bit samples from lossless source must be in [-1, 1]", allInRange);
    } finally {
      file.delete();
    }
  }

  private void extractFile(final String filename, final File file) throws IOException {
    try (final InputStream in = getClass().getResourceAsStream(filename);
        final OutputStream out = new FileOutputStream(file)) {
      final byte[] buf = new byte[1024 * 64];
      int justRead;
      while ((justRead = in.read(buf)) != -1) {
        out.write(buf, 0, justRead);
      }
    }
  }
}
