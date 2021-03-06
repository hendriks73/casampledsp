/*
 * =================================================
 * Copyright 2021 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.casampledsp;

import org.junit.Test;

import java.io.*;

import static com.tagtraum.casampledsp.CANativeLibraryLoader.decodeURL;
import static org.junit.Assert.*;

/**
 * TestCANativeLibraryLoader.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class TestCANativeLibraryLoader {

    @Test(expected = FileNotFoundException.class)
    public void testFindNonExistingFile() throws FileNotFoundException {
        CANativeLibraryLoader.findFile("testFindFile", CANativeLibraryLoader.class, new FileFilter() {
            @Override
            public boolean accept(final File pathname) {
                return false;
            }
        });
    }

    @Test
    public void testFindExistingFile() throws IOException {
        final File directory = CANativeLibraryLoader.getClasspathOrJarDir(CANativeLibraryLoader.class);
        final File tempFile = File.createTempFile("findFileTest", "lib", directory);
        tempFile.deleteOnExit();
        final String testFindFile = CANativeLibraryLoader.findFile("testFindFile", CANativeLibraryLoader.class, new FileFilter() {
            @Override
            public boolean accept(final File pathname) {
                return tempFile.equals(pathname);
            }
        });
        assertEquals(testFindFile, tempFile.toString());
    }

    @Test
    public void testLibFileFilter() throws IOException {
        final CANativeLibraryLoader.LibFileFilter mylib = new CANativeLibraryLoader.LibFileFilter("mylib");
        assertFalse(mylib.accept(new File("slnnfl")));
        assertFalse(mylib.accept(new File("mylib.dylib")));
        final File tempFile = File.createTempFile("mylib", ".dylib");
        tempFile.deleteOnExit();
        assertTrue(mylib.accept(tempFile));
    }

    @Test
    public void testDecodeURL() {
        assertEquals("someString", decodeURL("someString"));
        assertEquals("someString some", decodeURL("someString%20some"));
        assertEquals("  ", decodeURL("%20%20"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecodeURLIncompleteTrailingEscapePattern() {
        decodeURL("someString%h");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecodeURLIllegalHex() {
        decodeURL("someString%ah");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecodeURLNegativeValue() {
        decodeURL("someString%-1");
    }
}
