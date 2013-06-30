/*
 * =================================================
 * Copyright 2011 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.casampledsp;

import org.junit.Test;

import javax.sound.sampled.AudioFormat;

import static org.junit.Assert.assertEquals;

/**
 * TestCAFormatConversionProvider.
 * <p/>
 * Date: 8/25/11
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
}
