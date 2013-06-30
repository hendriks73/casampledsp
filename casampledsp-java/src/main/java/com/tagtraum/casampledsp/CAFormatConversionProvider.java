/*
 * =================================================
 * Copyright 2011 tagtraum industries incorporated
 * This file is part of CASampledSP.
 *
 * FFSampledSP is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * FFSampledSP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with FFSampledSP; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * =================================================
 */
package com.tagtraum.casampledsp;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.spi.FormatConversionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        final Set<CAAudioFormat.CAEncoding> supportedAudioFormats = CAAudioFormat.CAEncoding.getSupportedEncodings();
        return supportedAudioFormats.toArray(new AudioFormat.Encoding[supportedAudioFormats.size()]);
    }

    @Override
    public AudioFormat.Encoding[] getTargetEncodings() {
        if (!nativeLibraryLoaded) return new AudioFormat.Encoding[0];
        return new AudioFormat.Encoding[] {CAAudioFormat.CAEncoding.PCM_SIGNED};
    }

    @Override
    public AudioFormat.Encoding[] getTargetEncodings(final AudioFormat sourceFormat) {
        if (!nativeLibraryLoaded) return new AudioFormat.Encoding[0];
        return new AudioFormat.Encoding[] {CAAudioFormat.CAEncoding.PCM_SIGNED};
    }

    @Override
    public boolean isConversionSupported(AudioFormat.Encoding targetEncoding, AudioFormat sourceFormat){
        if (!nativeLibraryLoaded) return false;
        // because we only support conversions from CAAudioInputStreams we have to check whether the source format
        // was created by us. All source formats created by us, have the property "provider" set to "casampledsp".
        if (!CAAudioFormat.CASAMPLEDSP.equals(sourceFormat.properties().get(CAAudioFormat.PROVIDER))) return false;
        if (super.isConversionSupported(targetEncoding, sourceFormat)) return true;
        final CAAudioFormat.CAEncoding caEncoding = CAAudioFormat.CAEncoding.getInstance(targetEncoding.toString());
        // for now we only decode to signed linear pcm
        return CAAudioFormat.CAEncoding.PCM_SIGNED.equals(caEncoding);
    }

    @Override
    public boolean isConversionSupported(final AudioFormat targetFormat, final AudioFormat sourceFormat) {
        if (!nativeLibraryLoaded) return false;
        // because we only support conversions from CAAudioInputStreams we have to check whether the source format
        // was created by us. All source formats created by us, have the property "provider" set to "casampledsp".
        if (!CAAudioFormat.CASAMPLEDSP.equals(sourceFormat.properties().get(CAAudioFormat.PROVIDER))) return false;
        if (super.isConversionSupported(targetFormat, sourceFormat)) return true;
        final CAAudioFormat.CAEncoding caEncoding = CAAudioFormat.CAEncoding.getInstance(targetFormat.getEncoding().toString());
        // for now we only decode to signed linear pcm
        if (!CAAudioFormat.CAEncoding.PCM_SIGNED.equals(caEncoding)) return false;
        return (targetFormat.getSampleSizeInBits() == 8 || targetFormat.getSampleSizeInBits() == 16
                || targetFormat.getSampleSizeInBits() == 24 || targetFormat.getSampleSizeInBits() == 32);
    }


    @Override
    public AudioFormat[] getTargetFormats(final AudioFormat.Encoding targetEncoding, final AudioFormat sourceFormat) {
        if (!nativeLibraryLoaded) return new AudioFormat[0];
        final CAAudioFormat.CAEncoding caEncoding = CAAudioFormat.CAEncoding.getInstance(targetEncoding.toString());
        // for now we only decode to signed linear pcm
        if (!CAAudioFormat.CAEncoding.PCM_SIGNED.equals(caEncoding)) return new AudioFormat[0];
        final List<AudioFormat> targetFormats = new ArrayList<AudioFormat>();

        targetFormats.add(new AudioFormat(caEncoding, AudioSystem.NOT_SPECIFIED, 8, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, true));
        targetFormats.add(new AudioFormat(caEncoding, AudioSystem.NOT_SPECIFIED, 16, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, true));
        targetFormats.add(new AudioFormat(caEncoding, AudioSystem.NOT_SPECIFIED, 24, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, true));
        targetFormats.add(new AudioFormat(caEncoding, AudioSystem.NOT_SPECIFIED, 32, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, true));

        targetFormats.add(new AudioFormat(caEncoding, AudioSystem.NOT_SPECIFIED, 8, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, false));
        targetFormats.add(new AudioFormat(caEncoding, AudioSystem.NOT_SPECIFIED, 16, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, false));
        targetFormats.add(new AudioFormat(caEncoding, AudioSystem.NOT_SPECIFIED, 24, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, false));
        targetFormats.add(new AudioFormat(caEncoding, AudioSystem.NOT_SPECIFIED, 32, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, false));

        return targetFormats.toArray(new AudioFormat[targetFormats.size()]);
    }

    @Override
    public AudioInputStream getAudioInputStream(final AudioFormat targetFormat, final AudioInputStream sourceStream) {
        if (!nativeLibraryLoaded) throw new IllegalArgumentException("Native library casampledsp not loaded.");
        try {
            return new CAAudioInputStream(new CACodecInputStream(targetFormat, (CAAudioInputStream)sourceStream), targetFormat, AudioSystem.NOT_SPECIFIED);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create AudioInputStream with format " + targetFormat + " from " + sourceStream, e);
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(final AudioFormat.Encoding targetEncoding, final AudioInputStream sourceStream) {
        final AudioFormat sourceFormat = sourceStream.getFormat();
        // we assume some defaults...
        final int sampleSizeInBits = sourceFormat.getSampleSizeInBits() > 0 ? sourceFormat.getSampleSizeInBits() : 16;
        final int frameSize = sourceFormat.getFrameSize() > 0 ? sourceFormat.getFrameSize() : sampleSizeInBits * sourceFormat.getChannels() / 8;
        final AudioFormat targetFormat = new AudioFormat(targetEncoding, sourceFormat.getSampleRate(),
                sampleSizeInBits, sourceFormat.getChannels(), frameSize,
                sourceFormat.getSampleRate(), sourceFormat.isBigEndian());
        return getAudioInputStream(targetFormat, sourceStream);
    }


}
