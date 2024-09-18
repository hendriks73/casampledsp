- 0.9.32
  - Updated several GH actions.

 
- 0.9.31
  - Fixed native library loader.
  - Updated maven plugins.
 
 
- 0.9.30
   - Updated Maven project report skin (fixing the *Fork Me*-banner).


- 0.9.29

  - Added signature for native macOS libs


- 0.9.27

  - Updated to `actions/setup-java@v2`.


- 0.9.26

  - Fixed rounding error when computing duration.
  - Fixed frame length in AudioInputStream.
  - Support seeking even after end of file has been reached.


- 0.9.25

  - Updated JUnit and Ant dependencies.
  - Updated site and dependencies plugins and config.
  - Improved test coverage.
  - Renamed arm64 to aarch64.
  - Added missing files to repo.
  - Added automatic build via GitHub actions.


- 0.9.24

  - Added support for arm64.


- 0.9.23

  - Explicitly set connection timeout for network resources in CAAudioFileReader (defaults to 5s).


- 0.9.22

  - Increased size of default buffer substantially.
  - Introduced API to manipulate buffer size.


- 0.9.21

  - Fixed loading wrong native lib.


- 0.9.20

  - Fixed crash on `com.tagtraum.casampledsp.CACodecInputStream.open` (jni_DeleteGlobalRef).
  - Dropped support for 32 bit.


- 0.9.19

  - Attached sources and javadocs to complete module.


- 0.9.18

  - Moved to maven central repository.
  - Fixed provided dependencies.


- 0.9.17

  - Fixed embedded library loading.


- 0.9.16

  - Moved to javac -h header generation.
  - Moved to Java7.
  - Use `${java.home}` from Maven to locate JNI headers.
  - Embedded dylib into casampledsp-complete artifact.


- 0.9.15

  - More tests for CBR/VBR files.
  - Safeguard against SIGFPE (div by zero) in native CAURLInputStream.


- 0.9.14

  - Ensure that we can still read the whole file after seeking.


- 0.9.13

  - Ensure that we can still read the whole file after seeking.


- 0.9.12

  - Ensure that `seek()` does not fail silently, if the stream is already closed.
  - Ensure that wrapped streams are closed when transcoding.
  - Fixed pom.xml to compile under High Sierra.


- 0.9.11

  - Fixed library loading issues when the classpath contains a + char.


- 0.9.10

  - Added some `NULL` pointer checks.


- 0.9.9

  - Made sure, url streams are buffered and can be marked.
  - Workaround hang in Toolkit.<clinit>.
  - Fixed AIFF test.
  - Fixed FileNotFound test.
  - Migrated to dylib, replacing jnilib.
  - Changed Maven skin to Fluido.
  - Added GitHub ribbon.


- 0.9.8

  - Moved to OS X 10.6 in order to take advantage of AudioFileReadPacketData.
  - Fixed seek() in CACodecInputStream - buffers are now flushed appropriately.


- 0.9.7

  - Minor fixes to pom.
  - Support for non-file URLs, taking content type hints into account.
  - Allowed for deltas in tests, to make tests pass on Mavericks.


- 0.9.6

  - Added support for seek.
  - Switched license to LGPL 2.1.
  - Upgraded to Maven 3.0.5.
  - Added doxygen call for C docs.
  - Moved to github.com.


- 0.9.5

  - Fixed issue with filenames containing punctuation.


- 0.9.4

  - Fixed issue with profile activation.
  - Migrated to Maven 3.0.4.
  - Migrated to native-maven-plugin.
  - Updated JUnit to 4.10


- 0.9.3

  - Fixed issues with chaining multiple AudioInputStreams for conversion purposes.


- 0.9.2

  - Fixed a plethora of issues with stream-(not file-)based conversion.
  - Fixed issues with formats that require a magic cookie for decompression.


- 0.9.1

  - The library can now still be present as (no-op) service provider even when the native library is not available (e.g. on Windows systems).
  - Fixed wrong endianness issue in CAAudioFileReader.
  - Fixed issue with non-ASCII file names.
  - Prevent conversion from non-CASampledSP AudioInputStreams (as it will fail).


- 0.9.0

  - First release
