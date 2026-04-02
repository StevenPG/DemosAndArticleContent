# FFM Example

Demonstrates the Foreign Function & Memory API (FFM) - Java's modern native interop API, standardized in Java 22 via [JEP 454](https://openjdk.org/jeps/454).

## What This Project Shows

| Class | What It Demonstrates |
|---|---|
| `StringExamples` | Calling C stdlib functions (strlen, toupper) directly from Java |
| `MemoryExamples` | Arena-based memory management, StructLayout, memory slicing |
| `SystemCallExamples` | POSIX system calls (gethostname, getpid, malloc/free) - no C code needed |
| `CustomLibraryExamples` | Loading and calling a custom shared library (.so) |

## How FFM Works

FFM's workflow is simpler than JNI - most calls need no C wrapper at all:

```
For calling standard/existing libraries (no C code!):

┌──────────────────────────────────────────┐
│  Pure Java code                          │
│                                          │
│  1. Linker.nativeLinker()                │  ◄── Get the native linker
│  2. lookup.find("functionName")          │  ◄── Find the function
│  3. FunctionDescriptor.of(ret, params)   │  ◄── Describe its signature
│  4. linker.downcallHandle(addr, desc)    │  ◄── Create callable handle
│  5. handle.invoke(args...)               │  ◄── Call it!
└──────────────────────────────────────────┘

For calling your own custom library:

┌──────────────┐     gcc -shared    ┌──────────────┐
│  Plain C code │ ────────────────► │  Shared lib  │
│  (no JNI!)    │                   │  (.so/.dylib)│
└──────────────┘                    └──────┬───────┘
                                           │
┌──────────────────────────────────────────┘
│
▼
┌──────────────────────────────────────────┐
│  Pure Java code                          │
│  SymbolLookup.libraryLookup(path, arena) │  ◄── Load the library
│  ... then same steps 2-5 as above        │
└──────────────────────────────────────────┘
```

## Running

```bash
./gradlew :ffm-example:run
```

This will:
1. Compile the plain C library into `libffmdemo.so` (only for the CustomLibrary example)
2. Run the Java application with `--enable-native-access=ALL-UNNAMED`

### Prerequisites

- Java 25
- GCC (only for the custom library example; stdlib/system call examples need no compiler)
- Linux (see parent README for macOS/Windows notes)

<!-- TODO: Paste actual output here after running -->

### Expected Output

```
=== FFM (Foreign Function & Memory) Examples ===
Java Version: 25

--- String Operations (C Standard Library via FFM) ---
strlen("Hello from FFM!") = 15
toupper("Hello from FFM!") = HELLO FROM FFM!

--- Memory Operations (FFM MemorySegment & Arena) ---
Squares: [0, 1, 4, 9, 16, 25, 36, 49, 64, 81]
Point3D { x=1.0, y=2.5, z=3.7 }
Point3D size: 24 bytes
Slice [5..9]: [6, 7, 8, 9, 10]

--- System Calls (POSIX functions via FFM) ---
Hostname:    <your-hostname>
Process ID:  <your-pid>
Native mem:  FFM native memory test

--- Custom Library (libffmdemo via FFM) ---
add(17, 25) = 42
factorial(12) = 479001600
fibonacci(0..9) = [0, 1, 1, 2, 3, 5, 8, 13, 21, 34]
distance((0,0), (3,4)) = 5.0
```

## Key FFM Concepts to Note

### The Core Types

- **`Linker`**: The bridge between Java and native code. Provides `downcallHandle()` (Java→native) and `upcallStub()` (native→Java callback).
- **`SymbolLookup`**: Finds native function addresses by name. `defaultLookup()` searches the C standard library; `libraryLookup()` loads custom libraries.
- **`FunctionDescriptor`**: Type-safe description of a native function's signature (return type + parameter types).
- **`MethodHandle`**: A callable reference to the native function. Thread-safe, JIT-optimizable.
- **`Arena`**: Manages the lifecycle of native memory allocations. When the arena closes, all its memory is freed.
- **`MemorySegment`**: A bounded reference to a region of native memory. Provides bounds-checked, type-safe access.
- **`MemoryLayout`** / **`StructLayout`**: Describes the structure of native data (like C structs) with named fields.

### Arena Types

| Arena | Thread Safety | Lifecycle |
|---|---|---|
| `Arena.ofConfined()` | Single-thread only | Explicit close (try-with-resources) |
| `Arena.ofShared()` | Multi-thread safe | Explicit close |
| `Arena.ofAuto()` | Multi-thread safe | GC-managed (like traditional Java objects) |
| `Arena.global()` | Multi-thread safe | Never freed (JVM lifetime) |

### Safety Model

FFM requires `--enable-native-access` at launch to call native functions. This is a deliberate safety measure - native code can crash the JVM, so the developer must explicitly opt in. This is in contrast to JNI, which has no such restriction.

### No C Wrapper Needed (for existing libraries)

The `StringExamples` and `SystemCallExamples` classes call C standard library functions **without writing any C code**. This is impossible with JNI - even calling `strlen()` requires writing a C wrapper file.

### StructLayout vs Manual Offsets

FFM:
```java
StructLayout layout = MemoryLayout.structLayout(
    JAVA_DOUBLE.withName("x"),
    JAVA_DOUBLE.withName("y")
);
VarHandle xHandle = layout.varHandle(PathElement.groupElement("x"));
xHandle.set(point, 0L, 3.14);  // Type-safe, named access
```

JNI equivalent would require manually knowing that `x` is at offset 0 and `y` is at offset 8, or using JNI field accessor functions with `GetFieldID` calls.
