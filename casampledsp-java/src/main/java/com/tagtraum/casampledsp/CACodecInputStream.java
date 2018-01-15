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
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.TimeUnit;

/**
 * Used by {@link CAFormatConversionProvider} to convert a {@link CAAudioInputStream} (not just
 * any {@link javax.sound.sampled.AudioInputStream}) to another {@link AudioFormat}.
 * <p>
 * Note that we take a shortcut:<br>
 * Instead of only relying on the source Java stream, we take advantage of the
 * {@link CANativePeerInputStream}.
 * This of course only works, if the stream to convert is also an {@link CAAudioInputStream}.
 * This needs to be checked in {@link CAFormatConversionProvider} using the {@link CAAudioFormat#PROVIDER}
 * property of the source format.
 *
 * @see CAFormatConversionProvider#isConversionSupported(javax.sound.sampled.AudioFormat, javax.sound.sampled.AudioFormat)
 * @see CAFormatConversionProvider#isConversionSupported(javax.sound.sampled.AudioFormat.Encoding, javax.sound.sampled.AudioFormat)
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class CACodecInputStream extends CANativePeerInputStream {

    private CANativePeerInputStream wrappedStream;

    public CACodecInputStream(final AudioFormat targetFormat, final CAAudioInputStream stream) throws IOException, UnsupportedAudioFileException {

        // make sure we have a supported encoding
        AudioFormat audioFormat = targetFormat;
        if (!(targetFormat.getEncoding() instanceof CAAudioFormat.CAEncoding)) {
            // make sure we hand a CAEncoding to the native code
            final CAAudioFormat.CAEncoding caEncoding = CAAudioFormat.CAEncoding.getInstance(targetFormat.getEncoding().toString());
            if (caEncoding == null) {
                throw new UnsupportedEncodingException("This codec does not support the encoding \"" + targetFormat.getEncoding()
                        + "\". Supported codecs are: " + CAAudioFormat.CAEncoding.getSupportedEncodings());
            }
            audioFormat = new AudioFormat(caEncoding,
                    targetFormat.getSampleRate(), targetFormat.getSampleSizeInBits(), targetFormat.getChannels(),
                    targetFormat.getFrameSize(), targetFormat.getFrameRate(), targetFormat.isBigEndian());
        }

        this.nativeBuffer.limit(0);
        this.pointer = open(audioFormat, stream.getNativePeerInputStream(), stream.getNativePeerInputStreamPointer());
        this.wrappedStream = stream.getNativePeerInputStream();
    }

    @Override
    protected void fillNativeBuffer() throws IOException {
        if (isOpen()) {
            fillNativeBuffer(pointer);
        }
    }

    @Override
    public boolean isSeekable() {
        return wrappedStream.isSeekable();
    }

    @Override
    public void seek(final long time, final TimeUnit timeUnit) throws UnsupportedOperationException, IOException {
        wrappedStream.seek(time, timeUnit);
        nativeBuffer.limit(0);
        if (isOpen()) {
            reset(pointer);
        } else {
            throw new IOException("Stream is already closed");
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (wrappedStream != null) wrappedStream.close();
        } finally {
            super.close();
        }
    }

    private native void reset(final long pointer) throws IOException;
    private native void fillNativeBuffer(final long pointer) throws IOException;
    private native long open(final AudioFormat target, final CANativePeerInputStream stream, final long pointer) throws IOException;
    @Override
    protected native void close(final long pointer) throws IOException;


}
