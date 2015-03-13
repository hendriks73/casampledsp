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
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Audio stream capable of decoding resources via Core Audio.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class CAURLInputStream extends CANativePeerInputStream {

    private final boolean seekable;
    private URL url;

    public CAURLInputStream(final URL url) throws IOException, UnsupportedAudioFileException {
        this.url = url;
        this.nativeBuffer.limit(0);
        this.pointer = open(url.toString());
        this.seekable = isSeekable(pointer);
    }

    @Override
    public boolean isSeekable() {
        return seekable;
    }

    @Override
    public synchronized void seek(final long time, final TimeUnit timeUnit) throws UnsupportedOperationException, IOException {
        if (!isSeekable()) throw new UnsupportedOperationException("Seeking is not supported for " + url);
        final long microseconds = timeUnit.toMicros(time);
        if (isOpen()) {
            seek(pointer, microseconds);
        }
        nativeBuffer.limit(0);
    }

    protected void fillNativeBuffer() throws IOException {
        if (isOpen()) {
            fillNativeBuffer(pointer);
        }
    }

    @Override
    public String toString() {
        return "CAURLInputStream{" +
                "url=" + url +
                ", seekable=" + seekable +
                '}';
    }

    private native boolean isSeekable(final long pointer);
    private native void seek(final long pointer, final long microseconds) throws IOException;
    private native void fillNativeBuffer(final long audioFileID) throws IOException;
    private native long open(final String url) throws IOException;
    protected native void close(final long audioFileID) throws IOException;

}
