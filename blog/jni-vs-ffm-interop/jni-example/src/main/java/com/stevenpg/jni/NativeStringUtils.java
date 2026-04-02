package com.stevenpg.jni;

/**
 * Demonstrates JNI string operations.
 * Each native method requires a corresponding C implementation with
 * a specific naming convention: Java_<package>_<class>_<method>
 */
public class NativeStringUtils {

    // Load the native library - must match the compiled .so/.dylib name
    static {
        System.loadLibrary("jniexamples");
    }

    /**
     * Converts a string to uppercase using native C code.
     * Demonstrates: passing a String to native code and receiving one back.
     */
    public native String toUpperCase(String input);

    /**
     * Reverses a string using native C code.
     * Demonstrates: string manipulation in native code with JNI string functions.
     */
    public native String reverse(String input);

    /**
     * Counts occurrences of a character in a string.
     * Demonstrates: passing primitives alongside objects.
     */
    public native int countChar(String input, char target);
}
