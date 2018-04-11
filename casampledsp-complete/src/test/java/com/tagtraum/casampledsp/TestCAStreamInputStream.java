/*
 * =================================================
 * Copyright 2011 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.casampledsp;

import org.junit.Test;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * TestCAStreamInputStream.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class TestCAStreamInputStream {

    @Test
    public void testReadThroughMP3File() throws IOException, UnsupportedAudioFileException {
        final String filename = "test.mp3";
        final File file = File.createTempFile("testReadThroughMP3File", filename);
        extractFile(filename, file);
        int bytesRead = 0;
        try (final CAStreamInputStream in = new CAStreamInputStream(new FileInputStream(file), 0)) {
            int justRead;
            final byte[] buf = new byte[1024*8];
            while ((justRead = in.read(buf)) != -1) {
                assertTrue(justRead > 0);
                bytesRead += justRead;
            }
        } finally {
            file.delete();
        }
        System.out.println("Read " + bytesRead + " bytes.");
    }

    @Test
    public void testReadThroughWaveFile() throws IOException, UnsupportedAudioFileException {
        final String filename = "test.wav";
        final File file = File.createTempFile("testReadThroughWaveFile", filename);
        extractFile(filename, file);

        // pre-computed reference values index 1024-50 to 1024 (excl.)
        final int[] referenceValues = new int[]{240, 255, 230, 255, 230, 255, 232, 255, 232, 255, 247, 255, 247, 255, 246, 255, 246, 255, 235, 255, 235, 255, 250, 255, 250, 255, 13, 0, 13, 0, 15, 0, 15, 0, 39, 0, 39, 0, 87, 0, 87, 0, 90, 0, 90, 0, 31, 0, 31, 0};

        int bytesRead = 0;
        try (final CAStreamInputStream in = new CAStreamInputStream(new FileInputStream(file), 0)) {
            int justRead;
            final byte[] buf = new byte[1024];
            while ((justRead = in.read(buf)) != -1) {
                assertTrue(justRead > 0);
                bytesRead += justRead;
                if (bytesRead == 1024) {
                    for (int i=0; i<50; i++) {
                        assertEquals(referenceValues[i], buf[i+(1024-50)] & 0xFF);
                    }
                }
            }
        } finally {
            file.delete();
        }
        assertEquals(133632, (bytesRead / 4));
    }

    @Test
    public void testBogusStream() throws IOException {
        final String filename = "test.wav";
        final File file = File.createTempFile("testBogusFile", filename);
        FileOutputStream out = new FileOutputStream(file);
        final Random random = new Random();
        for (int i=0; i<8*1024; i++) {
            out.write(random.nextInt());
        }
        out.close();
        try (final CAStreamInputStream in = new CAStreamInputStream(new FileInputStream(file), 0)) {
            in.read(new byte[1024]);
            fail("Expected UnsupportedAudioFileException");
        } catch (UnsupportedAudioFileException e) {
            // expected this
            assertTrue(e.toString().endsWith("(typ?)"));
        } finally {
            file.delete();
        }
    }

    @Test
    public void testNotSeekable() throws IOException, UnsupportedAudioFileException {
        final String filename = "test.wav";
        final File file = File.createTempFile("testNotSeekable", filename);
        extractFile(filename, file);
        try (final CAStreamInputStream in = new CAStreamInputStream(new FileInputStream(file), 0)) {
            assertFalse(in.isSeekable());
            in.read(new byte[1024 * 4]);
            in.seek(0, TimeUnit.MICROSECONDS);

        } catch (UnsupportedOperationException e) {
            // expected this
            e.printStackTrace();
        } finally {
            file.delete();
        }
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
}
