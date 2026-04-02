# JNI vs FFM: Java Native Interop Compared

This project accompanies a blog article comparing Java's two native interop technologies:

- **JNI (Java Native Interface)** - The original native interop API, available since Java 1.1
- **FFM (Foreign Function & Memory API)** - The modern replacement, standardized in Java 22 (JEP 454)

Both subprojects implement equivalent functionality so you can directly compare the developer experience, code complexity, and capabilities of each approach.

## Prerequisites

- **Java 25** (required for both projects)
- **GCC** or another C compiler (for compiling native code)
- **Linux** (examples use Linux paths/extensions; see platform notes below)

## Project Structure

```
jni-vs-ffm-interop/
├── jni-example/          # JNI-based native interop examples
│   ├── src/main/java/    # Java classes with native method declarations
│   └── src/main/native/  # C implementations with JNI boilerplate
├── ffm-example/          # FFM-based native interop examples
│   ├── src/main/java/    # Pure Java code using FFM API
│   └── src/main/native/  # Plain C library (no JNI dependencies)
└── README.md
```

## Running the Examples

```bash
# Run JNI examples (compiles C code automatically)
./gradlew :jni-example:run

# Run FFM examples (compiles C code automatically)
./gradlew :ffm-example:run
```

<!-- TODO: Paste actual output from running each project here -->

## Platform Notes

The build files are configured for **Linux** by default. For macOS:
- Change `.so` to `.dylib` in both `build.gradle` files
- Change `-I${javaHome}/include/linux` to `-I${javaHome}/include/darwin` in `jni-example/build.gradle`

For Windows, you'll need MinGW or MSVC and will need to produce `.dll` files instead.

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
| **Error handling** | Mix of C error codes + Java exceptions | Standard Java exceptions |
| **Struct access** | Manual byte offset calculations or JNI field access | `StructLayout` with named fields and `VarHandle` |
| **Thread safety** | Must manage `JNIEnv*` per thread | Confined/shared arenas with clear ownership |
| **Performance** | Very fast (direct native calls) | Comparable, with JIT optimizations possible |
| **Tooling** | Mature but complex (header generation, build scripts) | Pure Java, standard build tools |

### When to Use JNI

- **Legacy codebases**: You already have JNI code that works and is battle-tested
- **Callback-heavy APIs**: JNI's callback mechanism is mature (though FFM supports upcalls too)
- **Pre-Java 22 targets**: If you must support older Java versions
- **Existing expertise**: Your team knows JNI well

### When to Use FFM

- **New projects**: No reason to choose JNI for greenfield development targeting Java 22+
- **Calling existing C libraries**: FFM shines when wrapping libraries you didn't write (no C wrapper code needed)
- **Memory-intensive workloads**: Arena-based memory management is safer and often faster
- **Rapid prototyping**: Pure Java means faster iteration, no compile-link-test cycle for C code
- **Struct-heavy APIs**: `StructLayout` is vastly easier than JNI field access

### Key Advantages of FFM over JNI

1. **No C wrapper code needed**: Call `strlen()`, `getpid()`, or any C function directly from Java. JNI requires writing a C file for every native method.

2. **Memory safety**: FFM's `Arena` pattern ensures native memory is freed deterministically. `MemorySegment` provides bounds checking. JNI gives you raw pointers and wishes you luck.

3. **Portability of native code**: FFM's C libraries are plain C with no JNI headers. The same `.so` file can be used from Java, Python, Rust, etc. JNI C code is Java-specific.

4. **Developer experience**: FFM code is all Java - you get IDE support, refactoring, debugging. JNI requires jumping between Java and C, with a compilation step in between.

5. **Struct mapping**: FFM's `StructLayout` + `VarHandle` provides named, type-safe access to struct fields. In JNI, you manually calculate byte offsets or use JNI field accessor functions.

### Key Advantages of JNI over FFM

1. **Maturity and ecosystem**: JNI has nearly 30 years of usage, documentation, and library support.

2. **Slightly lower ceremony for callbacks**: JNI's `CallVoidMethod`/`CallObjectMethod` pattern for calling Java from C is well-understood (though FFM's upcall stubs work too).

3. **Broader Java version support**: JNI works on any Java version. FFM requires Java 22+ for the stable API.

<!-- TODO: Add benchmark results after running both projects with equivalent workloads -->

### The Boilerplate Problem

Here's a concrete example of the difference. To call C's `strlen()` from Java:

**JNI approach** (3 files, 2 languages):
```java
// Java: Declare native method
public native long strlen(String s);

// C header: Auto-generated by javac -h (must not be edited)
JNIEXPORT jlong JNICALL Java_com_example_MyClass_strlen(JNIEnv *, jobject, jstring);

// C implementation: You write this
JNIEXPORT jlong JNICALL Java_com_example_MyClass_strlen(JNIEnv *env, jobject obj, jstring s) {
    const char *str = (*env)->GetStringUTFChars(env, s, NULL);
    jlong len = strlen(str);
    (*env)->ReleaseStringUTFChars(env, s, str);
    return len;
}
```

**FFM approach** (1 file, 1 language):
```java
// Java: Call strlen directly
MethodHandle strlen = linker.downcallHandle(
    stdlib.find("strlen").orElseThrow(),
    FunctionDescriptor.of(JAVA_LONG, ADDRESS)
);
try (Arena arena = Arena.ofConfined()) {
    long len = (long) strlen.invoke(arena.allocateFrom("hello"));
}
```

## Further Reading

- [JEP 454: Foreign Function & Memory API](https://openjdk.org/jeps/454) - The FFM specification
- [JNI Specification](https://docs.oracle.com/en/java/javase/25/docs/specs/jni/index.html) - Official JNI docs
- [Project Panama](https://openjdk.org/projects/panama/) - The OpenJDK project that developed FFM
- [jextract](https://github.com/openjdk/jextract) - Tool to auto-generate FFM bindings from C headers

<!-- TODO: Add link to the published blog article -->
