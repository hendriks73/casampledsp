/*
 * =================================================
 * Copyright 2011 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.casampledsp;

import org.junit.Test;

import javax.sound.sampled.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * TestCAAudioFileReader.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class TestCAAudioFileReader {

    @Test
    public void testGetAudioFileFormatFileAIFF() throws IOException, UnsupportedAudioFileException {
        final String filename = "test.aiff"; // apple lossless
        final File file = File.createTempFile("testGetAudioFileFormatAIFFFile", filename);
        extractFile(filename, file);
        try {
            final AudioFileFormat fileFormat = new CAAudioFileReader().getAudioFileFormat(file);
            System.out.println(fileFormat);

            assertEquals("aiff", fileFormat.getType().getExtension());
            assertEquals(file.length(), fileFormat.getByteLength());
            assertEquals(55484, fileFormat.getFrameLength());

            final AudioFormat format = fileFormat.getFormat();
            assertTrue(format.isBigEndian());
            assertEquals(2, format.getChannels());
            final Long duration = (Long) fileFormat.getProperty("duration");
            assertNotNull(duration);
            assertEquals(1258140, (long) duration);
            assertEquals(44100f, format.getFrameRate(), 0.001f);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testGetAudioFileFormatM4AFile() throws IOException, UnsupportedAudioFileException {
        // first copy the file from resources to actual location in temp
        final String filename = "test.m4a"; // apple lossless
        final File file = File.createTempFile("testGetAudioFileFormatM4AFile", filename);
        extractFile(filename, file);
        try {
            final AudioFileFormat fileFormat = new CAAudioFileReader().getAudioFileFormat(file);
            System.out.println(fileFormat);

            assertEquals("m4a", fileFormat.getType().getExtension());
            assertEquals(file.length(), fileFormat.getByteLength());
            assertEquals(33, fileFormat.getFrameLength());

            final AudioFormat format = fileFormat.getFormat();
            assertEquals(2, format.getChannels());
            final Long duration = (Long)fileFormat.getProperty("duration");
            assertNotNull(duration);
            assertEquals(3065034, (long)duration);
            assertEquals(10.766602f, format.getFrameRate(), 0.001f);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testGetAudioFileFormatMP3File() throws IOException, UnsupportedAudioFileException {
        // first copy the file from resources to actual location in temp
        final String filename = "test.mp3";
        final File file = File.createTempFile("testGetAudioFileFormatMP3File", filename);
        extractFile(filename, file);
        try {
            final AudioFileFormat fileFormat = new CAAudioFileReader().getAudioFileFormat(file);
            System.out.println(fileFormat);

            assertEquals("mp3", fileFormat.getType().getExtension());
            assertEquals(file.length(), fileFormat.getByteLength());
            assertEquals(117, fileFormat.getFrameLength());

            final AudioFormat format = fileFormat.getFormat();
            assertEquals(2, format.getChannels());
            final Long duration = (Long)fileFormat.getProperty("duration");
            assertNotNull(duration);
            assertEquals(3056326, (long)duration);
            assertEquals(38.28125f, format.getFrameRate(), 0.001f);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testGetAudioFileFormatWavFile() throws IOException, UnsupportedAudioFileException {
        // first copy the file from resources to actual location in temp
        final String filename = "test.wav";
        final File file = File.createTempFile("testGetAudioFileFormatWavFile", filename);
        extractFile(filename, file);
        try {
            final AudioFileFormat fileFormat = new CAAudioFileReader().getAudioFileFormat(file);
            System.out.println(fileFormat);

            assertEquals("wav", fileFormat.getType().getExtension());
            assertEquals(file.length(), fileFormat.getByteLength());
            assertEquals(133632, fileFormat.getFrameLength());

            final AudioFormat format = fileFormat.getFormat();
            assertEquals(2, format.getChannels());
            final Long duration = (Long)fileFormat.getProperty("duration");
            assertNotNull(duration);
            assertEquals(3030204, (long)duration);
            assertEquals(44100f, format.getFrameRate(), 0.001f);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testGetAudioFileFormatURL() throws IOException, UnsupportedAudioFileException {
        // first copy the file from resources to actual location in temp
        final String filename = "test.mp3";
        final File file = File.createTempFile("testGetAudioFileFormatURL", filename);
        extractFile(filename, file);
        try {
            final AudioFileFormat fileFormat = new CAAudioFileReader().getAudioFileFormat(file.toURI().toURL());
            System.out.println(fileFormat);

            assertEquals("mp3", fileFormat.getType().getExtension());
            assertEquals(file.length(), fileFormat.getByteLength());
            assertEquals(117, fileFormat.getFrameLength());

            final AudioFormat format = fileFormat.getFormat();
            assertEquals(2, format.getChannels());
            final Long duration = (Long)fileFormat.getProperty("duration");
            assertNotNull(duration);
            assertEquals(3056326, (long)duration);
            assertEquals(38.28125f, format.getFrameRate(), 0.001f);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testGetAudioFileFormatInputStream() throws IOException, UnsupportedAudioFileException {
        // first copy the file from resources to actual location in temp
        final String filename = "test.mp3";
        final File file = File.createTempFile("testGetAudioFileFormatInputStream", filename);
        extractFile(filename, file);
        try {
            final AudioFileFormat fileFormat = new CAAudioFileReader().getAudioFileFormat(new BufferedInputStream(new FileInputStream(file)));
            System.out.println(fileFormat);

            assertEquals("mp3", fileFormat.getType().getExtension());
            assertEquals(AudioSystem.NOT_SPECIFIED, fileFormat.getByteLength());
            assertEquals(AudioSystem.NOT_SPECIFIED, fileFormat.getFrameLength());

            final AudioFormat format = fileFormat.getFormat();
            assertEquals(2, format.getChannels());
            final Long duration = (Long)fileFormat.getProperty("duration");
            assertNull(duration);
            assertEquals(AudioSystem.NOT_SPECIFIED, (int)format.getFrameRate());
        } finally {
            file.delete();
        }
    }

    @Test
    public void testFileLength() throws IOException {
        final String filename = "test.wav";
        final File file = File.createTempFile("testFileLength", filename);
        extractFile(filename, file);
        try {
            final AudioFileFormat audioFileFormat = new CAAudioFileReader().getAudioFileFormat(file.toURI().toURL());
            assertEquals(133632, audioFileFormat.getFrameLength());
        } catch (UnsupportedAudioFileException e) {
            // expected this
            e.printStackTrace();
            assertTrue(e.toString().endsWith("(typ?)"));
        }
    }

    @Test
    public void testBogusFile() throws IOException {
        final String filename = "test.wav";
        final File file = File.createTempFile("testBogusFile", filename);
        FileOutputStream out = new FileOutputStream(file);
        final Random random = new Random();
        for (int i=0; i<8*1024; i++) {
            out.write(random.nextInt());
        }
        out.close();
        try {
            new CAAudioFileReader().getAudioFileFormat(file.toURI().toURL());
            fail("Expected UnsupportedAudioFileException");
        } catch (UnsupportedAudioFileException e) {
            // expected this
            e.printStackTrace();
            assertTrue(e.toString().endsWith("(typ?)"));
        }
    }

    @Test
    public void testFileWithPunctuationToURL() throws MalformedURLException {
        final File file = new File("/someDir/;:&=+@[]?/name.txt");
        final URL url = CAAudioFileReader.fileToURL(file);
        assertEquals("file:/someDir/%3B%3A%26%3D%2B%40%5B%5D%3F/name.txt", url.toString());
    }

    private void extractFile(final String filename, final File file) throws IOException {
        try (final InputStream in = getClass().getResourceAsStream(filename);
             final OutputStream out = new FileOutputStream(file)) {
            final byte[] buf = new byte[1024*64];
            int justRead;
            while ((justRead = in.read(buf)) != -1) {
                out.write(buf, 0, justRead);
            }
        }
    }

    @Test
    public void testSetTimeout() {
        final CAAudioFileReader reader = new CAAudioFileReader();
        reader.setConnectTimeout(500);
        assertEquals(500, reader.getConnectTimeout());
    }

    @Test
    public void testToFileTypeHint() {
        final CAAudioFileReader reader = new CAAudioFileReader();
        assertEquals(1297106739, reader.toFileTypeHint("audio/MPEG"));
        assertEquals(1832149350, reader.toFileTypeHint("audio/mp4"));
        assertEquals(1463899717, reader.toFileTypeHint("audio/vnd.WAVE;something"));
        assertEquals(0, reader.toFileTypeHint("garbage"));
    }

    @Test
    public void testGetAudioInputStreamURL() throws IOException, UnsupportedAudioFileException {
        // first copy the file from resources to actual location in temp
        final String filename = "test.mp3";
        final File file = File.createTempFile("testGetAudioFileFormatURL", filename);
        extractFile(filename, file);
        try {
            final AudioInputStream audioInputStream = new CAAudioFileReader().getAudioInputStream(file.toURI().toURL());
            final AudioFormat format = audioInputStream.getFormat();
            assertEquals(2, format.getChannels());
            assertEquals(38.28125f, format.getFrameRate(), 0.001f);
        } finally {
            file.delete();
        }
    }


    @Test
    public void testGetAudioInputStreamURLAndSeek() throws IOException, UnsupportedAudioFileException {
        // first copy the file from resources to actual location in temp
        final String filename = "test.mp3";
        final File file = File.createTempFile("testGetAudioInputStreamURLAndSeek", filename);
        extractFile(filename, file);
        try {
            final AudioInputStream audioInputStream = new CAAudioFileReader().getAudioInputStream(file.toURI().toURL());
            // try seeking
            if (audioInputStream instanceof CAAudioInputStream) {
                if (((CAAudioInputStream) audioInputStream).isSeekable()) {
                    ((CAAudioInputStream) audioInputStream).seek(20, TimeUnit.SECONDS);
                }
            }
        } finally {
            file.delete();
        }
    }}
