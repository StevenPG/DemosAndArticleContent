# JNI vs FFM: Java Native Interop Compared

This project accompanies a blog article comparing Java's two native interop technologies:

- **JNI (Java Native Interface)** - The original native interop API, available since Java 1.1
- **FFM (Foreign Function & Memory API)** - The modern replacement, standardized in Java 22 (JEP 454)
- **jextract** - A companion tool that auto-generates FFM bindings from C header files

All three subprojects implement equivalent functionality so you can directly compare the developer experience, code complexity, and capabilities of each approach.

## Prerequisites

- **Java 25** (required for all projects)
- **GCC** or another C compiler (for compiling native code)
- **Linux** (examples use Linux paths/extensions; see platform notes below)
- **jextract** (optional, only for auto-generating FFM bindings)

## Project Structure

```
jni-vs-ffm-interop/
├── jni-example/              # JNI-based native interop
│   ├── src/main/java/        # Java classes with 'native' method declarations
│   │   └── .../jni/
│   │       ├── NativeStringUtils.java     # String ops (toUpperCase, reverse, countChar)
│   │       ├── NativeArrayOps.java        # Array ops (sum, sort, scale)
│   │       ├── NativeSystemInfo.java      # System calls (hostname, pid, malloc)
│   │       ├── NativeCallback.java        # C→Java upcalls (progress, string transform)
│   │       ├── NativeThreading.java       # Multi-threaded JNI (AttachCurrentThread)
│   │       └── JniExamplesMain.java       # Entry point
│   └── src/main/native/      # C implementations (JNI-specific, with jni.h)
│       ├── native_string_utils.c
│       ├── native_array_ops.c
│       ├── native_system_info.c
│       ├── native_callback.c
│       └── native_threading.c
│
├── ffm-example/              # FFM-based native interop
│   ├── src/main/java/        # Pure Java code (no C needed for stdlib calls!)
│   │   └── .../ffm/
│   │       ├── StringExamples.java        # Call strlen/toupper from C stdlib
│   │       ├── MemoryExamples.java        # Arena, StructLayout, SequenceLayout
│   │       ├── SystemCallExamples.java    # gethostname, getpid, malloc, errno
│   │       ├── CustomLibraryExamples.java # Load your own .so library
│   │       ├── UpcallExamples.java        # qsort with a Java comparator
│   │       ├── AdvancedExamples.java      # Variadic (snprintf), nested structs, unions
│   │       └── FfmExamplesMain.java       # Entry point
│   └── src/main/native/      # Plain C library (NO JNI dependencies)
│       └── math_utils.c
│
├── jextract-example/         # jextract auto-generated bindings demo
│   ├── src/main/native/
│   │   ├── geometry.h         # C header (input to jextract)
│   │   └── geometry.c         # C implementation
│   ├── src/main/java/.../
│   │   └── JextractExampleMain.java   # Manual FFM equivalent for comparison
│   ├── generate-bindings.sh   # Script to run jextract
│   └── README.md              # Detailed jextract documentation
│
└── README.md                 # This file
```

## Running the Examples

```bash
# Run all JNI examples (compiles C code automatically)
./gradlew :jni-example:run

# Run all FFM examples (compiles C code automatically)
./gradlew :ffm-example:run

# Run the jextract example (manual FFM equivalent)
./gradlew :jextract-example:run
```

<!-- TODO: Paste actual output from running each project here after manual verification -->

## Platform Notes

The build files are configured for **Linux** by default. For macOS:
- Change `.so` to `.dylib` in all `build.gradle` files
- Change `-I${javaHome}/include/linux` to `-I${javaHome}/include/darwin` in `jni-example/build.gradle`

For Windows, you'll need MinGW or MSVC and will need to produce `.dll` files instead.

---

## Comparison: JNI vs FFM

### Side-by-Side Summary

| Aspect | JNI | FFM |
|---|---|---|
| **Available since** | Java 1.1 (1997) | Java 22 (2024), preview since Java 19 |
| **C code required?** | Yes - must write JNI-specific C wrappers | No - call any C function directly from Java |
| **Header generation** | `javac -h` generates headers you must implement | Not needed |
| **Function naming** | Strict convention: `Java_pkg_Class_method` | Any function name, looked up by string |
| **Memory management** | Manual (`malloc`/`free`), easy to leak | Arena-based, deterministic, bounds-checked |
| **Type safety** | Weak - C void pointers, manual casting | Strong - `MemoryLayout`, `VarHandle`, `FunctionDescriptor` |
| **Error handling** | Mix of C error codes + Java exceptions | Standard Java exceptions + errno capture |
| **Struct access** | Manual byte offset calculations or JNI field access | `StructLayout` with named fields and `VarHandle` |
| **Array access** | Pin/Copy/Release pattern (3 strategies, each with caveats) | `MemorySegment.setAtIndex()` with bounds checking |
| **Callbacks (upcalls)** | FindClass + GetMethodID + CallXxxMethod from C | `linker.upcallStub()` creates a function pointer from Java |
| **Thread safety** | Must manage `JNIEnv*` per thread, `AttachCurrentThread` | Confined/shared arenas with clear ownership |
| **Variadic functions** | Cannot call directly (need C wrapper for each variant) | Supported via `Linker.Option.firstVariadicArg()` |
| **errno access** | Must read in C and pass back manually | `Linker.Option.captureCallState("errno")` |
| **Performance** | Very fast (direct native calls) | Comparable, with JIT optimizations possible |
| **Tooling** | Mature but complex (header generation, build scripts) | Pure Java + optional jextract for code generation |
| **Auto-generation** | None (manual C wrappers always required) | jextract generates bindings from C headers |

### When to Use JNI

- **Legacy codebases**: You already have JNI code that works and is battle-tested
- **Pre-Java 22 targets**: If you must support older Java versions
- **Existing team expertise**: Your team knows JNI well and has established patterns
- **Heavily documented use case**: Many existing tutorials and StackOverflow answers

### When to Use FFM

- **New projects**: No reason to choose JNI for greenfield development targeting Java 22+
- **Calling existing C libraries**: FFM shines when wrapping libraries you didn't write (no C wrapper code needed)
- **Memory-intensive workloads**: Arena-based memory management is safer and often faster
- **Rapid prototyping**: Pure Java means faster iteration, no compile-link-test cycle for C code
- **Struct-heavy APIs**: `StructLayout` is vastly easier than JNI field access
- **Large C APIs**: Combine FFM with jextract to auto-generate bindings

### When to Use jextract

- **Large C APIs** (50+ functions): Manually writing FFM bindings is tedious and error-prone
- **Complex struct hierarchies**: Nested structs, arrays of structs, unions
- **Frequently changing headers**: Re-run jextract instead of manually updating bindings
- **Correctness**: jextract reads the actual C compiler's struct layout information

---

## Key Advantages of FFM over JNI

### 1. No C Wrapper Code Needed

Call `strlen()`, `getpid()`, `qsort()`, or ANY C function directly from Java:

```java
// FFM: Call strlen directly from Java. No C code at all.
MethodHandle strlen = linker.downcallHandle(
    stdlib.find("strlen").orElseThrow(),
    FunctionDescriptor.of(JAVA_LONG, ADDRESS));
try (Arena arena = Arena.ofConfined()) {
    long len = (long) strlen.invoke(arena.allocateFrom("hello"));
}
```

JNI requires writing a C file for every native method, with the full `GetStringUTFChars`/`ReleaseStringUTFChars` dance.

### 2. Memory Safety

FFM's `Arena` pattern ensures native memory is freed deterministically:

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment buf = arena.allocate(1024);
    // ... use buf ...
}  // ALL memory freed here. Guaranteed. No possibility of a leak.
```

JNI gives you raw pointers and requires manual `free()`. Forget it once → memory leak forever.

### 3. Callbacks / Upcalls

FFM can turn any Java method into a C function pointer:

```java
// Java comparator called from C's qsort!
MemorySegment comparatorPtr = linker.upcallStub(comparatorHandle, desc, arena);
qsort.invoke(array, count, elemSize, comparatorPtr);
```

JNI requires `FindClass` + `GetMethodID` + `CallVoidMethod` from C code, with manual `ExceptionCheck` after each call, and `AttachCurrentThread` for native-created threads.

### 4. Variadic Functions

FFM can call printf/snprintf directly:

```java
MethodHandle snprintf = linker.downcallHandle(addr, desc,
    Linker.Option.firstVariadicArg(3));
snprintf.invoke(buffer, 256L, format, name, 42);
```

JNI **cannot** call variadic functions at all. You must write a separate C wrapper for each combination of argument types.

### 5. errno Capture

FFM captures errno atomically:

```java
MethodHandle open = linker.downcallHandle(addr, desc,
    Linker.Option.captureCallState("errno"));
open.invoke(state, path, flags);
int errno = /* read from state */;
```

In JNI, you must read errno in your C wrapper immediately after the call and pass it back manually.

### 6. Portable Native Code

FFM's C libraries are plain C. The same `.so` file works with Java, Python, Rust, Go. JNI C code requires `#include <jni.h>` and the `Java_pkg_Class_method` naming convention - it can only be used from Java.

---

## Key Advantages of JNI over FFM

1. **Maturity and ecosystem**: JNI has nearly 30 years of usage, documentation, and library support.

2. **Broader Java version support**: JNI works on any Java version. FFM requires Java 22+ for the stable API.

3. **Well-understood patterns**: JNI callbacks, threading, and error handling patterns are extensively documented in books, tutorials, and production codebases.

<!-- TODO: Add benchmark results after running both projects with equivalent workloads -->

---

## The Boilerplate Spectrum

Here's how the three approaches compare for calling C's `circle_area()`:

**JNI approach** (3 files, 2 languages):
```java
// Java: Declare native method
public native double circleArea(double centerX, double centerY, double radius);

// C header: Auto-generated by javac -h
JNIEXPORT jdouble JNICALL Java_pkg_Class_circleArea(JNIEnv *, jobject, jdouble, jdouble, jdouble);

// C implementation: You write this
JNIEXPORT jdouble JNICALL Java_pkg_Class_circleArea(
    JNIEnv *env, jobject obj, jdouble cx, jdouble cy, jdouble r) {
    Circle c = { .center = { .x = cx, .y = cy }, .radius = r };
    return circle_area(&c);  // Finally call the actual function
}
```

**FFM approach** (1 file, verbose):
```java
StructLayout circleLayout = MemoryLayout.structLayout(
    MemoryLayout.structLayout(JAVA_DOUBLE.withName("x"), JAVA_DOUBLE.withName("y")).withName("center"),
    JAVA_DOUBLE.withName("radius"));
MethodHandle circleArea = linker.downcallHandle(
    lib.find("circle_area").orElseThrow(),
    FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS));
```

**jextract approach** (0 manual files):
```java
// All generated automatically from geometry.h:
double area = geometry_h.circle_area(circle);
```

## Further Reading

- [JEP 454: Foreign Function & Memory API](https://openjdk.org/jeps/454) - The FFM specification
- [JNI Specification](https://docs.oracle.com/en/java/javase/25/docs/specs/jni/index.html) - Official JNI docs
- [Project Panama](https://openjdk.org/projects/panama/) - The OpenJDK project that developed FFM
- [jextract](https://github.com/openjdk/jextract) - Auto-generate FFM bindings from C headers
- [FFM API Javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/package-summary.html) - API documentation

<!-- TODO: Add link to the published blog article -->
