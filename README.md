README.md
==========

[![LGPL 2.1](https://img.shields.io/badge/License-LGPL_2.1-blue.svg)](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html)
[![Maven Central](https://img.shields.io/maven-central/v/com.tagtraum/casampledsp-complete)](https://central.sonatype.com/artifact/com.tagtraum/casampledsp-complete)
[![Build and Test](https://github.com/hendriks73/casampledsp/workflows/Build%20and%20Test/badge.svg)](https://github.com/hendriks73/casampledsp/actions)
[![CodeCov](https://codecov.io/gh/hendriks73/casampledsp/branch/main/graph/badge.svg?token=H98FM0SKQL)](https://codecov.io/gh/hendriks73/casampledsp/branch/main)

*CASampledSP* is an implementation of the
[javax.sound.sampled](https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/sound/sampled/spi/package-summary.html)
service provider interfaces based on Apple's Core Audio library, supporting all its file formats (mp3, aac, ...).
It is part of the [SampledSP](https://www.tagtraum.com/sampledsp.html) collection of `javax.sound.sampled`
libraries.

Its main purpose is to decode audio files or streams to
[PCM](https://en.wikipedia.org/wiki/Pulse-code_modulation) — signed integer (`PCM_SIGNED`) or
floating-point (`PCM_FLOAT`).


Usage Example
-------------

To use the library with Maven, introduce the following dependency:
          
```xml
<dependency>
  <groupId>com.tagtraum</groupId>
  <artifactId>casampledsp-complete</artifactId>
</dependency>
```

Note that when opening a compressed file with *CASampledSP*, you still need to
convert to PCM in order to actually decode the file.

Here's a simple example for how that's done for mp3 to wave: 

```java
public static void mp3ToWav(File mp3Data) throws UnsupportedAudioFileException, IOException {
    // open stream
    AudioInputStream mp3Stream = AudioSystem.getAudioInputStream(mp3Data);
    AudioFormat sourceFormat = mp3Stream.getFormat();
    // create audio format object for the desired stream/audio format
    // this is *not* the same as the file format (wav)
    AudioFormat convertFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 
        sourceFormat.getSampleRate(), 16, 
        sourceFormat.getChannels(), 
        sourceFormat.getChannels() * 2,
        sourceFormat.getSampleRate(),
        false);
    // create stream that delivers the desired format
    AudioInputStream converted = AudioSystem.getAudioInputStream(convertFormat, mp3Stream);
    // write stream into a file with file format wav
    AudioSystem.write(converted, Type.WAVE, new File("C:\\temp\\out.wav"));
}
```

See also [here](https://stackoverflow.com/a/41850901/942774).

To decode to 32-bit float PCM (`PCM_FLOAT`) instead, specify the float encoding and
read the samples via a `FloatBuffer`:

```java
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public class FloatDecodeExample {
    public static void main(final String[] args) throws Exception {
        // compressed stream
        final AudioInputStream mp3In = AudioSystem.getAudioInputStream(new File(args[0]));
        // AudioFormat describing the compressed stream
        final AudioFormat mp3Format = mp3In.getFormat();
        // AudioFormat describing 32-bit float PCM output (little-endian, samples in [-1.0, 1.0])
        final AudioFormat pcmFloatFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_FLOAT,
            mp3Format.getSampleRate(),
            32,
            mp3Format.getChannels(),
            32 * mp3Format.getChannels() / 8,
            mp3Format.getSampleRate(),
            false  // little-endian
            );
        // decoded float PCM stream
        final AudioInputStream pcmIn = AudioSystem.getAudioInputStream(pcmFloatFormat, mp3In);
        // read and process samples as float values
        final byte[] buf = new byte[4096];
        int justRead;
        while ((justRead = pcmIn.read(buf)) != -1) {
            final FloatBuffer floats = ByteBuffer.wrap(buf, 0, justRead)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer();
            while (floats.hasRemaining()) {
                final float sample = floats.get(); // value in [-1.0, 1.0]
                // process sample...
            }
        }
    }
}
```


Build
-----

You can only build this library on macOS.

To do so, you also need:

- Maven 3.6.0 or later, https://maven.apache.org/
- Apple Command Line Tools, available via https://developer.apple.com/,
  or XCode, https://developer.apple.com/xcode/
- a JDK (to run Maven and get the JNI headers)
- [Doxygen](http://www.doxygen.org), available via [MacPorts](https://www.macports.org) or [HomeBrew](https://brew.sh)

Once you have all this, a simple build is:

    mvn clean install

Note: the GitHub Actions CI runner (`macos-latest`) is ARM64 (Apple Silicon). Both x86_64 and
arm64 native libraries are always built together in a single Maven run via cross-compilation.


Release Notes
-------------

You can find the release notes/history [here](NOTES.md).
