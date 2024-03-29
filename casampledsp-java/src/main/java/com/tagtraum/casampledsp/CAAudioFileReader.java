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

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Open URLs/files or streams and returns a {@link AudioFileFormat} instance.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class CAAudioFileReader extends AudioFileReader {

    private static final boolean nativeLibraryLoaded;

    static {
        // Ensure JNI library is loaded
        nativeLibraryLoaded = CANativeLibraryLoader.loadLibrary();
    }

    private static final Map<URL, AudioFileFormat> cache = Collections.synchronizedMap(new LinkedHashMap<URL, AudioFileFormat>() {
        private static final int MAX_ENTRIES = 20;

        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > MAX_ENTRIES;
        }
    });
    private int connectTimeout = 5000;

    private static void addAudioAudioFileFormatToCache(final URL url, final AudioFileFormat audioFileFormat) {
        cache.put(url, audioFileFormat);
    }

    private static AudioFileFormat getAudioFileFormatFromCache(final URL url) {
        return cache.get(url);
    }

    /**
     * In case a resource is located on the network, use the given connection timeout (in ms).
     *
     * @param connectTimeout timeout
     */
    public void setConnectTimeout(final int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Return the connection timeout for network resources.
     *
     * @return timeout in ms
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

    @Override
    public AudioFileFormat getAudioFileFormat(final InputStream stream) throws UnsupportedAudioFileException, IOException {
        if (!nativeLibraryLoaded) throw new UnsupportedAudioFileException("Native library casampledsp not loaded.");
        return getAudioFileFormat(stream, 0);
    }

    private AudioFileFormat getAudioFileFormat(final InputStream stream, final int fileTypeHint) throws IOException {
        if (!stream.markSupported()) throw new IOException("InputStream must support mark()");
        final int readlimit = 1024 * 32;
        stream.mark(readlimit);
        try {
            final byte[] buf = new byte[readlimit];
            final int length = stream.read(buf);
            return intGetAudioFormat(buf, length, fileTypeHint);
        } finally {
            stream.reset();
        }
    }

    @Override
    public AudioFileFormat getAudioFileFormat(final File file) throws UnsupportedAudioFileException, IOException {
        if (!file.exists()) throw new FileNotFoundException(file.toString());
        if (!file.canRead()) throw new IOException("Can't read " + file);
        return getAudioFileFormat(fileToURL(file));
    }

    /**
     * Convert file to URL. Assumes that any punctuation in the filename needs to be url encoded.
     *
     * @param file file
     * @return correctly encoded URL
     * @throws MalformedURLException if the URL is malformed
     */
    static URL fileToURL(final File file) throws MalformedURLException {
        /*
        punct: ",;:$&+="
        reserved "?/[]@"
        */

        try {
            final String url = file.toURI().toASCIIString();
            final StringBuilder finalURL = new StringBuilder(url.length());
            boolean firstColon = true;
            for (final char c : url.toCharArray()) {
                switch (c) {
                    case ':':
                        if (firstColon) {
                            firstColon = false;
                            finalURL.append(c);
                            continue;
                        }
                    case ',':
                    case ';':
                    case '$':
                    case '&':
                    case '+':
                    case '=':
                    case '?':
                    case '[':
                    case ']':
                    case '@':
                        finalURL.append(URLEncoder.encode(String.valueOf(c), "UTF-8"));
                        break;
                    default:
                        finalURL.append(c);
                }
            }
            return new URL(finalURL.toString());
        } catch (UnsupportedEncodingException e) {
            final MalformedURLException malformedURLException = new MalformedURLException();
            malformedURLException.initCause(e);
            throw malformedURLException;
        }
    }

    @Override
    public AudioFileFormat getAudioFileFormat(final URL url) throws UnsupportedAudioFileException, IOException {
        if (!nativeLibraryLoaded) throw new UnsupportedAudioFileException("Native library casampledsp not loaded.");
        final AudioFileFormat fileFormat = getAudioFileFormatFromCache(url);
        if (fileFormat != null) {
            return fileFormat;
        }
        final AudioFileFormat audioFileFormat;
        if (isFile(url)) {
            audioFileFormat = intGetAudioFormat(url.toString());
        } else {
            final URLConnection urlConnection = url.openConnection();
            urlConnection.setConnectTimeout(connectTimeout);
            final String contentType = urlConnection.getContentType();
            final InputStream stream = buffer(url.openStream());
            audioFileFormat = getAudioFileFormat(stream, toFileTypeHint(contentType));
        }
        if (audioFileFormat != null) {
            addAudioAudioFileFormatToCache(url, audioFileFormat);
        }
        return audioFileFormat;
    }

    /**
     * Tries to guess the file type hint from the mime content-type.
     *
     * @param contentType content-type
     * @return specific hint or {@code 0}, if we don't really know what it is.
     */
    public int toFileTypeHint(final String contentType) {
        if (contentType == null || contentType.isEmpty()) return 0;
        final int semiColon = contentType.indexOf(';');
        final String rawType = semiColon >= 0 ? contentType.substring(0, semiColon) : contentType;
        final String trimmedLowercase = rawType.trim().toLowerCase();
        if ("audio/mpeg".equals(trimmedLowercase)) return toEnum("MPG3");
        if ("audio/mp4".equals(trimmedLowercase)) return toEnum("m4af");
        if ("audio/vnd.wave".equals(trimmedLowercase)) return toEnum("WAVE");
        return 0;
    }

    @Override
    public AudioInputStream getAudioInputStream(final InputStream stream) throws UnsupportedAudioFileException, IOException {
        return getAudioInputStream(stream, CANativePeerInputStream.DEFAULT_BUFFER_SIZE);
    }

    public AudioInputStream getAudioInputStream(final InputStream stream, final int bufferSize) throws UnsupportedAudioFileException, IOException {
        if (!nativeLibraryLoaded) throw new UnsupportedAudioFileException("Native library casampledsp not loaded.");
        final AudioFileFormat fileFormat = getAudioFileFormat(stream);
        return new CAAudioInputStream(new CAStreamInputStream(stream, 0, bufferSize), fileFormat.getFormat(), fileFormat.getFrameLength());
    }

    @Override
    public AudioInputStream getAudioInputStream(final URL url) throws UnsupportedAudioFileException, IOException {
        return getAudioInputStream(url, CANativePeerInputStream.DEFAULT_BUFFER_SIZE);
    }

    public AudioInputStream getAudioInputStream(final URL url, final int bufferSize) throws UnsupportedAudioFileException, IOException {
        if (!nativeLibraryLoaded) throw new UnsupportedAudioFileException("Native library casampledsp not loaded.");
        final AudioFileFormat fileFormat;
        final CANativePeerInputStream stream;
        if (isFile(url)) {
            fileFormat = getAudioFileFormat(url);
            stream = new CAURLInputStream(url);
        } else {
            final URLConnection urlConnection = url.openConnection();
            urlConnection.setConnectTimeout(connectTimeout);
            final String contentType = urlConnection.getContentType();
            final InputStream rawStream = buffer(url.openStream());
            final int fileTypeHint = toFileTypeHint(contentType);
            fileFormat = getAudioFileFormat(rawStream, fileTypeHint);
            stream = new CAStreamInputStream(rawStream, fileTypeHint, bufferSize);
        }
        return new CAAudioInputStream(stream, fileFormat.getFormat(), fileFormat.getFrameLength());
    }

    @Override
    public AudioInputStream getAudioInputStream(final File file) throws UnsupportedAudioFileException, IOException {
        return getAudioInputStream(file, CANativePeerInputStream.DEFAULT_BUFFER_SIZE);
    }

    public AudioInputStream getAudioInputStream(final File file, final int bufferSize) throws UnsupportedAudioFileException, IOException {
        if (!file.exists()) throw new FileNotFoundException(file.toString());
        if (!file.canRead()) throw new IOException("Can't read " + file);
        return getAudioInputStream(fileToURL(file), bufferSize);
    }

    /**
     * Wrap inputstream to make sure we can {@link java.io.InputStream#mark(int)}.
     *
     * @param in in
     * @return buffered in
     */
    private InputStream buffer(final InputStream in) {
        return !in.markSupported() ? new BufferedInputStream(in, 32 * 1024) : in;
    }

    /**
     * Indicates whether the given url is pointing to a file.
     *
     * @param url url
     * @return true or false
     */
    private static boolean isFile(final URL url) {
        return url != null && "file".equals(url.getProtocol());
    }

    /**
     * Converts a four char, 'MPG3'-type-enum into an {@code int}.
     *
     * @param s four char string
     * @return {@code c[0] << 24 | c[1] << 16 | c[2] << 8 | c[3]}
     */
    private static int toEnum(final String s) {
        final char[] c = s.toCharArray();
        return c[0] << 24 | c[1] << 16 | c[2] << 8 | c[3];
    }

    private native AudioFileFormat intGetAudioFormat(final String url) throws IOException;
    private native AudioFileFormat intGetAudioFormat(final byte[] buf, final int length, final int fileTypeHint) throws IOException;

}

