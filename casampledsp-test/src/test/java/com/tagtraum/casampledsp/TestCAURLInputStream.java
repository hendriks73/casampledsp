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
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * TestCAURLInputStream.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class TestCAURLInputStream {

    @Test
    public void testReadThroughMP3File() throws IOException, UnsupportedAudioFileException {
        readThroughFile("testReadThroughMP3File", "test.mp3");
    }

    @Test
    public void testReadThroughVBRMP3File() throws IOException, UnsupportedAudioFileException {
        readThroughFile("testReadThroughVBRMP3File", "test_vbr130.mp3");
    }

    @Test
    public void testReadThroughCBRMP3File() throws IOException, UnsupportedAudioFileException {
        readThroughFile("testReadThroughCBRMP3File", "test_cbr256.mp3");
    }

    @Test
    public void testReadThroughM4AFile() throws IOException, UnsupportedAudioFileException {
        readThroughFile("testReadThroughM4AFile", "test.m4a");
    }

    @Test
    public void testReadThroughCBRM4AFile() throws IOException, UnsupportedAudioFileException {
        readThroughFile("testReadThroughCBRM4AFile", "test_cbr.m4a");
    }

    @Test
    public void testReadThroughVBRM4AFile() throws IOException, UnsupportedAudioFileException {
        readThroughFile("testReadThroughVBRM4AFile", "test_vbr.m4a");
    }

    @Test
    public void testReadThrough48kCBRM4AFile() throws IOException, UnsupportedAudioFileException {
        readThroughFile("testReadThrough48kCBRM4AFile", "test_48k_cbr.m4a");
    }

    @Test
    public void testReadThrough48kVBRM4AFile() throws IOException, UnsupportedAudioFileException {
        readThroughFile("testReadThrough48kVBRM4AFile", "test_48k_vbr.m4a");
    }

    @Test
    public void testReadThrough48kWavFile() throws IOException, UnsupportedAudioFileException {
        readThroughFile("testReadThrough48kWavFile", "test_48k.wav");
    }

    @Test
    public void testReadThrough48kAppleLosslessFile() throws IOException, UnsupportedAudioFileException {
        readThroughFile("testReadThrough48kAppleLosslessFile", "test_48k_alac.m4a");
    }

    private void readThroughFile(final String prefix, final String filename) throws IOException, UnsupportedAudioFileException {
        final File file = File.createTempFile(prefix, filename);
        extractFile(filename, file);
        int bytesRead = 0;
        CAURLInputStream in = null;
        try {
            in = new CAURLInputStream(file.toURI().toURL());
            int justRead;
            final byte[] buf = new byte[1024];
            while ((justRead = in.read(buf)) != -1) {
                assertTrue(justRead > 0);
                bytesRead += justRead;
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            file.delete();
        }
        System.out.println("Read " + bytesRead + " bytes.");
    }

    @Test
    public void testStressOpenClose() throws IOException, UnsupportedAudioFileException {
        final List<String> list = Arrays.asList(
            "test.aiff", "test.m4a", "test.mp3",
            "test_cbr.m4a", "test_cbr256.mp3",
            "test_vbr.m4a", "test_vbr130.mp3",
            "test_48k_alac.m4a"
            );
        for (final String f : list) {
            final File file = File.createTempFile("testStressOpenClose", f);
            extractFile(f, file);
            try {
                for (int i=0; i<100; i++) {
                    CAURLInputStream in = null;
                    try {
                        in = new CAURLInputStream(file.toURI().toURL());
                    } finally {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } finally {
                file.delete();
            }
        }
    }

    @Test
    public void testReadThroughWaveFile() throws IOException, UnsupportedAudioFileException {
        final String filename = "test.wav";
        final File file = File.createTempFile("testReadThroughWaveFile", filename);
        extractFile(filename, file);

        // pre-computed reference values index 1024-50 to 1024 (excl.)
        final int[] referenceValues = new int[]{240, 255, 230, 255, 230, 255, 232, 255, 232, 255, 247, 255, 247, 255, 246, 255, 246, 255, 235, 255, 235, 255, 250, 255, 250, 255, 13, 0, 13, 0, 15, 0, 15, 0, 39, 0, 39, 0, 87, 0, 87, 0, 90, 0, 90, 0, 31, 0, 31, 0};

        int bytesRead = 0;
        CAURLInputStream in = null;
        try {
            in = new CAURLInputStream(file.toURI().toURL());
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
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            file.delete();
        }
        assertEquals(133632, (bytesRead / 4));
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
        CAURLInputStream in = null;
        try {
            in = new CAURLInputStream(file.toURI().toURL());
            in.read(new byte[1024]);
            fail("Expected UnsupportedAudioFileException");
        } catch (UnsupportedAudioFileException e) {
            // expected this
            e.printStackTrace();
            assertTrue(e.toString().endsWith("(typ?)"));
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            file.delete();
        }
    }


    @Test
    public void testNonExistingFile() throws IOException, UnsupportedAudioFileException {
        CAURLInputStream in = null;
        try {
            in = new CAURLInputStream(new File("/Users/hendrik/bcisdbvigfeir.wav").toURI().toURL());
            in.read(new byte[1024]);
            fail("Expected FileNotFoundException");
        } catch (FileNotFoundException e) {
            // expected this
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Test
    public void testNonExistingURL() throws IOException, UnsupportedAudioFileException {
        CAURLInputStream in = null;
        try {
            in = new CAURLInputStream(new URL("http://www.bubu.de/hendrik/bcisdbvigfeir.wav"));
            in.read(new byte[1024]);
            fail("Expected FileNotFoundException");
        } catch (FileNotFoundException e) {
            // expected this
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Test
    public void testSeekBackwards() throws IOException, UnsupportedAudioFileException {
        final String filename = "test.wav";
        final File file = File.createTempFile("testSeekBackwards", filename);
        extractFile(filename, file);
        CAURLInputStream in = null;
        try {
            in = new CAURLInputStream(file.toURI().toURL());
            assertTrue(in.isSeekable());
            in.read(new byte[1024 * 4]);
            in.seek(0, TimeUnit.MICROSECONDS);
            in.read(new byte[1024 * 4]);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            file.delete();
        }
    }

    @Test
    public void testSeekForwards() throws IOException, UnsupportedAudioFileException {
        final String filename = "test.mp3";
        final File file = File.createTempFile("testSeekForwards", filename);
        extractFile(filename, file);
        CAURLInputStream in = null;
        try {
            in = new CAURLInputStream(file.toURI().toURL());
            assertTrue(in.isSeekable());
            in.read(new byte[1024 * 4]);
            in.seek(1, TimeUnit.SECONDS);
            in.read(new byte[1024 * 4]);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            file.delete();
        }
    }

    private void extractFile(final String filename, final File file) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = getClass().getResourceAsStream(filename);
            out = new FileOutputStream(file);
            final byte[] buf = new byte[1024*64];
            int justRead;
            while ((justRead = in.read(buf)) != -1) {
                out.write(buf, 0, justRead);
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Test(expected = IOException.class)
    public void testSeekClosedFile() throws IOException, UnsupportedAudioFileException {
        final String filename = "test.mp3";
        final File file = File.createTempFile("testSeekClosedFile", filename);
        extractFile(filename, file);
        int bytesRead = 0;
        CAURLInputStream in = null;
        try {
            in = new CAURLInputStream(file.toURI().toURL());
            final byte[] buf = new byte[1024];
            in.read(buf);
            in.close();
            in.seek(1, TimeUnit.SECONDS);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            file.delete();
        }
    }
}
