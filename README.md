README.md
==========

*CASampledSP* is an implementation of the
[javax.sound.sampled](http://docs.oracle.com/javase/7/docs/api/javax/sound/sampled/spi/package-summary.html)
service provider interfaces based on Apple's Core Audio library.
It is part of the [SampledSP](http://www.tagtraum.com/sampledsp.html) collection of `javax.sound.sampled`
libraries

Its main purpose is to decode audio files or streams to signed linear pcm.

This library comes with absolutely no support, warranty etc. you name it.


Build
-----

You can only build this library on OS X.

To do so, you also need:

- Maven 3.0.5 or later, http://maven.apache.org/
- Apple Command Line Tools, available via https://developer.apple.com/,
  or XCode, https://developer.apple.com/xcode/
- a JDK (to run Maven and get the OSX JNI headers)
- Doxygen, available via MacPorts

Once you have all this, you need to adjust some properties in the parent pom.xml.
Or.. simply override them using `-Dname=value` notation. E.g. to point to your
Oracle JDK JNI headers, add e.g.

    -Ddarwin.headers.jni=/Library/Java/JavaVirtualMachines/jdk1.7.0_51.jdk/Contents/Home/include/

to your mvn call. You might also need to change `mmacosx-version-min` and `isysroot`, if you
don't have an OS X 10.5 SDK installed.

So all in all, something like the following might work for you:

    mvn -Ddarwin.headers.jni=/Library/Java/JavaVirtualMachines/jdk1.7.0_51.jdk/Contents/Home/include/ \
        -Dmmacosx-version-min=10.7 \
        -Disysroot=/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.7.sdk/ \
        clean install

Note, that the C sources in the casampledsp-x86_64 module are expected to compile on
all supported architectures. In fact, the very same sources *are* compiled in the modules
for other architectures.


Have fun,

-hendrik

hs@tagtraum.com
