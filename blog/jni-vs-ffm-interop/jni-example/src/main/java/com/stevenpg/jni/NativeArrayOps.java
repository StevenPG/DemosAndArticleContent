package com.stevenpg.jni;

/**
 * Demonstrates JNI array operations.
 * Working with arrays in JNI requires pinning/unpinning to avoid
 * garbage collector interference - a common source of bugs.
 */
public class NativeArrayOps {

    static {
        System.loadLibrary("jniexamples");
    }

    /**
     * Sums all elements of an integer array.
     * Demonstrates: accessing Java array elements from native code.
     * JNI requires GetIntArrayElements/ReleaseIntArrayElements pairs.
     */
    public native long sumArray(int[] values);

    /**
     * Sorts an integer array in-place using native qsort.
     * Demonstrates: modifying a Java array from native code.
     * Changes are reflected back in the Java array.
     */
    public native void sortArray(int[] values);

    /**
     * Multiplies each element by a scalar, returning a new array.
     * Demonstrates: creating and returning a new Java array from native code.
     */
    public native int[] scaleArray(int[] values, int scalar);
}
