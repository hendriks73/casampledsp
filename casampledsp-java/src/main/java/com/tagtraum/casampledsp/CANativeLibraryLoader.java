/*
 * =================================================
 * Copyright 2007 tagtraum industries incorporated
 * This file is part of CASampledSP.
 *
 * CASampledSP is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * CASampledSP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with CASampledSP; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * =================================================
 */
package com.tagtraum.casampledsp;

import java.awt.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * First tries to load a library the default way using {@link System#loadLibrary(String)},
 * upon failure falls back to the base directory of the given class package or the jar the class
 * is in. This way, a native library is found, if it is located in the same directory as a particular jar, identified
 * by a specific class from that jar.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public final class CANativeLibraryLoader {

    static {
        // because of https://bugs.openjdk.java.net/browse/JDK-8013560,
        // we have to load the toolkit first.

        // perhaps this problem was also caused by
        // https://bugs.openjdk.java.net/browse/JDK-8068412
        Toolkit.getDefaultToolkit();
    }

    private static final String JAR_PROTOCOL = "jar";
    private static final String FILE_PROTOCOL = "file";
    private static final String CLASS_FILE_EXTENSION = ".class";
    private static final String NATIVE_LIBRARY_EXTENSION = ".dylib";
    private static final String NATIVE_LIBRARY_PREFIX = "lib";
    private static final Set<String> LOADED = new HashSet<>();
    private static final String VERSION = readProjectVersion();

    private static Boolean caSampledSPLibraryLoaded;

    private CANativeLibraryLoader() {
    }

    /**
     * Loads the CASampledSP library.
     *
     * @return true, if loading was successful
     */
    public static synchronized boolean loadLibrary() {
        if (caSampledSPLibraryLoaded != null) {
            return caSampledSPLibraryLoaded;
        }
        boolean loaded = false;
        try {
            final String arch = System.getProperty("os.arch");
            if ("aarch64".equals(arch) || "arm64".equals(arch)) {
                CANativeLibraryLoader.loadLibrary("casampledsp-aarch64");
            } else {
                CANativeLibraryLoader.loadLibrary("casampledsp-x86_64");
            }
            loaded = true;
        } catch (Error e) {
            Logger.getLogger(CANativeLibraryLoader.class.getName()).severe("Failed to load native library 'casampledsp'. Please check your library path. CASampledSP will be dysfunctional.");
        }
        caSampledSPLibraryLoaded = loaded;
        return caSampledSPLibraryLoaded;
    }

    /**
     * Loads a library.
     *
     * @param libName name of the library, as described in {@link System#loadLibrary(String)} );
     */
    public static synchronized void loadLibrary(final String libName) {
        loadLibrary(libName, CANativeLibraryLoader.class);
    }

    /**
     * Loads a library.
     *
     * @param libName name of the library, as described in {@link System#loadLibrary(String)} );
     * @param baseClass class that identifies the jar
     */
    public static synchronized void loadLibrary(final String libName, final Class<?> baseClass) {
        final String key = libName + "|" + baseClass.getName();
        if (LOADED.contains(key)) return;
        final String packagedNativeLib = libName + "-" + VERSION + NATIVE_LIBRARY_EXTENSION;
        final File extractedNativeLib = new File(System.getProperty("java.io.tmpdir") + "/" + packagedNativeLib);
        if (!extractedNativeLib.exists() || extractedNativeLib.toString().contains("SNAPSHOT")) {
            extractResourceToFile(baseClass, "/" + packagedNativeLib, extractedNativeLib);
        }
        if (extractedNativeLib.exists()) {
            try {
                Runtime.getRuntime().load(extractedNativeLib.toString());
                LOADED.add(key);
                return;
            } catch (Error e) {
                // failed to extract and load, will try other ways
            }
        }
        try {
            System.loadLibrary(libName);
            LOADED.add(key);
        } catch (Error e) {
            try {
                final String libFilename = findFile(libName, baseClass, new LibFileFilter(libName));
                Runtime.getRuntime().load(libFilename);
                LOADED.add(key);
            } catch (FileNotFoundException e1) {
                throw e;
            }
        }
    }

    /**
     * Extracts the given resource and writes it to the specified file.
     * Note that this method fails silently.
     *
     * @param baseClass class to use as base class for the resource lookup
     * @param sourceResource resource name
     * @param targetFile target file
     */
    private static void extractResourceToFile(final Class<?> baseClass, final String sourceResource, final File targetFile) {
        try (final InputStream in = baseClass.getResourceAsStream(sourceResource)) {
            if (in != null) {
                try (final OutputStream out = new FileOutputStream(targetFile)) {
                    final byte[] buf = new byte[1024 * 8];
                    int justRead;
                    while ((justRead = in.read(buf)) != -1) {
                        out.write(buf, 0, justRead);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Finds a file that is either in the classpath or in the same directory as a given class's jar.
     *
     * @param name (partial) filename, only used for error reporting
     * @param baseClass base class
     * @param filter filter that determines whether a file is a match
     * @return file
     * @throws java.io.FileNotFoundException if a matching file cannot be found
     */
    public static String findFile(final String name, final Class<?> baseClass, final FileFilter filter)
            throws FileNotFoundException {
        try {
            final File directory = getClasspathOrJarDir(baseClass);
            final File[] libs = directory.listFiles(filter);
            if (libs == null || libs.length == 0) {
                throw new FileNotFoundException("No matching files in " + directory);
            }
            return libs[0].toString();
        } catch (UnsupportedEncodingException | MalformedURLException e) {
            final FileNotFoundException fnfe = new FileNotFoundException(name + ": " + e);
            fnfe.initCause(e);
            throw fnfe;
        }
    }

    /**
     * Return the classpath or the directory of the JAR of the given class.
     *
     * @param baseClass base class
     * @return classpath or the directory of the JAR of the given class.
     */
    public static File getClasspathOrJarDir(final Class<?> baseClass) throws UnsupportedEncodingException, MalformedURLException, FileNotFoundException {
        File directory = null;
        final URL url = baseClass.getResource(baseClass.getSimpleName() + CLASS_FILE_EXTENSION);
        if (url == null) {
            throw new FileNotFoundException("Failed to get URL of " + baseClass.getName());
        }
        final String path = decodeURL(url.getPath());
        if (JAR_PROTOCOL.equals(url.getProtocol())) {
            final String jarFileName = new URL(path.substring(0, path.lastIndexOf('!'))).getPath();
            directory = new File(jarFileName).getParentFile();
        } else if (FILE_PROTOCOL.equals(url.getProtocol())) {
            directory = new File(path.substring(0, path.length()
                    - baseClass.getName().length() - CLASS_FILE_EXTENSION.length()));
        }
        return directory;
    }

    public static class LibFileFilter implements FileFilter {
        private final String libName;

        public LibFileFilter(final String libName) {
            this.libName = libName;
        }

        public boolean accept(final File file) {
            final String fileString = file.toString();
            final String fileName = file.getName();
            return file.isFile()
                    && (fileName.startsWith(libName) || fileName.startsWith(NATIVE_LIBRARY_PREFIX + libName))
                    && fileString.endsWith(NATIVE_LIBRARY_EXTENSION);
        }
    }

    /**
     * Decode % encodings in URLs.
     * The common {@link java.net.URLDecoder#decode(String, String)} method also converts {@code +} to {@code space},
     * which is not what we want.
     *
     * @param s url
     * @return decoded URL
     */
    public static String decodeURL(final String s) {
        boolean needToChange = false;
        final int numChars = s.length();
        final StringBuilder sb = new StringBuilder(numChars > 500 ? numChars / 2 : numChars);
        int i = 0;

        char c;
        byte[] bytes = null;
        while (i < numChars) {
            c = s.charAt(i);

            if (c == '%') {
                /*
                 * Starting with this instance of %, process all
                 * consecutive substrings of the form %xy. Each
                 * substring %xy will yield a byte. Convert all
                 * consecutive  bytes obtained this way to whatever
                 * character(s) they represent in the provided
                 * encoding.
                 */

                try {

                    // (numChars-i)/3 is an upper bound for the number
                    // of remaining bytes
                    if (bytes == null) {
                        bytes = new byte[(numChars - i) / 3];
                    }
                    int pos = 0;

                    while (((i+2) < numChars) && (c=='%')) {
                        int v = Integer.parseInt(s.substring(i+1,i+3),16);
                        if (v < 0) {
                            throw new IllegalArgumentException("CANativeLibraryLoader: Illegal hex characters in escape (%) pattern - negative value");
                        }
                        bytes[pos++] = (byte) v;
                        i+= 3;
                        if (i < numChars) {
                            c = s.charAt(i);
                        }
                    }

                    // A trailing, incomplete byte encoding such as
                    // "%x" will cause an exception to be thrown
                    if ((i < numChars) && (c=='%')) {
                        throw new IllegalArgumentException("CANativeLibraryLoader: Incomplete trailing escape (%) pattern");
                    }

                    sb.append(new String(bytes, 0, pos, UTF_8));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("CANativeLibraryLoader: Illegal hex characters in escape (%) pattern - " + e.getMessage());
                }
                needToChange = true;
            } else {
                sb.append(c);
                i++;
            }
        }
        return needToChange ? sb.toString() : s;
    }

    /**
     * Read project version, injected by Maven.
     *
     * @return project version or <code>unknown</code>, if not found.
     */
    private static String readProjectVersion() {
        try {
            final Properties properties = new Properties();
            properties.load(CANativeLibraryLoader.class.getResourceAsStream("project.properties"));
            return properties.getProperty("version", "unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

}
