# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CASampledSP is a macOS-only Java library implementing the `javax.sound.sampled.spi` service provider interfaces. It uses Apple's Core Audio framework via JNI to decode audio files (MP3, AAC, AIFF, WAV, and other Core Audio-supported formats) to PCM — signed integer (`PCM_SIGNED`) or floating-point (`PCM_FLOAT`).

## Build Commands

```bash
# Full build including native compilation and tests
mvn clean install

# Run all tests (requires a prior install to build native libs)
mvn test

# Run a single test class
mvn -pl casampledsp-complete test -Dtest=TestCAAudioFileReader

# Build only the Java module (no native compilation)
mvn -pl casampledsp-java clean install

# Apply Google Java Format before committing Java changes
mvn spotless:apply

# Generate site documentation (requires Doxygen)
mvn clean site

# Release build (GPG signing + code signing required)
mvn -P release clean deploy
```

Tests live in `casampledsp-complete/src/test/java/com/tagtraum/casampledsp/` and run against the complete JAR that embeds both native libraries.

After editing Java sources run `mvn spotless:apply` to apply Google Java Format before the build checks it.

## Module Structure

Four Maven modules with a strict build order:

1. **`casampledsp-java`** — Java SPI implementation; `mvn compile` here also generates JNI headers via `javac -h`
2. **`casampledsp-x86_64`** — C++ native library compiled for Intel (`-arch x86_64`)
3. **`casampledsp-aarch64`** — Same C++ source compiled for Apple Silicon (`-arch arm64`); its `sources.directory` points to `../casampledsp-x86_64/src/main/c`
4. **`casampledsp-complete`** — Uber JAR that bundles both dylibs inside the JAR root alongside the Java classes; this is what gets published to Maven Central

## PCM_FLOAT Support

`CAFormatConversionProvider` supports `PCM_SIGNED` (8/16/24/32-bit) and `PCM_FLOAT` (32-bit and 64-bit, mono/stereo, always little-endian) as target encodings. The float path is wired end-to-end:

- `CAAudioFormat.CAEncoding.PCM_FLOAT` shares `kAudioFormatLinearPCM` as the Core Audio format ID with `PCM_SIGNED`. It is registered in `NAME_MAP` only (not `DATAFORMAT_MAP`) since it is a target-only encoding.
- `CACodecInputStream` validates that PCM_FLOAT requests use 32 or 64 bits.
- `CACodecInputStream.cpp` detects `"PCM_FLOAT"` by calling `encoding.toString()` via JNI and sets `kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked` instead of `kAudioFormatFlagIsSignedInteger`.

## Architecture

### Java-Native Bridge

Each Java stream class (`CACodecInputStream`, `CAURLInputStream`, `CAStreamInputStream`) extends `CANativePeerInputStream`, which holds a `long nativePeerPointer` to a C++ struct on the heap. The native struct (defined in `CAUtils.h`) carries Core Audio state: `AudioFileID`/`AudioConverterRef`, buffers, and packet descriptors.

URL strings are encoded to UTF-8 `byte[]` on the Java side (via `url.toString().getBytes(StandardCharsets.UTF_8)`) before crossing the JNI boundary. This avoids JNI Modified UTF-8 (CESU-8) issues with emoji and other supplementary characters in file paths. The native side uses `CFURLCreateWithBytes` with `kCFStringEncodingUTF8`.

### Audio Decoding Pipeline

```
AudioSystem.getAudioInputStream()
  → CAAudioFileReader       (detect format, read metadata via AudioFileID)
  → CAURLInputStream        (read compressed frames from file)
  → CACodecInputStream      (transcode to PCM via AudioConverterRef)
  → CAAudioInputStream      (returned to caller as standard AudioInputStream)
```

`CAStreamInputStream` is the network/streaming variant of `CAURLInputStream`, using `AudioFileStreamID` instead of `AudioFileID`.

### Buffer Sizes

- `CAURLInputStream` defaults to 1 MB per read (`casampledsp.fileBufferSize` system property), matching typical disk read granularity.
- `CAStreamInputStream` defaults to 64 KB (`casampledsp.streamBufferSize` system property), suitable for network streaming.

### Native Library Loading

`CANativeLibraryLoader` checks `os.arch` at runtime, extracts the matching dylib from the JAR to a temp directory, and loads it. The dylib name encodes the architecture (e.g., `libcasampledsp-aarch64.dylib`).

### SPI Registration

`casampledsp-java/src/main/resources/META-INF/services/` contains the two SPI registration files so Java's `AudioSystem` discovers this library automatically on the classpath.

## Native Code Notes

All C++ source is in `casampledsp-x86_64/src/main/c/` and shared by both architecture modules:

- **`CAUtils.h/.cpp`** — JNI exception helpers, `ca_url_ref_from_utf8()` for `CFURLRef` creation from UTF-8 bytes, four-char error code formatting, and the shared `CAAudioIO`/`CAAudioConverterIO`/`CAAudioFileIO`/`CAAudioStreamIO` structs
- **`CACodecInputStream.cpp`** — `AudioConverterRef` setup and the `ComplexInputDataProc` callback that feeds data from the source Java stream into Core Audio's converter
- **`CAURLInputStream.cpp`** / **`CAStreamInputStream.cpp`** — file and stream readers using `AudioFileID` and `AudioFileStreamID` respectively
- **`CAAudioFileReader.cpp`** — reads file properties (format, sample rate, duration) without decoding

C++ style: structs zero-initialised with `new T{}`, `nullptr` throughout, `reinterpret_cast` for pointer↔`jlong`, `static_cast` for numeric conversions. The `goto bail` pattern is used for JNI error cleanup; all locals are declared before the first `goto`.

Compiler flags: `-DTARGET_OS_MAC -arch {x86_64|arm64} -mmacosx-version-min=11.0 -O3` plus `-framework CoreFoundation -framework AudioToolbox`. Minimum deployment target is macOS 11.0. No deprecated Core Audio APIs are used (`AudioFileReadPacketData` not `AudioFileReadPackets`, `AudioConverterFillComplexBuffer` not `AudioConverterFillBuffer`).

## Key Constraints

- macOS only; the build will fail on Linux/Windows
- Java 8 source/target compatibility must be maintained
- Both native architectures must be built and pass tests before a release
- The `casampledsp-aarch64` module does **not** have its own C++ sources — edits to native code go in `casampledsp-x86_64/src/main/c/`
- `MAVEN_GPG_PASSPHRASE` is passed via environment variable in CI, never as a `-Dgpg.passphrase=` command-line argument
