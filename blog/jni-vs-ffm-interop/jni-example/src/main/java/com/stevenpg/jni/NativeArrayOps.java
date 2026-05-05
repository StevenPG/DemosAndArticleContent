package com.stevenpg.jni;

/**
 * =====================================================================
 * NativeArrayOps - JNI Array Operations
 * =====================================================================
 *
 * Arrays in JNI are one of the more complex topics because they live on
 * the Java heap, which is managed by the garbage collector (GC). When you
 * pass a Java array to native code, the JVM has two choices:
 *
 *   Option A: "Pin" the array in memory (prevent GC from moving it) and
 *             give C code a direct pointer to the Java heap data.
 *             Fast, but blocks the GC from compacting that memory region.
 *
 *   Option B: Copy the array data into a native buffer, give C code a
 *             pointer to the copy, and copy changes back when released.
 *             Slower, but doesn't interfere with the GC.
 *
 * The JVM decides which strategy to use - you don't control it. However,
 * you DO control what happens when you RELEASE the array:
 *
 *   Mode 0:         Copy changes back to Java array AND free the buffer
 *   JNI_COMMIT:     Copy changes back but DON'T free (for incremental updates)
 *   JNI_ABORT:      DON'T copy changes back, just free (read-only use)
 *
 * CRITICAL RULE: Every GetXxxArrayElements() MUST have a matching
 * ReleaseXxxArrayElements(). Missing releases cause memory leaks.
 *
 * PERFORMANCE NOTE:
 *   For very large arrays where you only need to read/write a portion,
 *   consider GetIntArrayRegion/SetIntArrayRegion instead - they copy
 *   only a subset of elements and don't require explicit release.
 *
 *   For the highest performance (with the most restrictions), use
 *   GetPrimitiveArrayCritical/ReleasePrimitiveArrayCritical. These
 *   give you a direct pointer but you MUST NOT:
 *     - Call any other JNI functions
 *     - Block the current thread
 *     - Allocate any Java objects
 *   Violating these rules can deadlock the JVM.
 *
 * SEE ALSO: The corresponding C implementation is in:
 *   src/main/native/native_array_ops.c
 */
public class NativeArrayOps {

    static {
        System.loadLibrary("jniexamples");
    }

    /**
     * Sums all elements of an integer array using native code.
     *
     * This is a read-only operation. The C implementation:
     *   1. Calls GetIntArrayElements to get a jint* pointer to the data
     *   2. Iterates and sums the elements
     *   3. Calls ReleaseIntArrayElements with JNI_ABORT (no copy-back needed)
     *
     * Returns long (not int) to demonstrate that return types can differ
     * from the array element type - useful to avoid overflow.
     *
     * @param values the array to sum
     * @return the sum of all elements (as a long to avoid overflow)
     */
    public native long sumArray(int[] values);

    /**
     * Sorts an integer array IN-PLACE using native C qsort().
     *
     * This is a write operation. The C implementation:
     *   1. Calls GetIntArrayElements to get a mutable jint* pointer
     *   2. Calls C's qsort() to sort the elements
     *   3. Calls ReleaseIntArrayElements with mode 0 (copy changes back)
     *
     * "In-place" means the original Java array is modified. After this
     * method returns, the array you passed in is sorted. This is a key
     * JNI concept: native code can modify Java objects if you release
     * with mode 0 or JNI_COMMIT.
     *
     * @param values the array to sort (modified in-place!)
     */
    public native void sortArray(int[] values);

    /**
     * Multiplies each element by a scalar, returning a NEW array.
     *
     * This demonstrates creating a Java array FROM native code:
     *   1. GetIntArrayElements on the input array (read-only)
     *   2. NewIntArray to create a fresh Java int[] of the same length
     *   3. Compute scaled values into a temp C buffer
     *   4. SetIntArrayRegion to copy the buffer into the new Java array
     *   5. ReleaseIntArrayElements on the input with JNI_ABORT
     *
     * The original array is NOT modified. A brand-new array is returned.
     *
     * @param values the input array (not modified)
     * @param scalar the value to multiply each element by
     * @return a new array where result[i] = values[i] * scalar
     */
    public native int[] scaleArray(int[] values, int scalar);
}
