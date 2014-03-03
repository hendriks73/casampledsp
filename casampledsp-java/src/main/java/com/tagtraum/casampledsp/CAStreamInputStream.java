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

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Audio stream capable of decoding a stream via Core Audio.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class CAStreamInputStream extends CANativePeerInputStream {

    /**
     * Java audio buffer. We keep this smaller to make sure that its content still fits into the native buffer.
     */
    private final byte[] streamReadBuffer = new byte[4 * 1024];
    private InputStream stream;

    public CAStreamInputStream(final InputStream stream, final int hint) throws IOException, UnsupportedAudioFileException {
        this.nativeBuffer.limit(0);
        this.pointer = open(hint);
        this.stream  = stream;
    }

    /**
     * Always returns <code>false</code>.
     * Stream based {@link CANativePeerInputStream}s are not seekable.
     *
     * @return false
     */
    @Override
    public boolean isSeekable() {
        return false;
    }

    /**
     * Always throws {@link UnsupportedOperationException}, because stream based
     * {@link CANativePeerInputStream}s are not seekable
     *
     * @param time time
     * @param timeUnit time unit
     * @throws UnsupportedOperationException
     * @throws IOException
     */
    @Override
    public void seek(final long time, final TimeUnit timeUnit) throws UnsupportedOperationException, IOException {
        throw new UnsupportedOperationException("Seeking is not supported.");
    }

    @Override
    protected void fillNativeBuffer() throws IOException {
        if (isOpen()) {
            // make sure we are at the start of the native buffer, before we fill it
            nativeBuffer.limit(0);
            // read data, until we have a new limit or we reached the end of the file
            int justRead;
            while ((justRead = stream.read(streamReadBuffer)) != -1) {
                fillNativeBuffer(pointer, streamReadBuffer, justRead);
                if (nativeBuffer.hasRemaining()) {
                    // we have new data, let's break
                    break;
                }
            }
            if (justRead == -1) {
                close();
            }
        }
    }

    private native void fillNativeBuffer(final long audioFileID, final byte[] buf, final int length) throws IOException;
    private native long open(final int hint) throws IOException;
    protected native void close(final long pointer) throws IOException;


}
