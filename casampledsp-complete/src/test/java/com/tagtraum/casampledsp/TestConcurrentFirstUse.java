/*
 * =================================================
 * Copyright 2026 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.casampledsp;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import org.junit.Test;

/**
 * Decodes from several threads at once in a fresh JVM, so that the very first native calls happen
 * concurrently. Before JNI IDs were cached in the static initializer of {@link
 * CANativePeerInputStream}, they were set lazily on first use, and a thread could see a {@code
 * nullptr} ID and crash the JVM.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class TestConcurrentFirstUse {

  private static final int JVM_RUNS = 10;
  private static final int THREADS = 8;
  private static final int CHILD_TIMEOUT_SECONDS = 60;
  private static final int BARRIER_TIMEOUT_SECONDS = 10;
  private static final String[] FILES = {"test.m4a", "test.mp3", "test.aiff", "test.wav"};

  @Test
  public void testConcurrentFirstUseInFreshJVM() throws Exception {
    final File dir = Files.createTempDirectory("TestConcurrentFirstUse").toFile();
    try {
      for (final String filename : FILES) {
        try (final InputStream in = getClass().getResourceAsStream(filename)) {
          Files.copy(in, new File(dir, filename).toPath());
        }
      }
      final String java =
          System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
      final File output = new File(dir, "output.txt");
      for (int run = 0; run < JVM_RUNS; run++) {
        final Process process =
            new ProcessBuilder(
                    java,
                    "-Djava.awt.headless=true",
                    "-Djava.io.tmpdir=" + dir,
                    "-XX:ErrorFile=" + new File(dir, "hs_err_pid%p.log"),
                    "-cp",
                    System.getProperty("java.class.path"),
                    TestConcurrentFirstUse.class.getName(),
                    dir.toString())
                .redirectErrorStream(true)
                .redirectOutput(output)
                .start();
        try {
          final boolean exited = process.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
          final String message = readOutputAndCrashLogs(dir, output);
          assertTrue("Child JVM timed out in run " + run + ":\n" + message, exited);
          assertEquals("Child JVM failed in run " + run + ":\n" + message, 0, process.exitValue());
        } finally {
          process.destroyForcibly();
        }
      }
    } finally {
      deleteRecursively(dir);
    }
  }

  /** Collects the child's output plus any JVM crash logs, before the temp dir is deleted. */
  private static String readOutputAndCrashLogs(final File dir, final File output)
      throws IOException {
    final StringBuilder sb =
        new StringBuilder(new String(Files.readAllBytes(output.toPath()), "UTF-8"));
    final File[] crashLogs = dir.listFiles((d, name) -> name.startsWith("hs_err_pid"));
    if (crashLogs != null) {
      for (final File crashLog : crashLogs) {
        sb.append("\n--- ").append(crashLog.getName()).append(" ---\n");
        sb.append(new String(Files.readAllBytes(crashLog.toPath()), "UTF-8"));
        crashLog.delete();
      }
    }
    return sb.toString();
  }

  private static void deleteRecursively(final File file) {
    final File[] children = file.listFiles();
    if (children != null) {
      for (final File child : children) {
        deleteRecursively(child);
      }
    }
    file.delete();
  }

  /**
   * Child JVM entry point: start {@link #THREADS} threads that simultaneously open, convert and
   * fully read audio files. Threads are lined up twice, before opening and before the first read,
   * so that the first native calls of each kind overlap as much as possible. Each file is read by
   * one thread via a file (i.e. {@link CAURLInputStream}) and by another via an {@link InputStream}
   * (i.e. {@link CAStreamInputStream}).
   *
   * @param args directory containing the test files
   */
  public static void main(final String[] args) throws Exception {
    final File dir = new File(args[0]);
    final CyclicBarrier barrier = new CyclicBarrier(THREADS);
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      final File file = new File(dir, FILES[i % FILES.length]);
      final boolean viaStream = (i / FILES.length) % 2 == 1;
      final Thread thread =
          new Thread(
              () -> {
                try {
                  decode(file, viaStream, barrier);
                } catch (Throwable t) {
                  failure.compareAndSet(null, t);
                  // release the other threads instead of leaving them waiting forever
                  barrier.reset();
                }
              });
      thread.start();
      threads.add(thread);
    }
    for (final Thread thread : threads) {
      thread.join();
    }
    if (failure.get() != null) {
      failure.get().printStackTrace();
      System.exit(1);
    }
    System.exit(0);
  }

  private static void decode(final File file, final boolean viaStream, final CyclicBarrier barrier)
      throws Exception {
    final AudioFormat targetFormat =
        new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 2, 4, 44100f, false);
    barrier.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    try (final AudioInputStream source =
            viaStream
                ? new CAAudioFileReader()
                    .getAudioInputStream(new BufferedInputStream(new FileInputStream(file)))
                : new CAAudioFileReader().getAudioInputStream(file);
        final AudioInputStream target =
            new CAFormatConversionProvider().getAudioInputStream(targetFormat, source)) {
      barrier.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      final byte[] buf = new byte[8192];
      long total = 0;
      int justRead;
      while ((justRead = target.read(buf)) != -1) {
        total += justRead;
      }
      if (total == 0) {
        throw new IOException("No audio decoded from " + file);
      }
    }
  }
}
