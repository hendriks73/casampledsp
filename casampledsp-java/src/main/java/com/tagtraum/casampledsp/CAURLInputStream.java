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

import java.io.IOException;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Audio stream capable of decoding resources via Core Audio.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class CAURLInputStream extends CANativePeerInputStream {

  /**
   * Default I/O buffer size for {@code file:} URLs, in bytes (1 MB). A large buffer reduces the
   * number of disk read syscalls when decoding long files.
   */
  public static final int DEFAULT_FILE_BUFFER_SIZE = 1024 * 1024;

  /**
   * System property name to override {@link #DEFAULT_FILE_BUFFER_SIZE}, in bytes.
   *
   * <p>Example: {@code -Dcasampledsp.fileBufferSize=524288} sets a 512 KB buffer.
   */
  public static final String FILE_BUFFER_SIZE_PROPERTY = "casampledsp.fileBufferSize";

  static int getDefaultFileBufferSize() {
    try {
      final String prop = System.getProperty(FILE_BUFFER_SIZE_PROPERTY);
      if (prop != null) {
        final int v = Integer.parseInt(prop.trim());
        if (v > 0) return v;
      }
    } catch (NumberFormatException ignored) {
    }
    return DEFAULT_FILE_BUFFER_SIZE;
  }

  private final boolean seekable;
  private final URL url;

  /**
   * Opens a stream from the given URL with the default buffer size given in {@link
   * #DEFAULT_BUFFER_SIZE}.
   *
   * @param url resource to open
   */
  public CAURLInputStream(final URL url) throws IOException, UnsupportedAudioFileException {
    this(url, getDefaultFileBufferSize());
  }

  /**
   * Opens a stream from a URL with the given buffer size.
   *
   * @param url resource to open
   * @param bufferSize buffer size to use when reading
   */
  public CAURLInputStream(final URL url, final int bufferSize)
      throws IOException, UnsupportedAudioFileException {
    this.url = url;
    this.nativeBuffer = ByteBuffer.allocateDirect(bufferSize);
    // we cast, because of https://github.com/eclipse/jetty.project/issues/3244
    ((Buffer) this.nativeBuffer).limit(0);
    this.pointer = open(url.toString().getBytes(StandardCharsets.UTF_8), bufferSize);
    this.seekable = isSeekable(pointer);
  }

  @Override
  public boolean isSeekable() {
    return seekable;
  }

  @Override
  public synchronized void seek(final long time, final TimeUnit timeUnit)
      throws UnsupportedOperationException, IOException {
    if (!isSeekable())
      throw new UnsupportedOperationException("Seeking is not supported for " + url);
    final long microseconds = timeUnit.toMicros(time);
    if (isOpen()) {
      seek(pointer, microseconds);
    } else {
      throw new IOException("Stream is already closed: " + url);
    }
    ((Buffer) nativeBuffer).limit(0);
  }

  protected void fillNativeBuffer() throws IOException {
    if (isOpen()) {
      fillNativeBuffer(pointer);
    }
  }

  @Override
  public String toString() {
    return "CAURLInputStream{" + "url=" + url + ", seekable=" + seekable + '}';
  }

  private native boolean isSeekable(final long pointer);

  private native void seek(final long pointer, final long microseconds) throws IOException;

  private native void fillNativeBuffer(final long audioFileID) throws IOException;

  private native long open(final byte[] urlBytes, final int bufferSize) throws IOException;

  protected native void close(final long audioFileID) throws IOException;
}
