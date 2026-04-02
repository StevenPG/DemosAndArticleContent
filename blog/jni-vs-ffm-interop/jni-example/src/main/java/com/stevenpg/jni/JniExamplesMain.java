package com.stevenpg.jni;

import java.util.Arrays;

/**
 * Entry point demonstrating all JNI examples.
 * Run with: ./gradlew :jni-example:run
 *
 * NOTE: The native library must be compiled first. The Gradle build
 * handles this automatically, but you need a C compiler (gcc) installed.
 */
public class JniExamplesMain {

    public static void main(String[] args) {
        System.out.println("=== JNI (Java Native Interface) Examples ===");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println();

        runStringExamples();
        runArrayExamples();
        runSystemInfoExamples();
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
}
