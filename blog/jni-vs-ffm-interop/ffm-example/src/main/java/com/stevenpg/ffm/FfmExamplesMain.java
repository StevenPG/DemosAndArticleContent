package com.stevenpg.ffm;

/**
 * =====================================================================
 * FfmExamplesMain - Entry Point for All FFM Examples
 * =====================================================================
 *
 * Run with: ./gradlew :ffm-example:run
 *
 * This will automatically:
 *   1. Compile the custom C library (libffmdemo.so) - only needed for
 *      CustomLibraryExamples, the other examples use C stdlib directly!
 *   2. Run this main class with --enable-native-access=ALL-UNNAMED
 *
 * FFM (Foreign Function & Memory API) became a STANDARD API in Java 22
 * via JEP 454. It is the modern replacement for JNI with these benefits:
 *
 *   - PURE JAVA: Call C functions without writing any C code
 *   - MEMORY SAFE: Arena-based memory management prevents leaks
 *   - TYPE SAFE: FunctionDescriptor + ValueLayout catch type mismatches
 *   - BOUNDS CHECKED: MemorySegment prevents buffer overflows
 *   - DETERMINISTIC: Arena.close() frees all memory immediately (not GC-dependent)
 *
 * The examples progress from simple to advanced:
 *   1. StringExamples    : Basic pattern - call strlen/toupper from C stdlib
 *   2. MemoryExamples    : Arena, MemorySegment, StructLayout, SequenceLayout
 *   3. SystemCallExamples: POSIX calls + errno capture (no C code!)
 *   4. CustomLibraryExamples: Load your own .so and call its functions
 *   5. UpcallExamples    : Native code calling BACK into Java (qsort comparator)
 *   6. AdvancedExamples  : Variadic functions (snprintf), nested structs, unions
 *
 * REQUIRED JVM FLAGS:
 *   --enable-native-access=ALL-UNNAMED
 *
 *   This is a SAFETY REQUIREMENT. FFM can crash the JVM if used incorrectly
 *   (just like JNI), so Java requires explicit opt-in. In a modular app,
 *   you'd specify the module name instead of ALL-UNNAMED.
 */
public class FfmExamplesMain {

    public static void main(String[] args) {
        System.out.println("=== FFM (Foreign Function & Memory) Examples ===");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println();

        // Basic: calling C stdlib functions directly from Java
        StringExamples.run();

        // Memory management: Arena, StructLayout, SequenceLayout, slicing
        MemoryExamples.run();

        // System calls: gethostname, getpid, malloc/free, errno capture
        SystemCallExamples.run();

        // Custom library: loading your own .so and calling its functions
        CustomLibraryExamples.run();

        // Upcalls: native code calling back into Java (qsort with Java comparator)
        UpcallExamples.run();

        // Advanced: variadic functions (snprintf), nested structs, unions
        AdvancedExamples.run();
    }
}
