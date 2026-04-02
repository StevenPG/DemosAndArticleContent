package com.stevenpg.ffm;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * Demonstrates calling a custom shared library (.so/.dylib) using FFM.
 *
 * This shows the FFM equivalent of writing a JNI wrapper: loading your own
 * compiled C library and calling its functions. The key difference is that
 * with FFM, the C code doesn't need any JNI-specific headers or naming
 * conventions - it's plain C that can be used by any language.
 */
public class CustomLibraryExamples {

    public static void run() {
        System.out.println("--- Custom Library (libffmdemo via FFM) ---");

        Linker linker = Linker.nativeLinker();

        try {
            // Load our custom shared library
            // SymbolLookup.libraryLookup loads a .so/.dylib by path
            String libPath = System.getProperty("java.library.path") + "/libffmdemo.so";

            // Using Arena.ofAuto() for the library - it stays loaded for the JVM lifetime
            SymbolLookup customLib = SymbolLookup.libraryLookup(
                    Path.of(libPath), Arena.ofAuto());

            callAdd(linker, customLib);
            callFactorial(linker, customLib);
            callFibonacci(linker, customLib);
            callDistance(linker, customLib);
        } catch (Throwable e) {
            System.err.println("Error in custom library examples: " + e.getMessage());
            System.err.println("Make sure the native library is compiled (./gradlew :ffm-example:compileNative)");
            e.printStackTrace();
        }

        System.out.println();
    }

    private static void callAdd(Linker linker, SymbolLookup lib) throws Throwable {
        MethodHandle add = linker.downcallHandle(
                lib.find("add").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );

        int result = (int) add.invoke(17, 25);
        System.out.println("add(17, 25) = " + result);
    }

    private static void callFactorial(Linker linker, SymbolLookup lib) throws Throwable {
        MethodHandle factorial = linker.downcallHandle(
                lib.find("factorial").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
        );

        long result = (long) factorial.invoke(12);
        System.out.println("factorial(12) = " + result);
    }

    private static void callFibonacci(Linker linker, SymbolLookup lib) throws Throwable {
        MethodHandle fibonacci = linker.downcallHandle(
                lib.find("fibonacci").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
        );

        StringBuilder sb = new StringBuilder("fibonacci(0..9) = [");
        for (int i = 0; i < 10; i++) {
            if (i > 0) sb.append(", ");
            sb.append((long) fibonacci.invoke(i));
        }
        sb.append("]");
        System.out.println(sb);
    }

    /**
     * Calls a function that takes a struct pointer and returns a double.
     * Demonstrates passing structured data to a custom native function.
     */
    private static void callDistance(Linker linker, SymbolLookup lib) throws Throwable {
        // Define the Point2D struct layout matching the C struct
        StructLayout point2dLayout = MemoryLayout.structLayout(
                ValueLayout.JAVA_DOUBLE.withName("x"),
                ValueLayout.JAVA_DOUBLE.withName("y")
        );

        MethodHandle distance = linker.downcallHandle(
                lib.find("distance").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        try (Arena arena = Arena.ofConfined()) {
            // Allocate two Point2D structs
            MemorySegment p1 = arena.allocate(point2dLayout);
            MemorySegment p2 = arena.allocate(point2dLayout);

            // Set p1 = (0.0, 0.0)
            p1.set(ValueLayout.JAVA_DOUBLE, 0, 0.0);
            p1.set(ValueLayout.JAVA_DOUBLE, 8, 0.0);

            // Set p2 = (3.0, 4.0)
            p2.set(ValueLayout.JAVA_DOUBLE, 0, 3.0);
            p2.set(ValueLayout.JAVA_DOUBLE, 8, 4.0);

            double dist = (double) distance.invoke(p1, p2);
            System.out.printf("distance((0,0), (3,4)) = %.1f%n", dist);
        }
    }
}
