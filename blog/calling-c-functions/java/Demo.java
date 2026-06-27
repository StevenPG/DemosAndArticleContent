// Call the C `mathlib` shared library from Java using the Foreign Function &
// Memory API (a.k.a. Project Panama), which is *final* in Java 22+ — no JNI,
// no third-party libraries, no hand-written native glue.
//
// Run as a single source file (Java 25):
//   java --enable-native-access=ALL-UNNAMED Demo.java
//
// Core ideas:
//   * Linker          - the bridge to the native ABI
//   * SymbolLookup    - finds exported functions in the loaded library
//   * MethodHandle    - a callable handle to a native function
//   * Arena           - scoped native memory; everything is freed on close()
//   * MemoryLayout    - describes C types so the runtime can marshal them

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;

public class Demo {

    static final ValueLayout.OfInt    C_INT    = ValueLayout.JAVA_INT;
    static final ValueLayout.OfLong   C_LONG   = ValueLayout.JAVA_LONG;
    static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE;
    // On macOS/Linux x86-64 & AArch64, C `long` and `size_t` are 64-bit.
    static final ValueLayout C_SIZE_T = ValueLayout.JAVA_LONG;
    // C pointer (char*, int*, ...).
    static final java.lang.foreign.AddressLayout C_PTR = ValueLayout.ADDRESS;

    // Layout of `struct { double x; double y; }`.
    static final StructLayout POINT = MemoryLayout.structLayout(
            C_DOUBLE.withName("x"),
            C_DOUBLE.withName("y")
    );
    static final VarHandle POINT_X = POINT.varHandle(MemoryLayout.PathElement.groupElement("x"));
    static final VarHandle POINT_Y = POINT.varHandle(MemoryLayout.PathElement.groupElement("y"));

    public static void main(String[] args) throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup lookup = SymbolLookup.libraryLookup(libraryPath(), Arena.global());

        MethodHandle add = linker.downcallHandle(
                lookup.find("add").orElseThrow(),
                FunctionDescriptor.of(C_INT, C_INT, C_INT));

        MethodHandle fibonacci = linker.downcallHandle(
                lookup.find("fibonacci").orElseThrow(),
                FunctionDescriptor.of(C_LONG, C_INT));

        MethodHandle greet = linker.downcallHandle(
                lookup.find("greet").orElseThrow(),
                FunctionDescriptor.of(C_PTR, C_PTR));

        MethodHandle freeString = linker.downcallHandle(
                lookup.find("free_string").orElseThrow(),
                FunctionDescriptor.ofVoid(C_PTR));

        MethodHandle distance = linker.downcallHandle(
                lookup.find("distance").orElseThrow(),
                FunctionDescriptor.of(C_DOUBLE, POINT, POINT));

        MethodHandle midpoint = linker.downcallHandle(
                lookup.find("midpoint").orElseThrow(),
                FunctionDescriptor.of(POINT, POINT, POINT));

        MethodHandle sumArray = linker.downcallHandle(
                lookup.find("sum_array").orElseThrow(),
                FunctionDescriptor.of(C_LONG, C_PTR, C_SIZE_T));

        System.out.println("== scalars ==");
        System.out.println("add(2, 3)        = " + (int) add.invokeExact(2, 3));
        System.out.println("fibonacci(20)    = " + (long) fibonacci.invokeExact(20));

        System.out.println("\n== strings ==");
        System.out.println("greet(\"Ada\")     = " + greet(greet, freeString, "Ada"));

        System.out.println("\n== structs & arrays ==");
        // An Arena owns native memory; try-with-resources frees it deterministically.
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment a = point(arena, 0.0, 0.0);
            MemorySegment b = point(arena, 3.0, 4.0);

            double d = (double) distance.invokeExact(a, b);
            System.out.println("distance(a, b)   = " + d);

            // Struct returns need a SegmentAllocator to place the result.
            MemorySegment m = (MemorySegment) midpoint.invokeExact((java.lang.foreign.SegmentAllocator) arena, a, b);
            System.out.println("midpoint(a, b)   = Point(x=" + (double) POINT_X.get(m, 0L)
                    + ", y=" + (double) POINT_Y.get(m, 0L) + ")");

            int[] numbers = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
            MemorySegment arr = arena.allocateFrom(C_INT, numbers);
            long sum = (long) sumArray.invokeExact(arr, (long) numbers.length);
            System.out.println("sum_array(1..10) = " + sum);
        }
    }

    static MemorySegment point(Arena arena, double x, double y) {
        MemorySegment p = arena.allocate(POINT);
        POINT_X.set(p, 0L, x);
        POINT_Y.set(p, 0L, y);
        return p;
    }

    static String greet(MethodHandle greet, MethodHandle freeString, String name) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cName = arena.allocateFrom(name);          // Java String -> C char*
            MemorySegment result = (MemorySegment) greet.invokeExact(cName);
            // The returned pointer is zero-length; reinterpret it so we can read the bytes.
            String s = result.reinterpret(Long.MAX_VALUE).getString(0);
            freeString.invokeExact(result);                          // hand ownership back to C
            return s;
        }
    }

    static Path libraryPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String suffix = os.contains("mac") ? "dylib" : "so";
        return Path.of("..", "c-library", "build", "libmathlib." + suffix)
                .toAbsolutePath().normalize();
    }
}
