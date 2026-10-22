/*
 * =================================================
 * Copyright 2011 tagtraum industries incorporated
 * This file is part of CASampledSP.
 *
 * CASampledSP is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * CASampledSP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with CASampledSP; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * =================================================
 */
package com.tagtraum.casampledsp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.spi.FormatConversionProvider;

/**
 * {@link FormatConversionProvider} for CASampledSP.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class CAFormatConversionProvider extends FormatConversionProvider {

  private static final boolean nativeLibraryLoaded;

  static {
    // Ensure JNI library is loaded
    nativeLibraryLoaded = CANativeLibraryLoader.loadLibrary();
  }

  @Override
  public AudioFormat.Encoding[] getSourceEncodings() {
    if (!nativeLibraryLoaded) return new AudioFormat.Encoding[0];
    final Set<CAAudioFormat.CAEncoding> supportedAudioFormats =
        CAAudioFormat.CAEncoding.getSupportedEncodings();
    return supportedAudioFormats.toArray(new AudioFormat.Encoding[0]);
  }

  @Override
  public AudioFormat.Encoding[] getTargetEncodings() {
    if (!nativeLibraryLoaded) return new AudioFormat.Encoding[0];
    return new AudioFormat.Encoding[] {
      CAAudioFormat.CAEncoding.PCM_SIGNED, AudioFormat.Encoding.PCM_FLOAT
    };
  }

  @Override
  public AudioFormat.Encoding[] getTargetEncodings(final AudioFormat sourceFormat) {
    if (!nativeLibraryLoaded) return new AudioFormat.Encoding[0];
    return new AudioFormat.Encoding[] {
      CAAudioFormat.CAEncoding.PCM_SIGNED, AudioFormat.Encoding.PCM_FLOAT
    };
  }

  private static boolean isFloatEncoding(final AudioFormat.Encoding encoding) {
    return AudioFormat.Encoding.PCM_FLOAT.toString().equals(encoding.toString());
  }

  @Override
  public boolean isConversionSupported(
      final AudioFormat.Encoding targetEncoding, final AudioFormat sourceFormat) {
    if (!nativeLibraryLoaded) return false;
    if (!CAAudioFormat.CASAMPLEDSP.equals(sourceFormat.properties().get(CAAudioFormat.PROVIDER)))
      return false;
    if (super.isConversionSupported(targetEncoding, sourceFormat)) return true;
    if (isFloatEncoding(targetEncoding)) return true;
    final CAAudioFormat.CAEncoding caEncoding =
        CAAudioFormat.CAEncoding.getInstance(targetEncoding.toString());
    return CAAudioFormat.CAEncoding.PCM_SIGNED.equals(caEncoding);
  }

  @Override
  public boolean isConversionSupported(
      final AudioFormat targetFormat, final AudioFormat sourceFormat) {
    if (!nativeLibraryLoaded) return false;
    if (!CAAudioFormat.CASAMPLEDSP.equals(sourceFormat.properties().get(CAAudioFormat.PROVIDER)))
      return false;
    if (super.isConversionSupported(targetFormat, sourceFormat)) return true;
    if (isFloatEncoding(targetFormat.getEncoding())) {
      final int bits = targetFormat.getSampleSizeInBits();
      return bits == 32 || bits == 64;
    }
    final CAAudioFormat.CAEncoding caEncoding =
        CAAudioFormat.CAEncoding.getInstance(targetFormat.getEncoding().toString());
    if (!CAAudioFormat.CAEncoding.PCM_SIGNED.equals(caEncoding)) return false;
    return (targetFormat.getSampleSizeInBits() == 8
        || targetFormat.getSampleSizeInBits() == 16
        || targetFormat.getSampleSizeInBits() == 24
        || targetFormat.getSampleSizeInBits() == 32);
  }

  @Override
  public AudioFormat[] getTargetFormats(
      final AudioFormat.Encoding targetEncoding, final AudioFormat sourceFormat) {
    if (!nativeLibraryLoaded) return new AudioFormat[0];
    final List<AudioFormat> targetFormats = new ArrayList<>();

    if (isFloatEncoding(targetEncoding)) {
      // PCM_FLOAT: 32-bit and 64-bit, mono and stereo, always little-endian on macOS
      targetFormats.add(
          new AudioFormat(
              AudioFormat.Encoding.PCM_FLOAT,
              AudioSystem.NOT_SPECIFIED,
              32,
              1,
              4,
              AudioSystem.NOT_SPECIFIED,
              false));
      targetFormats.add(
          new AudioFormat(
              AudioFormat.Encoding.PCM_FLOAT,
              AudioSystem.NOT_SPECIFIED,
              32,
              2,
              8,
              AudioSystem.NOT_SPECIFIED,
              false));
      targetFormats.add(
          new AudioFormat(
              AudioFormat.Encoding.PCM_FLOAT,
              AudioSystem.NOT_SPECIFIED,
              64,
              1,
              8,
              AudioSystem.NOT_SPECIFIED,
              false));
      targetFormats.add(
          new AudioFormat(
              AudioFormat.Encoding.PCM_FLOAT,
              AudioSystem.NOT_SPECIFIED,
              64,
              2,
              16,
              AudioSystem.NOT_SPECIFIED,
              false));
      return targetFormats.toArray(new AudioFormat[0]);
    }

    final CAAudioFormat.CAEncoding caEncoding =
        CAAudioFormat.CAEncoding.getInstance(targetEncoding.toString());
    if (!CAAudioFormat.CAEncoding.PCM_SIGNED.equals(caEncoding)) return new AudioFormat[0];

    targetFormats.add(
        new AudioFormat(
            caEncoding,
            AudioSystem.NOT_SPECIFIED,
            8,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            true));
    targetFormats.add(
        new AudioFormat(
            caEncoding,
            AudioSystem.NOT_SPECIFIED,
            16,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            true));
    targetFormats.add(
        new AudioFormat(
            caEncoding,
            AudioSystem.NOT_SPECIFIED,
            24,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            true));
    targetFormats.add(
        new AudioFormat(
            caEncoding,
            AudioSystem.NOT_SPECIFIED,
            32,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            true));

    targetFormats.add(
        new AudioFormat(
            caEncoding,
            AudioSystem.NOT_SPECIFIED,
            8,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            false));
    targetFormats.add(
        new AudioFormat(
            caEncoding,
            AudioSystem.NOT_SPECIFIED,
            16,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            false));
    targetFormats.add(
        new AudioFormat(
            caEncoding,
            AudioSystem.NOT_SPECIFIED,
            24,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            false));
    targetFormats.add(
        new AudioFormat(
            caEncoding,
            AudioSystem.NOT_SPECIFIED,
            32,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            false));

    return targetFormats.toArray(new AudioFormat[0]);
  }

  @Override
  public AudioInputStream getAudioInputStream(
      final AudioFormat targetFormat, final AudioInputStream sourceStream) {
    if (!nativeLibraryLoaded)
      throw new IllegalArgumentException("Native library casampledsp not loaded.");
    try {
      return new CAAudioInputStream(
          new CACodecInputStream(targetFormat, (CAAudioInputStream) sourceStream),
          targetFormat,
          AudioSystem.NOT_SPECIFIED);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Failed to create AudioInputStream with format " + targetFormat + " from " + sourceStream,
          e);
    }
  }

  @Override
  public AudioInputStream getAudioInputStream(
      final AudioFormat.Encoding targetEncoding, final AudioInputStream sourceStream) {
    final AudioFormat sourceFormat = sourceStream.getFormat();
    final int channels = sourceFormat.getChannels() > 0 ? sourceFormat.getChannels() : 2;
    final int sampleSizeInBits;
    final boolean bigEndian;
    if (isFloatEncoding(targetEncoding)) {
      // upgrade source bit depth to minimum 32 bits for float output
      final int sourceBits =
          sourceFormat.getSampleSizeInBits() > 0 ? sourceFormat.getSampleSizeInBits() : 32;
      sampleSizeInBits = Math.max(32, sourceBits);
      bigEndian = false; // macOS is always little-endian
    } else {
      sampleSizeInBits =
          sourceFormat.getSampleSizeInBits() > 0 ? sourceFormat.getSampleSizeInBits() : 16;
      bigEndian = sourceFormat.isBigEndian();
    }
    final int frameSize = sampleSizeInBits / 8 * channels;
    final AudioFormat targetFormat =
        new AudioFormat(
            targetEncoding,
            sourceFormat.getSampleRate(),
            sampleSizeInBits,
            channels,
            frameSize,
            sourceFormat.getSampleRate(),
            bigEndian);
    return getAudioInputStream(targetFormat, sourceStream);
  }
}
