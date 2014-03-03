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

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
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

    private static Map<URL, AudioFileFormat> cache = Collections.synchronizedMap(new LinkedHashMap<URL, AudioFileFormat>() {
        private static final int MAX_ENTRIES = 20;

        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > MAX_ENTRIES;
        }
    });

    private static void addAudioAudioFileFormatToCache(final URL url, final AudioFileFormat audioFileFormat) {
        cache.put(url, audioFileFormat);
    }

    private static AudioFileFormat getAudioFileFormatFromCache(final URL url) {
        return cache.get(url);
    }

    @Override
    public AudioFileFormat getAudioFileFormat(final InputStream stream) throws UnsupportedAudioFileException, IOException {
        if (!nativeLibraryLoaded) throw new UnsupportedAudioFileException("Native library casampledsp not loaded.");
        if (!stream.markSupported()) throw new IOException("InputStream must support mark()");
        final int readlimit = 1024 * 32;
        stream.mark(readlimit);
        try {
            final byte[] buf = new byte[readlimit];
            final int length = stream.read(buf);
            return intGetAudioFormat(buf, length);
        } finally {
            stream.reset();

        }
    }

    @Override
    public AudioFileFormat getAudioFileFormat(final File file) throws UnsupportedAudioFileException, IOException {
        if (!file.exists()) throw new FileNotFoundException(file.toString());
        if (!file.canRead()) throw new IOException("Can't read " + file.toString());
        return getAudioFileFormat(fileToURL(file));
    }

    /**
     * Convert file to URL. Assumes that any punctuation in the filename needs to be url encoded.
     *
     * @param file file
     * @return correctly encoded URL
     * @throws MalformedURLException
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
            audioFileFormat = getAudioFileFormat(url.openStream());
        }
        if (audioFileFormat != null) {
            addAudioAudioFileFormatToCache(url, audioFileFormat);
        }
        return audioFileFormat;
    }

    @Override
    public AudioInputStream getAudioInputStream(final InputStream stream) throws UnsupportedAudioFileException, IOException {
        if (!nativeLibraryLoaded) throw new UnsupportedAudioFileException("Native library casampledsp not loaded.");
        final AudioFileFormat fileFormat = getAudioFileFormat(stream);
        return new CAAudioInputStream(new CAStreamInputStream(stream), fileFormat.getFormat(), fileFormat.getFrameLength());
    }

    @Override
    public AudioInputStream getAudioInputStream(final URL url) throws UnsupportedAudioFileException, IOException {
        if (!nativeLibraryLoaded) throw new UnsupportedAudioFileException("Native library casampledsp not loaded.");
        final AudioFileFormat fileFormat;
        final CANativePeerInputStream stream;
        if (isFile(url)) {
            fileFormat = getAudioFileFormat(url);
            stream = new CAURLInputStream(url);
        } else {
            final InputStream rawStream = url.openStream();
            fileFormat = getAudioFileFormat(rawStream);
            stream = new CAStreamInputStream(rawStream);
        }
        return new CAAudioInputStream(stream, fileFormat.getFormat(), fileFormat.getFrameLength());
    }

    @Override
    public AudioInputStream getAudioInputStream(final File file) throws UnsupportedAudioFileException, IOException {
        if (!file.exists()) throw new FileNotFoundException(file.toString());
        if (!file.canRead()) throw new IOException("Can't read " + file.toString());
        return getAudioInputStream(fileToURL(file));
    }

    private native AudioFileFormat intGetAudioFormat(final String url) throws IOException;
    private native AudioFileFormat intGetAudioFormat(final byte[] buf, final int length) throws IOException;

    private static boolean isFile(final URL url) {
        return "file".equals(url.getProtocol());
    }


}

