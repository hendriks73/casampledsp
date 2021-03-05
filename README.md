README.md
==========

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.tagtraum/casampledsp-complete/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.tagtraum/casampledsp-complete)
[![Build and Test](https://github.com/hendriks73/casampledsp/workflows/Build%20and%20Test/badge.svg)](https://github.com/hendriks73/casampledsp/actions)
[![CodeCov](https://codecov.io/gh/hendriks73/casampledsp/branch/master/graph/badge.svg?token=H98FM0SKQL)](https://codecov.io/gh/hendriks73/casampledsp/branch/master)

*CASampledSP* is an implementation of the
[javax.sound.sampled](https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/sound/sampled/spi/package-summary.html)
service provider interfaces based on Apple's Core Audio library, supporting all its file formats (mp3, aac, ...).
It is part of the [SampledSP](http://www.tagtraum.com/sampledsp.html) collection of `javax.sound.sampled`
libraries.

Its main purpose is to decode audio files or streams to signed linear pcm.

This library comes with absolutely no support, warranty etc. you name it.

Binaries and more info can be found at its [tagtraum home](http://www.tagtraum.com/casampledsp/).


Build
-----

You can only build this library on macOS.

To do so, you also need:

- Maven 3.0.5 or later, http://maven.apache.org/
- Apple Command Line Tools, available via https://developer.apple.com/,
  or XCode, https://developer.apple.com/xcode/
- a JDK (to run Maven and get the OSX JNI headers)
- [Doxygen](http://www.doxygen.org), available via [MacPorts](https://www.macports.org) or [HomeBrew](https://brew.sh)

Once you have all this, you need to adjust some properties in the parent pom.xml.
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

http://www.tagtraum.com/casampledsp/