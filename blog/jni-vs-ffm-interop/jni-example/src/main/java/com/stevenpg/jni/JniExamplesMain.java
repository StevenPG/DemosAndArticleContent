package com.stevenpg.jni;

import java.util.Arrays;

/**
 * =====================================================================
 * JniExamplesMain - Entry Point for All JNI Examples
 * =====================================================================
 *
 * Run with: ./gradlew :jni-example:run
 *
 * This will automatically:
 *   1. Compile the Java source files
 *   2. Generate JNI header files (javac -h) for all native classes
 *   3. Compile the C source files into libjniexamples.so
 *   4. Run this main class with -Djava.library.path pointing to the .so
 *
 * PREREQUISITES:
 *   - Java 25 (for compilation and running)
 *   - GCC or compatible C compiler (for compiling native code)
 *   - Linux (this example uses Linux-specific paths and .so extension)
 *     See the parent README for macOS/Windows adaptation notes
 *
 * WHAT HAPPENS WHEN YOU RUN THIS:
 *   1. The JVM loads this class
 *   2. When NativeStringUtils/NativeArrayOps/etc. are first used, their
 *      static initializers call System.loadLibrary("jniexamples")
 *   3. The JVM searches java.library.path for libjniexamples.so
 *   4. The .so file is loaded into the JVM process via dlopen()
 *   5. JNI_OnLoad() runs (caches JavaVM* for threading examples)
 *   6. Each native method call is dispatched to the corresponding C function
 */
public class JniExamplesMain {

    public static void main(String[] args) {
        System.out.println("=== JNI (Java Native Interface) Examples ===");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println();

        runStringExamples();
        runArrayExamples();
        runSystemInfoExamples();
        runCallbackExamples();
        runThreadingExamples();
    }

    private static void runStringExamples() {
        System.out.println("--- String Operations (NativeStringUtils) ---");
        var stringUtils = new NativeStringUtils();

        String original = "Hello from JNI!";
        System.out.println("Original:    " + original);
        System.out.println("Uppercase:   " + stringUtils.toUpperCase(original));
        System.out.println("Reversed:    " + stringUtils.reverse(original));
        System.out.println("Count 'l':   " + stringUtils.countChar(original, 'l'));
        System.out.println();
    }

    private static void runArrayExamples() {
        System.out.println("--- Array Operations (NativeArrayOps) ---");
        var arrayOps = new NativeArrayOps();

        int[] data = {42, 17, 8, 99, 3, 61, 25};
        System.out.println("Original:    " + Arrays.toString(data));
        System.out.println("Sum:         " + arrayOps.sumArray(data));

        int[] scaled = arrayOps.scaleArray(data, 3);
        System.out.println("Scaled (x3): " + Arrays.toString(scaled));

        // Use a fresh copy for sorting since sortArray modifies in-place
        int[] toSort = {42, 17, 8, 99, 3, 61, 25};
        arrayOps.sortArray(toSort);
        System.out.println("Sorted:      " + Arrays.toString(toSort));
        System.out.println();
    }

    private static void runSystemInfoExamples() {
        System.out.println("--- System Info (NativeSystemInfo) ---");
        var sysInfo = new NativeSystemInfo();

        System.out.println("Hostname:    " + sysInfo.getHostname());
        System.out.println("Process ID:  " + sysInfo.getProcessId());

        String nativeMsg = sysInfo.allocateAndReadNativeMemory("JNI native memory test");
        System.out.println("Native mem:  " + nativeMsg);
        System.out.println();
    }

    private static void runCallbackExamples() {
        System.out.println("--- Callbacks / Upcalls (NativeCallback) ---");
        System.out.println("C code calling back into Java to report progress:");
        var callback = new NativeCallback();

        // C code will call onProgress() on each iteration
        callback.processWithCallback(5);

        System.out.println();
        System.out.println("C code calling Java's transformString() for each element:");
        String[] input = {"hello", "world", "jni"};
        System.out.println("Input:  " + Arrays.toString(input));

        String[] transformed = callback.processStringsNatively(input);
        System.out.println("Output: " + Arrays.toString(transformed));
        System.out.println();
    }

    private static void runThreadingExamples() {
        System.out.println("--- Multi-Threading (NativeThreading) ---");
        System.out.println("Native threads calling back into Java via AttachCurrentThread:");
        var threading = new NativeThreading();

        // Creates 4 native pthreads, each attaches to JVM and calls back
        threading.runNativeThreads(4);
        System.out.println();
    }
}
