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

import static javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;
import static org.junit.Assert.*;

/**
 * TestAudioSystemIntegration.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class TestAudioSystemIntegration {

    @Test
    public void testAudioFileReader() throws IOException, UnsupportedAudioFileException {
        final String filename = "test.mp3";
        final File file = File.createTempFile("testAudioFileReader", filename);
        extractFile(filename, file);

        int bytesRead = 0;
        try (final AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
            int justRead;
            final byte[] buf = new byte[1024];
            while ((justRead = in.read(buf)) != -1) {
                assertTrue(justRead > 0);
                bytesRead += justRead;
            }
        } finally {
            file.delete();
        }
        System.out.println("Bytes read: " + bytesRead);
        assertEquals(73352, bytesRead);
    }

    @Test
    public void testAudioFileReader2() throws IOException, UnsupportedAudioFileException {
        final String filename = "test.mp3";
        final File file = File.createTempFile("testAudioFileReader", filename);
        extractFile(filename, file);

        final AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat(file);
        final long duration = (Long)audioFileFormat.getProperty("duration");
        final AudioFormat sourceFormat = audioFileFormat.getFormat();
        final AudioFormat targetFormat = new AudioFormat(PCM_SIGNED, 44100f, 16, 2, 2, 44100f, true);
        // calculate expected bytes based on duration and target format
        final int expectedBytes = (int)Math.ceil(sourceFormat.getSampleRate() * duration / 1000L / 1000L * targetFormat.getChannels() * targetFormat.getFrameSize());
        int bytesRead = 0;
        try (final AudioInputStream mp3Stream = AudioSystem.getAudioInputStream(file)) {
            final AudioInputStream in = AudioSystem.getAudioInputStream(PCM_SIGNED, mp3Stream);
            int justRead;
            final byte[] buf = new byte[1024];
            while ((justRead = in.read(buf)) != -1) {
                assertTrue(justRead > 0);
                bytesRead += justRead;
            }
        } finally {
            file.delete();
        }
        System.out.println("Bytes read: " + bytesRead);
        assertEquals(expectedBytes, bytesRead);
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
