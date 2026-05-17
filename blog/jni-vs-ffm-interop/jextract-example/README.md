# jextract Example

Demonstrates [jextract](https://github.com/openjdk/jextract) - a tool that **automatically generates Java FFM bindings** from C header files, eliminating the need to manually write `FunctionDescriptor`, `StructLayout`, `VarHandle`, and `MethodHandle` code.

## What Is jextract?

jextract reads a C header file and produces Java source files containing all the FFM boilerplate. Think of it as a code generator that bridges C APIs to Java automatically.

```
                                jextract
geometry.h ──────────────────────────────────────► Java source files
  (C header with structs,                           (FFM bindings with
   functions, enums, #defines)                       MethodHandles, StructLayouts,
                                                     VarHandles, constants)
```

## Why Use jextract?

| Without jextract (manual FFM) | With jextract (auto-generated) |
|---|---|
| Write `FunctionDescriptor` for each function | Auto-generated |
| Write `StructLayout` for each struct | Auto-generated |
| Write `VarHandle` for each field | Auto-generated with getter/setter helpers |
| Map each `#define` constant manually | Auto-generated as `static final` |
| Map each enum manually | Auto-generated as constants |
| Handle nested structs manually | Auto-generated with proper nesting |
| Error-prone manual offset calculations | Correct by construction |

### Concrete Example

```java
// ─── WITHOUT jextract (manual FFM - 12 lines) ───

StructLayout circleLayout = MemoryLayout.structLayout(
    MemoryLayout.structLayout(
        JAVA_DOUBLE.withName("x"),
        JAVA_DOUBLE.withName("y")
    ).withName("center"),
    JAVA_DOUBLE.withName("radius")
);

MethodHandle circleArea = linker.downcallHandle(
    lib.find("circle_area").orElseThrow(),
    FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS));

try (Arena arena = Arena.ofConfined()) {
    MemorySegment circle = arena.allocate(circleLayout);
    // manually set fields by offset...
    double area = (double) circleArea.invoke(circle);
}

// ─── WITH jextract (auto-generated - 4 lines) ───

try (Arena arena = Arena.ofConfined()) {
    MemorySegment circle = Circle.allocate(arena);
    Circle.radius(circle, 5.0);
    double area = geometry_h.circle_area(circle);
}
```

## Project Structure

```
jextract-example/
├── src/main/native/
│   ├── geometry.h            ← C header (input to jextract)
│   └── geometry.c            ← C implementation (compiled to .so)
├── src/main/java/.../
│   └── JextractExampleMain.java  ← Manual FFM equivalent (for comparison)
├── generate-bindings.sh      ← Script to run jextract
└── build.gradle              ← Includes optional generateBindings task
```

## Running the Manual FFM Example

This works without jextract installed:

```bash
./gradlew :jextract-example:run
```

<!-- TODO: Paste actual output after running -->

### Expected Output

```
=== jextract Example (Manual FFM Equivalent) ===
Java Version: 25

--- Circle Operations ---
Circle(center=(0,0), radius=5): area=78.54, perimeter=31.42

--- Rectangle Operations ---
Rectangle(topLeft=(10,20), bottomRight=(50,80)): area=2400, perimeter=200

--- Point-in-Circle Test ---
  Point(5.0, 5.0): INSIDE circle (distance from center: 0.00)
  Point(6.0, 6.0): INSIDE circle (distance from center: 1.41)
  Point(8.0, 8.0): OUTSIDE circle (distance from center: 4.24)
  Point(5.0, 7.9): INSIDE circle (distance from center: 2.90)
  Point(5.0, 8.1): OUTSIDE circle (distance from center: 3.10)
```

## Running jextract (Optional)

To generate the FFM bindings automatically:

### 1. Install jextract

<!-- TODO: Verify exact download URL for jextract compatible with Java 25 -->

```bash
# Download from https://jdk.java.net/jextract/
# Extract and add to PATH:
export PATH=$PATH:/path/to/jextract-25/bin

# Verify installation:
jextract --version
```

### 2. Generate bindings

```bash
# Option A: Use the script
./jextract-example/generate-bindings.sh

# Option B: Use the Gradle task
./gradlew :jextract-example:generateBindings

# Option C: Run jextract directly
jextract --output src/main/generated-java \
         --target-package com.stevenpg.generated \
         --library geometry \
         src/main/native/geometry.h
```

### 3. What gets generated

jextract produces Java source files like:

```java
// geometry_h.java - Function bindings
public class geometry_h {
    public static double circle_area(MemorySegment c) {
        // Generated MethodHandle + FunctionDescriptor
        return (double) circle_area$MH.invokeExact(c);
    }
    // ... all other functions ...
}

// Point2D.java - Struct layout + field accessors
public class Point2D {
    public static final StructLayout layout = ...;  // Auto-generated
    
    public static MemorySegment allocate(SegmentAllocator arena) { ... }
    public static double x(MemorySegment seg) { ... }
    public static void x(MemorySegment seg, double value) { ... }
    public static double y(MemorySegment seg) { ... }
    public static void y(MemorySegment seg, double value) { ... }
}

// Circle.java - Nested struct with Point2D
public class Circle {
    public static final StructLayout layout = ...;
    
    public static MemorySegment center(MemorySegment seg) { ... }  // Returns Point2D slice
    public static double radius(MemorySegment seg) { ... }
    public static void radius(MemorySegment seg, double value) { ... }
}
```

## When to Use jextract

- **Large C APIs**: OpenGL, Vulkan, SQLite, libcurl, POSIX, Win32 - any API with dozens or hundreds of functions
- **Complex struct hierarchies**: Nested structs, arrays of structs, unions
- **Frequent C API changes**: Re-run jextract when the header changes instead of manually updating bindings
- **Correctness**: jextract reads the actual C compiler's layout information, so struct sizes, alignment, and padding are guaranteed correct

## When NOT to Use jextract

- **Simple APIs**: If you only need to call 2-3 functions, manual FFM is faster to set up
- **Non-C libraries**: jextract only reads C headers (not C++, Rust, etc.)
- **Heavy customization**: If you need to wrap the FFM calls with Java-friendly abstractions, you'll be layering code on top of the generated code anyway

## Relationship to FFM

jextract is a **companion tool** for FFM, not a replacement:

```
                   ┌──────────────────────┐
                   │  Your Java Code      │
                   └──────────┬───────────┘
                              │ calls
                   ┌──────────▼───────────┐
  jextract ──────► │  Generated FFM Code  │  ◄── or hand-written FFM code
  (optional)       │  (MethodHandles,     │
                   │   StructLayouts...)   │
                   └──────────┬───────────┘
                              │ uses
                   ┌──────────▼───────────┐
                   │  FFM API (JDK 22+)   │
                   │  (Linker, Arena,     │
                   │   MemorySegment...)   │
                   └──────────┬───────────┘
                              │ calls
                   ┌──────────▼───────────┐
                   │  Native Library      │
                   │  (.so / .dylib)      │
                   └──────────────────────┘
```
