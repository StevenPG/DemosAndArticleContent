package com.stevenpg.ffm;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * =====================================================================
 * StringExamples - Calling C String Functions via FFM (No C Code Needed!)
 * =====================================================================
 *
 * This class demonstrates the most fundamental FFM pattern: calling
 * functions from the C standard library directly from Java, without
 * writing a single line of C code.
 *
 * CONTRAST WITH JNI:
 *   To call C's strlen() via JNI, you need:
 *     1. A Java class with "native String strlen(String s)"
 *     2. Run javac -h to generate a C header file
 *     3. Write a C file implementing Java_pkg_Class_strlen()
 *     4. In that C file: GetStringUTFChars, call strlen, ReleaseStringUTFChars
 *     5. Compile the C into a .so/.dylib
 *     6. Load it with System.loadLibrary
 *
 *   To call strlen() via FFM, you need:
 *     1. This Java file. That's it. No C code at all.
 *
 * THE FFM "RECIPE" (5 steps to call any native function):
 *
 *   Step 1: Get a Linker
 *     Linker linker = Linker.nativeLinker();
 *     The Linker is the engine that bridges Java → native calls.
 *     It understands the platform's calling convention (how to put
 *     arguments in registers, how to handle return values, etc.)
 *
 *   Step 2: Find the function
 *     MemorySegment addr = lookup.find("strlen").orElseThrow();
 *     SymbolLookup finds native functions by name and returns their
 *     memory address. linker.defaultLookup() searches the C stdlib.
 *
 *   Step 3: Describe the signature
 *     FunctionDescriptor desc = FunctionDescriptor.of(JAVA_LONG, ADDRESS);
 *     This tells FFM: "strlen takes a pointer and returns a long".
 *     Unlike JNI's cryptic type signature strings "(Ljava/lang/String;)J",
 *     FFM uses type-safe Java constants.
 *
 *   Step 4: Create a MethodHandle
 *     MethodHandle handle = linker.downcallHandle(addr, desc);
 *     This creates a callable Java object that, when invoked, will
 *     call the native function. "Downcall" = Java calling down to native.
 *
 *   Step 5: Invoke it
 *     long result = (long) handle.invoke(cString);
 *     Call it like any other MethodHandle. FFM handles the marshalling
 *     of arguments from Java types to native types automatically.
 *
 * TERMINOLOGY:
 *   - Downcall: Java → Native (what we do here)
 *   - Upcall:   Native → Java (see UpcallExamples.java)
 *   - Linker:   The engine that generates the call bridge
 *   - Arena:    Memory scope manager (like try-with-resources for native memory)
 */
public class StringExamples {

    public static void run() {
        System.out.println("--- String Operations (C Standard Library via FFM) ---");

        /*
         * Linker.nativeLinker() returns the platform's native linker.
         *
         * This object knows how to:
         *   - Create downcall handles (Java → native)
         *   - Create upcall stubs (native → Java)
         *   - Describe the platform's calling convention (System V AMD64 ABI
         *     on Linux x64, Microsoft x64 on Windows, etc.)
         *
         * It's thread-safe and can be shared/reused across your application.
         */
        Linker linker = Linker.nativeLinker();

        /*
         * linker.defaultLookup() returns a SymbolLookup that searches the
         * C standard library (libc) and other default libraries.
         *
         * On Linux, this includes: libc, libm, libdl, etc.
         * On macOS: libSystem (which bundles libc, libm, etc.)
         *
         * For your own libraries, use SymbolLookup.libraryLookup() instead
         * (see CustomLibraryExamples.java).
         */
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
     * Calls C's strlen() function - the simplest possible FFM example.
     *
     * C signature: size_t strlen(const char *s)
     *
     * This demonstrates every step of the FFM recipe with detailed
     * explanations of each component.
     */
    private static void callStrlen(Linker linker, SymbolLookup stdlib) throws Throwable {

        /*
         * STEP 1: Find the native function's address.
         *
         * stdlib.find("strlen") searches the C standard library for a symbol
         * named "strlen" and returns its memory address as a MemorySegment.
         *
         * Returns Optional<MemorySegment> because the symbol might not exist
         * (e.g., if you misspell it or it's not in the default libraries).
         *
         * The returned MemorySegment is a zero-length segment pointing to
         * the function's code in memory. It's not the function itself - it's
         * just the address where the function's machine code begins.
         */
        MemorySegment strlenAddr = stdlib.find("strlen")
                .orElseThrow(() -> new RuntimeException("strlen not found"));

        /*
         * STEP 2: Describe the function's signature.
         *
         * FunctionDescriptor tells FFM how to marshal arguments and return values.
         * It's the type-safe equivalent of JNI's cryptic signature strings.
         *
         * FunctionDescriptor.of(returnLayout, paramLayout1, paramLayout2, ...)
         *
         * For strlen:
         *   - Returns: size_t → mapped to JAVA_LONG (64-bit on most platforms)
         *   - Parameter: const char* → mapped to ADDRESS (a native pointer)
         *
         * C TYPE → FFM LAYOUT MAPPING:
         *   int, int32_t      → ValueLayout.JAVA_INT
         *   long, int64_t     → ValueLayout.JAVA_LONG
         *   float              → ValueLayout.JAVA_FLOAT
         *   double             → ValueLayout.JAVA_DOUBLE
         *   char, int8_t      → ValueLayout.JAVA_BYTE
         *   short, int16_t    → ValueLayout.JAVA_SHORT
         *   any pointer (*)    → ValueLayout.ADDRESS
         *   void (return only) → use FunctionDescriptor.ofVoid(...)
         *
         * NOTE: C's 'long' is platform-dependent (32-bit on Windows, 64-bit
         * on Linux). Choose the Java type that matches the ACTUAL size.
         */
        FunctionDescriptor strlenDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,       // return type: size_t → long
                ValueLayout.ADDRESS           // parameter: const char* → pointer
        );

        /*
         * STEP 3: Create a MethodHandle (the callable bridge).
         *
         * linker.downcallHandle() generates machine code that:
         *   1. Takes Java arguments (long, int, MemorySegment, etc.)
         *   2. Converts them to native calling convention (registers, stack)
         *   3. Jumps to the native function at strlenAddr
         *   4. Takes the native return value
         *   5. Converts it back to a Java type
         *
         * The returned MethodHandle is:
         *   - Thread-safe (can be called from multiple threads)
         *   - JIT-optimizable (the JVM can inline the call)
         *   - Reusable (call it as many times as you want)
         *
         * PERFORMANCE: Creating the handle is somewhat expensive (it generates
         * code), but invoking it is very fast. Cache handles for hot paths.
         *
         * "Downcall" means Java is calling DOWN to native code.
         * (The opposite - native calling Java - is an "upcall".)
         */
        MethodHandle strlen = linker.downcallHandle(strlenAddr, strlenDesc);

        /*
         * STEP 4: Allocate native memory and invoke the function.
         *
         * Arena.ofConfined() creates a memory management scope:
         *   - All memory allocated from this arena is freed when it closes
         *   - "Confined" means only the thread that created it can use it
         *   - The try-with-resources ensures the arena closes (and frees memory)
         *
         * COMPARE WITH JNI:
         *   JNI: malloc(size); ... use ...; free(ptr);  // manual, error-prone
         *   FFM: try (Arena a = Arena.ofConfined()) { a.allocate(...) }  // automatic!
         */
        try (Arena arena = Arena.ofConfined()) {
            /*
             * arena.allocateFrom("Hello from FFM!") does three things:
             *   1. Allocates enough native memory for the string + null terminator
             *   2. Copies the Java String's bytes into that native memory
             *   3. Returns a MemorySegment pointing to it
             *
             * The result is equivalent to:
             *   char *s = malloc(strlen("Hello from FFM!") + 1);
             *   strcpy(s, "Hello from FFM!");
             *
             * But the memory is automatically freed when the arena closes!
             */
            MemorySegment cString = arena.allocateFrom("Hello from FFM!");

            /*
             * Call strlen via the MethodHandle.
             *
             * strlen.invoke(cString) is equivalent to:
             *   strlen(cString.address())
             *
             * FFM automatically extracts the native address from the
             * MemorySegment and passes it to strlen as a const char*.
             *
             * The return value (size_t / long) is auto-boxed by invoke().
             * We cast it to (long) to unbox.
             */
            long length = (long) strlen.invoke(cString);
            System.out.println("strlen(\"Hello from FFM!\") = " + length);
        }
        /*
         * When the arena closes (here, at the end of try-with-resources):
         *   - All memory allocated from it is freed
         *   - Any MemorySegments from it become invalid
         *   - Accessing them throws IllegalStateException
         *
         * This is FFM's answer to JNI's "did you remember to call free()?" problem.
         */
    }

    /**
     * Calls C's toupper() on each character to uppercase a string.
     *
     * C signature: int toupper(int c)
     *
     * This demonstrates calling a function in a loop - a pattern that
     * shows MethodHandles are lightweight to invoke repeatedly.
     */
    private static void callToUpper(Linker linker, SymbolLookup stdlib) throws Throwable {
        MemorySegment toupperAddr = stdlib.find("toupper")
                .orElseThrow(() -> new RuntimeException("toupper not found"));

        /*
         * toupper takes an int (character code) and returns an int.
         * Even though we're working with characters, the C function
         * uses int (not char) - this is standard C convention.
         */
        FunctionDescriptor toupperDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,   // return: int (uppercase char code)
                ValueLayout.JAVA_INT    // param: int (character code to convert)
        );

        MethodHandle toupper = linker.downcallHandle(toupperAddr, toupperDesc);

        /*
         * Call toupper for each character.
         *
         * This doesn't need an Arena because we're passing primitives (int),
         * not pointers. No native memory allocation needed.
         *
         * Each invoke() is a native function call, but the overhead is
         * minimal - comparable to a virtual method call in Java.
         */
        String input = "Hello from FFM!";
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            int upper = (int) toupper.invoke((int) c);
            result.append((char) upper);
        }
        System.out.println("toupper(\"" + input + "\") = " + result);
    }
}
