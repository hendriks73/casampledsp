/*
 * =================================================
 * Copyright 2021 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.casampledsp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * TestCAAudioFormat.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class TestCAAudioFormat {

    @Test
    public void testMissingDataFormat() {
        final CAAudioFormat.CAEncoding encoding = CAAudioFormat.CAEncoding.getInstance(1234567890);
        assertNotNull(encoding);
        assertEquals(1234567890, encoding.getDataFormat());
    }
}
