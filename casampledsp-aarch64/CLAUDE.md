# casampledsp-aarch64

Native library module for macOS aarch64 (Apple Silicon). Packages `casampledsp-aarch64.dylib`.

**No local C++ sources.** The compiler is pointed at `../casampledsp-x86_64/src/main/c/` — edit C++ code there.

## Build

```bash
mvn install -pl casampledsp-java,casampledsp-aarch64

# Debug build:
mvn install -pl casampledsp-java,casampledsp-aarch64 -Dcflags=-DDEBUG
```

Requires Apple Command Line Tools or Xcode (builds natively on Apple Silicon; cross-compilation from x86_64 also works via `-arch arm64`).
