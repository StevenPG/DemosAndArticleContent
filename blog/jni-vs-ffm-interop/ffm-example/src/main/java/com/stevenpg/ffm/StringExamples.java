package com.stevenpg.ffm;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Demonstrates calling C standard library string functions using FFM.
 *
 * Key FFM concepts:
 * - Linker: bridges Java method calls to native function calls
 * - SymbolLookup: finds native function addresses by name
 * - FunctionDescriptor: describes the native function signature (return type + parameter types)
 * - MethodHandle: the callable reference to the native function
 * - Arena: manages the lifecycle of off-heap memory allocations
 */
public class StringExamples {

    public static void run() {
        System.out.println("--- String Operations (C Standard Library via FFM) ---");

        // The Linker is the main entry point for FFM native calls
        Linker linker = Linker.nativeLinker();
        // Default lookup finds symbols in the C standard library
        SymbolLookup stdlib = linker.defaultLookup();

        try {
            callStrlen(linker, stdlib);
            callToUpper(linker, stdlib);
        } catch (Throwable e) {
            System.err.println("Error in string examples: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * Call C strlen() - the simplest possible FFM example.
     * C signature: size_t strlen(const char *s)
     */
    private static void callStrlen(Linker linker, SymbolLookup stdlib) throws Throwable {
        // 1. Find the native function
        MemorySegment strlenAddr = stdlib.find("strlen")
                .orElseThrow(() -> new RuntimeException("strlen not found"));

        // 2. Describe its signature: long strlen(pointer)
        FunctionDescriptor strlenDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,       // return type: size_t (mapped to long)
                ValueLayout.ADDRESS           // parameter: const char*
        );

        // 3. Create a MethodHandle (callable reference)
        MethodHandle strlen = linker.downcallHandle(strlenAddr, strlenDesc);

        // 4. Call it! Arena manages the native memory lifecycle.
        try (Arena arena = Arena.ofConfined()) {
            // Allocate a C string in off-heap memory
            MemorySegment cString = arena.allocateFrom("Hello from FFM!");
            long length = (long) strlen.invoke(cString);
            System.out.println("strlen(\"Hello from FFM!\") = " + length);
        }
        // Arena.close() automatically frees the native memory - no manual free() needed!
    }

    /**
     * Call C toupper() on each character to uppercase a string.
     * C signature: int toupper(int c)
     * Demonstrates calling a function in a loop.
     */
    private static void callToUpper(Linker linker, SymbolLookup stdlib) throws Throwable {
        MemorySegment toupperAddr = stdlib.find("toupper")
                .orElseThrow(() -> new RuntimeException("toupper not found"));

        FunctionDescriptor toupperDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,   // return: int
                ValueLayout.JAVA_INT    // param: int (character)
        );

        MethodHandle toupper = linker.downcallHandle(toupperAddr, toupperDesc);

        String input = "Hello from FFM!";
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            int upper = (int) toupper.invoke((int) c);
            result.append((char) upper);
        }
        System.out.println("toupper(\"" + input + "\") = " + result);
    }
}
