README.md
==========

[![LGPL 2.1](https://img.shields.io/badge/License-LGPL_2.1-blue.svg)](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.tagtraum/casampledsp-complete/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.tagtraum/casampledsp-complete)
[![Build and Test](https://github.com/hendriks73/casampledsp/workflows/Build%20and%20Test/badge.svg)](https://github.com/hendriks73/casampledsp/actions)
[![CodeCov](https://codecov.io/gh/hendriks73/casampledsp/branch/master/graph/badge.svg?token=H98FM0SKQL)](https://codecov.io/gh/hendriks73/casampledsp/branch/master)

*CASampledSP* is an implementation of the
[javax.sound.sampled](https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/sound/sampled/spi/package-summary.html)
service provider interfaces based on Apple's Core Audio library, supporting all its file formats (mp3, aac, ...).
It is part of the [SampledSP](https://www.tagtraum.com/sampledsp.html) collection of `javax.sound.sampled`
libraries.

Its main purpose is to decode audio files or streams to signed linear pcm.

This library comes with absolutely no support, warranty etc. you name it.

Binaries and more info can be found at its [tagtraum home](https://www.tagtraum.com/casampledsp/).

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


Build
-----

You can only build this library on macOS.

To do so, you also need:

- Maven 3.0.5 or later, https://maven.apache.org/
- Apple Command Line Tools, available via https://developer.apple.com/,
  or XCode, https://developer.apple.com/xcode/
- a JDK (to run Maven and get the JNI headers)
- [Doxygen](http://www.doxygen.org), available via [MacPorts](https://www.macports.org) or [HomeBrew](https://brew.sh)

Once you have all this, you need to adjust some properties in the parent `pom.xml`.
Or.. simply override them using `-Dname=value` notation. E.g. to point to your
Oracle JDK JNI headers, add e.g.

    -Ddarwin.headers.jni=/Library/Java/JavaVirtualMachines/jdk-10.jdk/Contents/Home/include/

to your mvn call. You might also need to change `mmacosx-version-min` and `isysroot`, if you
don't have an OS X 10.7 SDK installed.

So all in all, something like the following might work for you:

    mvn -Ddarwin.headers.jni=/Library/Java/JavaVirtualMachines/jdk-10.jdk/Contents/Home/include/ \
        -Dmmacosx-version-min=10.7 \
        -Disysroot=/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/ \
        clean install

Enjoy.

https://www.tagtraum.com/casampledsp/