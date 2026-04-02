package com.stevenpg.ffm;

/**
 * Entry point demonstrating all FFM (Foreign Function & Memory) examples.
 * Run with: ./gradlew :ffm-example:run
 *
 * FFM became a standard (non-preview) API in Java 22 via JEP 454.
 * It replaces JNI for most native interop use cases with a pure-Java,
 * type-safe, memory-safe API. No C compiler needed for calling existing
 * native libraries!
 */
public class FfmExamplesMain {

    public static void main(String[] args) {
        System.out.println("=== FFM (Foreign Function & Memory) Examples ===");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println();

        StringExamples.run();
        MemoryExamples.run();
        SystemCallExamples.run();
        CustomLibraryExamples.run();
    }
}
