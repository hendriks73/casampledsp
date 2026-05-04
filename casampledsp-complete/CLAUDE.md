# casampledsp-complete

Distribution artifact. Bundles:
- Java sources copied from `casampledsp-java` (by the Maven resources plugin at build time)
- Both native libraries (`casampledsp-x86_64.dylib` and `casampledsp-aarch64.dylib`) embedded in the JAR root

This is the jar users add as a Maven dependency. `CANativeLibraryLoader` selects the correct dylib at runtime based on `os.arch`.

## Build

```bash
# Requires prior install of both native modules:
mvn install

# After editing Java sources in casampledsp-java, fix formatting:
mvn spotless:apply -pl casampledsp-complete
```

## Test Suite

All tests live in `src/test/java/com/tagtraum/casampledsp/`. They require both native libraries to be built first (a full `mvn install` from the root).

| Test class | What it covers |
|---|---|
| `TestAudioSystemIntegration` | End-to-end via `AudioSystem` |
| `TestCAAudioFileReader` | Format detection: encoding, sample rate, channels, frame size, duration for all supported formats |
| `TestCAAudioFormat` | `CAEncoding` and audio format metadata |
| `TestCAFormatConversionProvider` | `getTargetEncodings`, `getTargetFormats`, `isConversionSupported`, end-to-end conversions |
| `TestCACodecInputStream` | Direct `CACodecInputStream` conversion tests; PCM_SIGNED and PCM_FLOAT (32-bit, 64-bit) output; sample range and finiteness assertions |
| `TestCAURLInputStream` | URL and file opening, seeking |
| `TestCAStreamInputStream` | Stream-based decoding |
| `TestCANativeLibraryLoader` | Library extraction and loading |

### PCM_FLOAT test coverage

- **32-bit float**: `testReadConvertWaveStreamToFloat32PCM`, `testConvertWavToFloat32SamplesInRange`, `testConvertMp3ToFloat32SamplesFiniteAndReasonable` (in `TestCACodecInputStream`); `testConvertWavToFloat32ViaStandardEncoding` (in `TestCAFormatConversionProvider`); `testDecodeMp3ToFloatPCM` (in `TestAudioSystemIntegration`)
- **64-bit float**: `testConvertWavToFloat64SamplesInRange` (in `TestCACodecInputStream`); `testConvertWavToFloat64` (in `TestCAFormatConversionProvider`)
- Float samples from lossless PCM sources (WAV) are asserted to be in `[-1.0, 1.0]`; MP3 asserts `Float.isFinite` only, as inter-sample peaks slightly above 1.0 are expected

### Test resources

`src/test/resources/com/tagtraum/casampledsp/` contains audio files for testing:
- `test.wav` / `test_48k.wav` — PCM WAV at 44.1 kHz and 48 kHz
- `test.mp3` / `test_cbr256.mp3` / `test_vbr130.mp3` — MP3 (CBR and VBR)
- `test.m4a` / `test_cbr.m4a` / `test_vbr.m4a` — M4A/AAC
- `test_48k_alac.m4a` / `test_48k_cbr.m4a` / `test_48k_vbr.m4a` — 48 kHz M4A variants
- `test.aiff` — AIFF
