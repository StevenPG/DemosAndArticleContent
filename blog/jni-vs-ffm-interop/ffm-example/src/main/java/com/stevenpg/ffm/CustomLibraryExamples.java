package com.stevenpg.ffm;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;

/**
 * =====================================================================
 * CustomLibraryExamples - Loading Your Own C Libraries with FFM
 * =====================================================================
 *
 * The previous examples called C standard library functions. This class
 * shows how to load and call YOUR OWN compiled C library (.so/.dylib).
 *
 * KEY DIFFERENCE FROM JNI:
 *   With JNI, your C code MUST:
 *     - #include <jni.h>
 *     - Follow the Java_pkg_Class_method naming convention
 *     - Accept JNIEnv* and jobject as first two parameters
 *     - Use JNI functions for all Java ↔ C data conversion
 *     → Your C code is JAVA-SPECIFIC. It can't be used from Python, Rust, etc.
 *
 *   With FFM, your C code is PLAIN C:
 *     - No JNI headers, no JNI naming convention
 *     - Standard C function signatures
 *     - Standard C types (int, double, struct*)
 *     → Your C code is PORTABLE. Use the same .so from Java, Python, Rust, Go.
 *
 * LOADING LIBRARIES:
 *   SymbolLookup.libraryLookup(path, arena):
 *     - Loads a shared library from the filesystem
 *     - The library stays loaded for the arena's lifetime
 *     - Returns a SymbolLookup to find symbols in that library
 *     - Equivalent to dlopen() in C (or System.loadLibrary() in JNI)
 *
 * SEE ALSO: The C library source is in:
 *   src/main/native/math_utils.c
 *   Notice how it has NO JNI dependencies - just plain C.
 */
public class CustomLibraryExamples {

    public static void run() {
        System.out.println("--- Custom Library (libffmdemo via FFM) ---");

        Linker linker = Linker.nativeLinker();

        try {
            /*
             * Load our custom shared library by file path.
             *
             * SymbolLookup.libraryLookup takes:
             *   1. Path to the .so/.dylib file
             *   2. An Arena that controls the library's lifetime
             *
             * Arena.ofAuto() means the library stays loaded until GC
             * collects the Arena. For a library you use throughout your
             * app's lifetime, this is appropriate. For temporary library
             * usage, Arena.ofConfined() would unload it when closed.
             *
             * PLATFORM DIFFERENCES:
             *   Linux:   libffmdemo.so
             *   macOS:   libffmdemo.dylib
             *   Windows: ffmdemo.dll
             */
            String libPath = System.getProperty("java.library.path") + "/libffmdemo.so";
            SymbolLookup customLib = SymbolLookup.libraryLookup(
                    Path.of(libPath), Arena.ofAuto());

            callAdd(linker, customLib);
            callFactorial(linker, customLib);
            callFibonacci(linker, customLib);
            callDistance(linker, customLib);
        } catch (Throwable e) {
            System.err.println("Error in custom library examples: " + e.getMessage());
            System.err.println("Make sure the native library is compiled:");
            System.err.println("  ./gradlew :ffm-example:compileNative");
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * Calls: int add(int a, int b)
     *
     * The simplest possible custom function call - takes two ints, returns an int.
     * No memory management, no structs, no cleanup needed.
     */
    private static void callAdd(Linker linker, SymbolLookup lib) throws Throwable {
        /*
         * lib.find("add") searches our custom library for a symbol named "add".
         *
         * The function name is EXACTLY what's in the C source code.
         * No mangling, no "Java_" prefix, no package encoding.
         * This is a huge simplicity win over JNI.
         */
        MethodHandle add = linker.downcallHandle(
                lib.find("add").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,          // return: int
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)  // params: int, int
        );

        /* Call it just like a regular Java method */
        int result = (int) add.invoke(17, 25);
        System.out.println("add(17, 25) = " + result);
    }

    /**
     * Calls: long factorial(int n)
     *
     * Note: C's 'long' is 8 bytes on 64-bit Linux (maps to JAVA_LONG).
     * On Windows, C's 'long' is only 4 bytes! Use 'long long' or 'int64_t'
     * in C for portable 64-bit integers.
     */
    private static void callFactorial(Linker linker, SymbolLookup lib) throws Throwable {
        MethodHandle factorial = linker.downcallHandle(
                lib.find("factorial").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG,   // return: long (8 bytes)
                        ValueLayout.JAVA_INT)                  // param: int
        );

        long result = (long) factorial.invoke(12);
        System.out.println("factorial(12) = " + result);
    }

    /**
     * Calls: long fibonacci(int n) in a loop.
     *
     * Demonstrates that MethodHandles are efficient for repeated calls.
     * Create the handle once, invoke it many times.
     */
    private static void callFibonacci(Linker linker, SymbolLookup lib) throws Throwable {
        MethodHandle fibonacci = linker.downcallHandle(
                lib.find("fibonacci").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
        );

        StringBuilder sb = new StringBuilder("fibonacci(0..9) = [");
        for (int i = 0; i < 10; i++) {
            if (i > 0) sb.append(", ");
            /* Each invoke is a separate native function call */
            sb.append((long) fibonacci.invoke(i));
        }
        sb.append("]");
        System.out.println(sb);
    }

    /**
     * Calls: double distance(const Point2D *p1, const Point2D *p2)
     *
     * This is the most interesting example because it involves:
     *   1. Defining a struct layout in Java that matches the C struct
     *   2. Allocating and populating struct instances in native memory
     *   3. Passing struct pointers to a native function
     *
     * The C struct is:
     *   typedef struct {
     *       double x;    // 8 bytes at offset 0
     *       double y;    // 8 bytes at offset 8
     *   } Point2D;       // total: 16 bytes
     *
     * COMPARE WITH JNI:
     *   In JNI, you'd either:
     *   a) Pass x1, y1, x2, y2 as separate doubles (losing the struct concept)
     *   b) Pass a Java object and use GetDoubleField in C (tedious, slow)
     *   c) Manually pack bytes into a ByteBuffer (error-prone)
     *
     *   FFM gives you named, type-safe struct access - the best of all worlds.
     */
    private static void callDistance(Linker linker, SymbolLookup lib) throws Throwable {
        /*
         * Define the Point2D struct layout.
         * This MUST match the C struct's memory layout exactly:
         *   - Same field types
         *   - Same field order
         *   - Same alignment/padding
         *
         * For this simple struct (two doubles), there's no padding.
         * For structs with mixed types (e.g., int + double), the C compiler
         * adds padding for alignment. FFM's structLayout handles this
         * automatically (using the platform's alignment rules).
         */
        StructLayout point2dLayout = MemoryLayout.structLayout(
                ValueLayout.JAVA_DOUBLE.withName("x"),  // offset 0, 8 bytes
                ValueLayout.JAVA_DOUBLE.withName("y")   // offset 8, 8 bytes
        );

        /*
         * The distance function takes two Point2D* (pointers to structs).
         * In FFM, any pointer is represented as ADDRESS.
         */
        MethodHandle distance = linker.downcallHandle(
                lib.find("distance").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,        // return: double
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS)     // params: Point2D*, Point2D*
        );

        /*
         * Create VarHandles for named field access.
         * These give us type-safe setters instead of manual offset math.
         */
        VarHandle xHandle = point2dLayout.varHandle(
                MemoryLayout.PathElement.groupElement("x"));
        VarHandle yHandle = point2dLayout.varHandle(
                MemoryLayout.PathElement.groupElement("y"));

        try (Arena arena = Arena.ofConfined()) {
            /* Allocate two Point2D structs in native memory */
            MemorySegment p1 = arena.allocate(point2dLayout);
            MemorySegment p2 = arena.allocate(point2dLayout);

            /* Set p1 = (0.0, 0.0) using VarHandles */
            xHandle.set(p1, 0L, 0.0);
            yHandle.set(p1, 0L, 0.0);

            /* Set p2 = (3.0, 4.0) - a classic 3-4-5 right triangle */
            xHandle.set(p2, 0L, 3.0);
            yHandle.set(p2, 0L, 4.0);

            /*
             * Call distance(p1, p2).
             *
             * FFM automatically passes the native memory addresses of p1 and p2
             * as the const Point2D* parameters. The C function reads the struct
             * fields from native memory and computes sqrt(dx² + dy²) = 5.0.
             */
            double dist = (double) distance.invoke(p1, p2);
            System.out.printf("distance((0,0), (3,4)) = %.1f%n", dist);
        }
    }
}
