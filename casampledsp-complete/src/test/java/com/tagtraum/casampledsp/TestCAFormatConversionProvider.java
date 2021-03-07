/*
 * =================================================
 * Copyright 2011 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.casampledsp;

import org.junit.Test;

import javax.sound.sampled.AudioFormat;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * TestCAFormatConversionProvider.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class TestCAFormatConversionProvider {

    @Test
    public void testGetTargetFormats() {
        final AudioFormat[] targetFormats = new CAFormatConversionProvider().getTargetFormats(AudioFormat.Encoding.PCM_SIGNED, new AudioFormat(CAAudioFormat.CAEncoding.MP3, 22050f, 16, 2, -1, -1, true));
        System.out.println("Formats: " + targetFormats.length);
        assertEquals(8, targetFormats.length);
    }

    @Test
    public void testGetTargetFormatsUnsupportedEncoding() {
        final AudioFormat[] targetFormats = new CAFormatConversionProvider().getTargetFormats(AudioFormat.Encoding.PCM_UNSIGNED, new AudioFormat(CAAudioFormat.CAEncoding.MP3, 22050f, 16, 2, -1, -1, true));
        System.out.println("Formats: " + targetFormats.length);
        assertEquals(0, targetFormats.length);
    }

    @Test
    public void testGetTargetEncodings() {
        final AudioFormat.Encoding[] targetEncodings = new CAFormatConversionProvider().getTargetEncodings();
        assertArrayEquals(new AudioFormat.Encoding[] {CAAudioFormat.CAEncoding.PCM_SIGNED}, targetEncodings);
    }
    
    @Test
    public void testGetSourceEncodings() {
        final AudioFormat.Encoding[] sourceEncodings = new CAFormatConversionProvider().getSourceEncodings();
        final Set<CAAudioFormat.CAEncoding> supportedEncodings = CAAudioFormat.CAEncoding.getSupportedEncodings();
        for (final AudioFormat.Encoding encoding : sourceEncodings) {
            assertTrue(supportedEncodings.contains(encoding));
        }
    }

}
