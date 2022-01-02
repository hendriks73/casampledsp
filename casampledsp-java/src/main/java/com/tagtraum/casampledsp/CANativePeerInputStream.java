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

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Audio stream backed by Core Audio.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public abstract class CANativePeerInputStream extends InputStream {

    static {
        // Ensure JNI library is loaded
        CANativeLibraryLoader.loadLibrary();
    }

    /**
     * Default read buffer size. Relates to how many bytes are read from the source (e.g. disk)
     * at a time (max). The default is 1 MB, but it can be overriden with the system property
     * {@code CA_DEFAULT_BUFFER_SIZE}. Values are given in kilo bytes (kB), <em>not bytes</em>.
     */
    public static int DEFAULT_BUFFER_SIZE = 1024 * Integer.parseInt(System.getProperty("CA_DEFAULT_BUFFER_SIZE", "1024"));

    /**
     * Pointer to the native peer struct.
     */
    protected long pointer;

    /**
     * Native audio buffer.
     */
    protected ByteBuffer nativeBuffer;

    protected CANativePeerInputStream() throws IOException, UnsupportedAudioFileException {
    }

    @Override
    public int read() throws IOException {
        if (!nativeBuffer.hasRemaining()) {
            fillNativeBuffer();
        }
        // we're at the end
        if (!nativeBuffer.hasRemaining()) {
            return -1;
        }
        return nativeBuffer.get() & 0xff;
    }

        @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (len == 0) return 0;
        if (len < 0) throw new IllegalArgumentException("Length must be greater than or equal to 0: " + len);
        if (off < 0) throw new IllegalArgumentException("Offset must be greater than or equal to 0: " + off);
        if (b.length - off < len) throw new IllegalArgumentException("There must be more space than "  + len + " bytes left in the buffer. Offset is " + off);

        int bytesRead = 0;
        while (bytesRead < len) {
            if (!nativeBuffer.hasRemaining()) {
                fillNativeBuffer();
                if (!nativeBuffer.hasRemaining()) {
                    // nothing more to read
                    break;
                }
            }
            final int chunkSize = Math.min(len-bytesRead, nativeBuffer.remaining());
            nativeBuffer.get(b, off+bytesRead, chunkSize);
            bytesRead += chunkSize;
        }

        if (!nativeBuffer.hasRemaining()) {
            fillNativeBuffer();
            if (!nativeBuffer.hasRemaining()) {
                // we're at the end
            }
        }
        return bytesRead == 0 ? -1 : bytesRead;
    }

    /**
     * @return true or false
     * @see com.tagtraum.casampledsp.CAAudioInputStream#isSeekable()
     */
    public abstract boolean isSeekable();

    /**
     * @param time time to seek
     * @param timeUnit unit for the time to seek
     * @see com.tagtraum.casampledsp.CAAudioInputStream#seek(long, java.util.concurrent.TimeUnit)
     * @throws java.io.IOException if something goes wrong
     * @throws UnsupportedOperationException if not supported
     */
    public abstract void seek(final long time, final TimeUnit timeUnit) throws UnsupportedOperationException, IOException;


    protected boolean isOpen() {
        return pointer != 0;
    }


    @Override
    public void close() throws IOException {
        if (isOpen()) {
            try {
                close(pointer);
            } finally {
                pointer = 0;
            }
        }
    }

    protected abstract void fillNativeBuffer() throws IOException;

    protected abstract void close(final long pointer) throws IOException;

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.finalize();
    }

}
