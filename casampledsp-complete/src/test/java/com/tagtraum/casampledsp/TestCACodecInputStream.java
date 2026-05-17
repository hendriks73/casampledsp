/*
 * =================================================
 * Copyright 2011 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.casampledsp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.junit.Test;

/**
 * TestCACodecInputStream.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class TestCACodecInputStream {

  @Test
  public void testReadConvertMP3FileToPCM() throws IOException, UnsupportedAudioFileException {
    final String filename = "test.mp3";
    final File file = File.createTempFile("testReadConvertMP3FileToPCM", filename);
    extractFile(filename, file);
    int bytesRead = 0;
    AudioInputStream mp3Stream = null;
    CACodecInputStream pcmStream = null;
    try {
      mp3Stream = new CAAudioFileReader().getAudioInputStream(file);
      final AudioFormat targetFormat =
          new AudioFormat(CAAudioFormat.CAEncoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
      System.err.println("mp3: " + mp3Stream.getFormat());
      System.err.println("pcm: " + targetFormat);
      pcmStream = new CACodecInputStream(targetFormat, (CAAudioInputStream) mp3Stream);

      int justRead;
      final byte[] buf = new byte[1024];
      while ((justRead = pcmStream.read(buf)) != -1) {
        assertTrue(justRead > 0);
        bytesRead += justRead;
      }
      // AudioSystem.write(new CAAudioInputStream(pcmStream, targetFormat, -1),
      // AudioFileFormat.Type.WAVE, new File("writtentest.wav"));

    } finally {
      if (pcmStream != null) {
        try {
          pcmStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (mp3Stream != null) {
        try {
          mp3Stream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      file.delete();
    }
    System.out.println("Read " + bytesRead + " bytes.");
    assertEquals(537020, bytesRead, 3000);
  }

  @Test
  public void testSeekForwards() throws IOException, UnsupportedAudioFileException {
    final String filename = "test.mp3";
    final File file = File.createTempFile("testSeekForwards", filename);
    extractFile(filename, file);
    AudioInputStream mp3Stream = null;
    CACodecInputStream pcmStream = null;
    try {
      mp3Stream = new CAAudioFileReader().getAudioInputStream(file);
      final AudioFormat targetFormat =
          new AudioFormat(CAAudioFormat.CAEncoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
      System.err.println("mp3: " + mp3Stream.getFormat());
      System.err.println("pcm: " + targetFormat);
      pcmStream = new CACodecInputStream(targetFormat, (CAAudioInputStream) mp3Stream);

      assertTrue(pcmStream.isSeekable());
      pcmStream.read(new byte[1024 * 4]);
      pcmStream.seek(1, TimeUnit.SECONDS);
      pcmStream.read(new byte[1024 * 4]);
    } finally {
      if (pcmStream != null) {
        try {
          pcmStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (mp3Stream != null) {
        try {
          mp3Stream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      file.delete();
    }
  }

  @Test(expected = IOException.class)
  public void testSeekClosedStream() throws IOException, UnsupportedAudioFileException {
    final String filename = "test.mp3";
    final File file = File.createTempFile("testSeekClosedStream", filename);
    extractFile(filename, file);
    AudioInputStream mp3Stream = null;
    CACodecInputStream pcmStream = null;
    try {
      mp3Stream = new CAAudioFileReader().getAudioInputStream(file);
      final AudioFormat targetFormat =
          new AudioFormat(CAAudioFormat.CAEncoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
      pcmStream = new CACodecInputStream(targetFormat, (CAAudioInputStream) mp3Stream);
      assertTrue(pcmStream.isSeekable());
    } finally {
      if (pcmStream != null) {
        try {
          pcmStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (mp3Stream != null) {
        try {
          mp3Stream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      file.delete();
    }
    // now seek in the already closed stream.
    pcmStream.seek(500, TimeUnit.SECONDS);
  }

  @Test
  public void testReadConvertM4AFileToPCM() throws IOException, UnsupportedAudioFileException {
    final String filename = "test.m4a"; // apple lossless
    final File file = File.createTempFile("testReadConvertM4AFileToPCM", filename);
    extractFile(filename, file);
    int bytesRead = 0;
    AudioInputStream m4aStream = null;
    CACodecInputStream pcmStream = null;
    try {
      m4aStream = new CAAudioFileReader().getAudioInputStream(file);
      final AudioFormat targetFormat =
          new AudioFormat(CAAudioFormat.CAEncoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
      System.err.println("m4a: " + m4aStream.getFormat());
      System.err.println("pcm: " + targetFormat);
      pcmStream = new CACodecInputStream(targetFormat, (CAAudioInputStream) m4aStream);

      // AudioSystem.write(new CAAudioInputStream(pcmStream, targetFormat, -1),
      // AudioFileFormat.Type.WAVE, new File("writtentest.wav"));
      int justRead;
      final byte[] buf = new byte[1024];
      while ((justRead = pcmStream.read(buf)) != -1) {
        assertTrue(justRead > 0);
        bytesRead += justRead;
      }

    } finally {
      if (pcmStream != null) {
        try {
          pcmStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (m4aStream != null) {
        try {
          m4aStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    System.out.println("Read " + bytesRead + " bytes.");
    assertEquals(534528, bytesRead);
  }

  @Test
  public void testDownsampleWaveFile() throws IOException, UnsupportedAudioFileException {
    final String filename = "test.wav";
    final File file = File.createTempFile("testDownsampleWaveFile", filename);
    extractFile(filename, file);
    int bytesRead = 0;
    AudioInputStream sourceStream = null;
    CACodecInputStream targetStream = null;
    try {
      sourceStream = new CAAudioFileReader().getAudioInputStream(file);
      final AudioFormat targetFormat =
          new AudioFormat(
              CAAudioFormat.CAEncoding.PCM_SIGNED, 44100 / 2, 16, 2, 4, 44100 / 2, false);
      System.err.println("44.100: " + sourceStream.getFormat());
      System.err.println("22.050: " + targetFormat);
      targetStream = new CACodecInputStream(targetFormat, (CAAudioInputStream) sourceStream);

      // AudioSystem.write(new CAAudioInputStream(targetStream, targetFormat, -1),
      // AudioFileFormat.Type.WAVE, new File("writtentest.wav"));
      int justRead;
      final byte[] buf = new byte[1024];
      while ((justRead = targetStream.read(buf)) != -1) {
        assertTrue(justRead > 0);
        bytesRead += justRead;
      }

    } finally {
      if (targetStream != null) {
        try {
          targetStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (sourceStream != null) {
        try {
          sourceStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    System.out.println("Read " + bytesRead + " bytes.");
    assertEquals(267264, bytesRead);
  }

  @Test
  public void testDownsampleMP3File() throws IOException, UnsupportedAudioFileException {
    final String filename = "test.mp3";
    final File file = File.createTempFile("testDownsampleMP3File", filename);
    extractFile(filename, file);
    int bytesRead = 0;
    AudioInputStream sourceStream = null;
    CACodecInputStream targetStream = null;
    try {
      sourceStream = new CAAudioFileReader().getAudioInputStream(file);
      final AudioFormat targetFormat =
          new AudioFormat(
              CAAudioFormat.CAEncoding.PCM_SIGNED, 44100 / 2, 16, 2, 4, 44100 / 2, false);
      System.err.println("44.100: " + sourceStream.getFormat());
      System.err.println("22.050: " + targetFormat);
      targetStream = new CACodecInputStream(targetFormat, (CAAudioInputStream) sourceStream);

      int justRead;
      final byte[] buf = new byte[1024];
      while ((justRead = targetStream.read(buf)) != -1) {
        assertTrue(justRead > 0);
        bytesRead += justRead;
      }
      // AudioSystem.write(new CAAudioInputStream(targetStream, targetFormat, -1),
      // AudioFileFormat.Type.WAVE, new File("writtentest.wav"));

    } finally {
      if (targetStream != null) {
        try {
          targetStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (sourceStream != null) {
        try {
          sourceStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    System.out.println("Read " + bytesRead + " bytes.");
    assertEquals(268512, bytesRead, 3000);
  }

  @Test
  public void testDownsampleMP3File2() throws IOException, UnsupportedAudioFileException {
    final String filename = "test.mp3";
    final File file = File.createTempFile("testDownsampleMP3File", filename);
    extractFile(filename, file);
    int bytesRead = 0;
    AudioInputStream sourceStream = null;
    CACodecInputStream targetStream = null;
    try {
      sourceStream = new CAAudioFileReader().getAudioInputStream(file);
      final AudioFormat targetFormat =
          new AudioFormat(
              CAAudioFormat.CAEncoding.PCM_SIGNED, 44100 / 4 * 3, 16, 2, 4, 44100 / 4 * 3, false);
      System.err.println("44.100: " + sourceStream.getFormat());
      System.err.println("33.075: " + targetFormat);
      targetStream = new CACodecInputStream(targetFormat, (CAAudioInputStream) sourceStream);

      int justRead;
      final byte[] buf = new byte[1024];
      while ((justRead = targetStream.read(buf)) != -1) {
        assertTrue(justRead > 0);
        bytesRead += justRead;
      }
      // AudioSystem.write(new CAAudioInputStream(targetStream, targetFormat, -1),
      // AudioFileFormat.Type.WAVE, new File("writtentest.wav"));

    } finally {
      if (targetStream != null) {
        try {
          targetStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (sourceStream != null) {
        try {
          sourceStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    System.out.println("Read " + bytesRead + " bytes.");
    assertEquals(402768, bytesRead, 3000);
  }

  @Test
  public void testReadConvertMP3StreamToPCM() throws IOException, UnsupportedAudioFileException {
    final String filename = "test.mp3";
    final File file = File.createTempFile("testReadConvertMP3FileToPCM", filename);
    extractFile(filename, file);
    int bytesRead = 0;
    AudioInputStream mp3Stream = null;
    CACodecInputStream pcmStream = null;
    try {
      mp3Stream =
          new CAAudioFileReader()
              .getAudioInputStream(new BufferedInputStream(new FileInputStream(file)));
      final AudioFormat targetFormat =
          new AudioFormat(CAAudioFormat.CAEncoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
      System.err.println("mp3: " + mp3Stream.getFormat());
      System.err.println("pcm: " + targetFormat);
      pcmStream = new CACodecInputStream(targetFormat, (CAAudioInputStream) mp3Stream);

      // AudioSystem.write(new CAAudioInputStream(pcmStream, targetFormat, -1),
      // AudioFileFormat.Type.WAVE, new File("writtentest.wav"));
      int justRead;
      final byte[] buf = new byte[1024];
      while ((justRead = pcmStream.read(buf)) != -1) {
        assertTrue(justRead > 0);
        bytesRead += justRead;
      }

    } finally {
      if (pcmStream != null) {
        try {
          pcmStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (mp3Stream != null) {
        try {
          mp3Stream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    System.out.println("Read " + bytesRead + " bytes.");
    assertEquals(537020, bytesRead, 3000);
  }

  @Test
  public void testReadConvertM4AStreamToPCMAndDownsample()
      throws IOException, UnsupportedAudioFileException {
    final String filename = "test.m4a";
    final File file = File.createTempFile("testReadConvertM4AFileToPCM", filename);
    extractFile(filename, file);
    int bytesRead = 0;
    AudioInputStream m4aStream = null;
    CACodecInputStream pcmStream = null;
    CACodecInputStream downStream = null;
    try {
      m4aStream =
          new CAAudioFileReader()
              .getAudioInputStream(new BufferedInputStream(new FileInputStream(file)));
      final AudioFormat pcmTargetFormat =
          new AudioFormat(CAAudioFormat.CAEncoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
      System.err.println("m4a: " + m4aStream.getFormat());
      System.err.println("pcm: " + pcmTargetFormat);
      pcmStream = new CACodecInputStream(pcmTargetFormat, (CAAudioInputStream) m4aStream);

      final AudioFormat downTargetFormat =
          new AudioFormat(CAAudioFormat.CAEncoding.PCM_SIGNED, 22050, 16, 2, 4, 22050, false);
      System.err.println("dwn: " + downTargetFormat);
      final CAAudioInputStream pcmAudioInputStream =
          new CAAudioInputStream(pcmStream, pcmTargetFormat, -1);
      downStream = new CACodecInputStream(downTargetFormat, pcmAudioInputStream);

      // AudioSystem.write(new CAAudioInputStream(downStream, downTargetFormat, -1),
      // AudioFileFormat.Type.WAVE, new File("writtentest.wav"));
      int justRead;
      final byte[] buf = new byte[1024];
      while ((justRead = downStream.read(buf)) != -1) {
        assertTrue(justRead > 0);
        bytesRead += justRead;
      }

    } finally {
      if (downStream != null) {
        try {
          downStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (pcmStream != null) {
        try {
          pcmStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (m4aStream != null) {
        try {
          m4aStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    System.out.println("Read " + bytesRead + " bytes.");
    assertEquals(267264, bytesRead);
  }

  @Test
  public void testReadConvertM4AStreamToPCM() throws IOException, UnsupportedAudioFileException {
    final String filename = "test.m4a";
    final File file = File.createTempFile("testReadConvertM4AFileToPCM", filename);
    extractFile(filename, file);
    int bytesRead = 0;
    AudioInputStream m4aStream = null;
    CACodecInputStream pcmStream = null;
    try {
      m4aStream =
          new CAAudioFileReader()
              .getAudioInputStream(new BufferedInputStream(new FileInputStream(file)));
      final AudioFormat targetFormat =
          new AudioFormat(CAAudioFormat.CAEncoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
      System.err.println("m4a: " + m4aStream.getFormat());
      System.err.println("pcm: " + targetFormat);
      pcmStream = new CACodecInputStream(targetFormat, (CAAudioInputStream) m4aStream);

      // AudioSystem.write(new CAAudioInputStream(pcmStream, targetFormat, -1),
      // AudioFileFormat.Type.WAVE, new File("writtentest.wav"));
      int justRead;
      final byte[] buf = new byte[1024];
      while ((justRead = pcmStream.read(buf)) != -1) {
        assertTrue(justRead > 0);
        bytesRead += justRead;
      }

    } finally {
      if (pcmStream != null) {
        try {
          pcmStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (m4aStream != null) {
        try {
          m4aStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    System.out.println("Read " + bytesRead + " bytes.");
    assertEquals(534528, bytesRead);
  }

  @Test
  public void testReadConvertWaveStreamToPCM() throws IOException, UnsupportedAudioFileException {
    final String filename = "test.wav";
    final File file = File.createTempFile("testReadConvertWaveStreamToPCM", filename);
    extractFile(filename, file);
    int bytesRead = 0;
    AudioInputStream wavStream = null;
    CACodecInputStream pcmStream = null;
    try {
      wavStream =
          new CAAudioFileReader()
              .getAudioInputStream(new BufferedInputStream(new FileInputStream(file)));
      final AudioFormat targetFormat =
          new AudioFormat(CAAudioFormat.CAEncoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
      System.err.println("wave: " + wavStream.getFormat());
      System.err.println("pcm: " + targetFormat);
      pcmStream = new CACodecInputStream(targetFormat, (CAAudioInputStream) wavStream);

      // AudioSystem.write(new CAAudioInputStream(pcmStream, targetFormat, -1),
      // AudioFileFormat.Type.WAVE, new File("writtentest.wav"));
      int justRead;
      final byte[] buf = new byte[1024];
      while ((justRead = pcmStream.read(buf)) != -1) {
        assertTrue(justRead > 0);
        bytesRead += justRead;
      }

    } finally {
      if (pcmStream != null) {
        try {
          pcmStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (wavStream != null) {
        try {
          wavStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    System.out.println("Read " + bytesRead + " bytes.");
    assertEquals(534528, bytesRead);
  }

  @Test
  public void testDownsampleWaveStream() throws IOException, UnsupportedAudioFileException {
    final String filename = "test.wav";
    final File file = File.createTempFile("testDownsampleWaveStream", filename);
    extractFile(filename, file);
    int bytesRead = 0;
    AudioInputStream sourceStream = null;
    CACodecInputStream targetStream = null;
    try {
      sourceStream =
          new CAAudioFileReader()
              .getAudioInputStream(new BufferedInputStream(new FileInputStream(file)));
      final AudioFormat targetFormat =
          new AudioFormat(
              CAAudioFormat.CAEncoding.PCM_SIGNED, 44100 / 2, 16, 2, 4, 44100 / 2, false);
      System.err.println("44.100: " + sourceStream.getFormat());
      System.err.println("22.050: " + targetFormat);
      targetStream = new CACodecInputStream(targetFormat, (CAAudioInputStream) sourceStream);

      // AudioSystem.write(new CAAudioInputStream(targetStream, targetFormat, -1),
      // AudioFileFormat.Type.WAVE, new File("writtentest.wav"));
      int justRead;
      final byte[] buf = new byte[1024];
      while ((justRead = targetStream.read(buf)) != -1) {
        assertTrue(justRead > 0);
        bytesRead += justRead;
      }

    } finally {
      if (targetStream != null) {
        try {
          targetStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (sourceStream != null) {
        try {
          sourceStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    System.out.println("Read " + bytesRead + " bytes.");
    assertEquals(267264, bytesRead);
  }

  @Test
  public void testUpsampleWaveStream() throws IOException, UnsupportedAudioFileException {
    final String filename = "test.wav";
    final File file = File.createTempFile("testUpsampleWaveStream", filename);
    extractFile(filename, file);
    int bytesRead = 0;
    AudioInputStream sourceStream = null;
    CACodecInputStream targetStream = null;
    try {
      sourceStream =
          new CAAudioFileReader()
              .getAudioInputStream(new BufferedInputStream(new FileInputStream(file)));
      final AudioFormat targetFormat =
          new AudioFormat(CAAudioFormat.CAEncoding.PCM_SIGNED, 48000, 16, 2, 4, 48000, false);
      System.err.println("44.100: " + sourceStream.getFormat());
      System.err.println("48.000: " + targetFormat);
      targetStream = new CACodecInputStream(targetFormat, (CAAudioInputStream) sourceStream);

      // AudioSystem.write(new CAAudioInputStream(targetStream, targetFormat, -1),
      // AudioFileFormat.Type.WAVE, new File("writtentest.wav"));
      int justRead;
      final byte[] buf = new byte[1024];
      while ((justRead = targetStream.read(buf)) != -1) {
        assertTrue(justRead > 0);
        bytesRead += justRead;
      }

    } finally {
      if (targetStream != null) {
        try {
          targetStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (sourceStream != null) {
        try {
          sourceStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    System.out.println("Read " + bytesRead + " bytes.");
    assertEquals(581796, bytesRead);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testPCMFloatIllegalSampleSize() throws IOException, UnsupportedAudioFileException {
    final String filename = "test.wav";
    final File file = File.createTempFile("testPCMFloatIllegalSampleSize", filename);
    extractFile(filename, file);
    try {
      final AudioInputStream wavStream = new CAAudioFileReader().getAudioInputStream(file);
      // 9-bit float is not valid — only 32 and 64 are supported
      final AudioFormat badFormat =
          new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 44100, 9, 2, 2, 44100, false);
      new CACodecInputStream(badFormat, (CAAudioInputStream) wavStream);
    } finally {
      file.delete();
    }
  }

  @Test
  public void testReadConvertWaveStreamToFloat32PCM()
      throws IOException, UnsupportedAudioFileException {
    final String filename = "test.wav";
    final File file = File.createTempFile("testReadConvertWaveStreamToFloat32PCM", filename);
    extractFile(filename, file);
    int bytesRead = 0;
    try {
      final AudioInputStream wavStream =
          new CAAudioFileReader()
              .getAudioInputStream(new BufferedInputStream(new FileInputStream(file)));
      // 32-bit float, same sample rate and channel count as source
      final AudioFormat targetFormat =
          new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 44100, 32, 2, 8, 44100, false);
      final CACodecInputStream floatStream =
          new CACodecInputStream(targetFormat, (CAAudioInputStream) wavStream);
      final byte[] buf = new byte[1024];
      int justRead;
      while ((justRead = floatStream.read(buf)) != -1) {
        assertTrue(justRead > 0);
        bytesRead += justRead;
      }
      floatStream.close();
      wavStream.close();
    } finally {
      file.delete();
    }
    // test.wav is 16-bit stereo 44100 Hz → 534528 bytes as PCM_SIGNED 16-bit
    // same frames as float32: 534528 / 4 * 8 = 1069056 bytes
    assertEquals(1069056, bytesRead);
  }

  @Test
  public void testConvertWavToFloat32SamplesInRange()
      throws IOException, UnsupportedAudioFileException {
    final String filename = "test.wav";
    final File file = File.createTempFile("testConvertWavToFloat32SamplesInRange", filename);
    extractFile(filename, file);
    try {
      final AudioInputStream wavStream = new CAAudioFileReader().getAudioInputStream(file);
      final AudioFormat targetFormat =
          new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 44100, 32, 2, 8, 44100, false);
      final CACodecInputStream floatStream =
          new CACodecInputStream(targetFormat, (CAAudioInputStream) wavStream);

      final byte[] buf = new byte[4096];
      final ByteBuffer bb = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
      final FloatBuffer fb = bb.asFloatBuffer();
      int justRead;
      while ((justRead = floatStream.read(buf)) != -1) {
        bb.clear();
        bb.put(buf, 0, justRead & ~3);
        bb.flip();
        fb.clear();
        final int count = (justRead & ~3) / 4;
        for (int i = 0; i < count; i++) {
          final float s = fb.get(i);
          assertTrue("Sample out of range: " + s, s >= -1.0f && s <= 1.0f);
        }
      }
      floatStream.close();
      wavStream.close();
    } finally {
      file.delete();
    }
  }

  @Test
  public void testConvertMp3ToFloat32SamplesFiniteAndReasonable()
      throws IOException, UnsupportedAudioFileException {
    final String filename = "test.mp3";
    final File file =
        File.createTempFile("testConvertMp3ToFloat32SamplesFiniteAndReasonable", filename);
    extractFile(filename, file);
    try {
      final AudioInputStream mp3Stream = new CAAudioFileReader().getAudioInputStream(file);
      final AudioFormat targetFormat =
          new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 44100, 32, 2, 8, 44100, false);
      final CACodecInputStream floatStream =
          new CACodecInputStream(targetFormat, (CAAudioInputStream) mp3Stream);

      final byte[] buf = new byte[4096];
      final ByteBuffer bb = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
      final FloatBuffer fb = bb.asFloatBuffer();
      int justRead;
      while ((justRead = floatStream.read(buf)) != -1) {
        bb.clear();
        bb.put(buf, 0, justRead & ~3);
        bb.flip();
        fb.clear();
        final int count = (justRead & ~3) / 4;
        for (int i = 0; i < count; i++) {
          final float s = fb.get(i);
          assertTrue("Sample must be finite: " + s, Float.isFinite(s));
          // MP3 may produce inter-sample peaks slightly outside [-1,1]; allow headroom
          assertTrue("Sample unreasonably large: " + s, s >= -2.0f && s <= 2.0f);
        }
      }
      floatStream.close();
      mp3Stream.close();
    } finally {
      file.delete();
    }
  }

  @Test
  public void testConvertWavToFloat64SamplesInRange()
      throws IOException, UnsupportedAudioFileException {
    final String filename = "test.wav";
    final File file = File.createTempFile("testConvertWavToFloat64SamplesInRange", filename);
    extractFile(filename, file);
    int bytesRead = 0;
    try {
      final AudioInputStream wavStream = new CAAudioFileReader().getAudioInputStream(file);
      final AudioFormat targetFormat =
          new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 44100, 64, 2, 16, 44100, false);
      final CACodecInputStream floatStream =
          new CACodecInputStream(targetFormat, (CAAudioInputStream) wavStream);

      final byte[] buf = new byte[4096];
      final ByteBuffer bb = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
      final DoubleBuffer db = bb.asDoubleBuffer();
      int justRead;
      while ((justRead = floatStream.read(buf)) != -1) {
        bytesRead += justRead;
        bb.clear();
        bb.put(buf, 0, justRead & ~7);
        bb.flip();
        db.clear();
        final int count = (justRead & ~7) / 8;
        for (int i = 0; i < count; i++) {
          final double s = db.get(i);
          assertTrue("Sample out of range: " + s, s >= -1.0 && s <= 1.0);
        }
      }
      floatStream.close();
      wavStream.close();
    } finally {
      file.delete();
    }
    assertEquals(2138112, bytesRead);
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
