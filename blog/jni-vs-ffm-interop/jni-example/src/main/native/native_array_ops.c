/*
 * =====================================================================
 * native_array_ops.c - JNI Array Operations (C Implementation)
 * =====================================================================
 *
 * Working with Java arrays in JNI is more complex than working with
 * strings because arrays can be MODIFIED in-place, which raises questions
 * about when and how changes are visible to Java.
 *
 * JNI ARRAY ACCESS STRATEGIES:
 *
 * Strategy 1: GetXxxArrayElements / ReleaseXxxArrayElements
 *   - Gets a pointer to the entire array
 *   - JVM may PIN the array (direct access) or COPY it (indirect)
 *   - You choose what happens on release: copy back, don't copy, or both
 *   - Best for: accessing most/all elements
 *
 * Strategy 2: GetXxxArrayRegion / SetXxxArrayRegion
 *   - Copies a RANGE of elements to/from a C buffer
 *   - Always copies (never pins)
 *   - No release needed (the C buffer is yours)
 *   - Best for: accessing a subset of a large array
 *
 * Strategy 3: GetPrimitiveArrayCritical / ReleasePrimitiveArrayCritical
 *   - Highest performance - likely gives a direct pointer
 *   - SEVERE restrictions: no other JNI calls, no blocking, no allocation
 *   - Best for: short, hot loops over array data
 *
 * RELEASE MODES (for Strategy 1):
 *   ┌───────────────┬──────────────────────────────────────────────────┐
 *   │ Mode          │ Behavior                                        │
 *   ├───────────────┼──────────────────────────────────────────────────┤
 *   │ 0             │ Copy changes back to Java array, free C buffer  │
 *   │ JNI_COMMIT    │ Copy changes back, but DON'T free (keep buffer) │
 *   │ JNI_ABORT     │ DON'T copy back, just free (discard changes)    │
 *   └───────────────┴──────────────────────────────────────────────────┘
 */

#include <jni.h>
#include <stdlib.h>    /* qsort, malloc, free */

/*
 * Java_com_stevenpg_jni_NativeArrayOps_sumArray
 *
 * Sums all elements of a Java int[] array.
 * This is a READ-ONLY operation, so we use JNI_ABORT on release.
 */
JNIEXPORT jlong JNICALL Java_com_stevenpg_jni_NativeArrayOps_sumArray
  (JNIEnv *env, jobject obj, jintArray values) {

    /*
     * GetIntArrayElements returns a pointer to the array data.
     *
     * The pointer type is jint* (which is int32_t* on most platforms).
     * The third parameter (isCopy) tells you if the JVM made a copy:
     *   JNI_TRUE  → you got a copy, changes won't be reflected unless you
     *                release with mode 0 or JNI_COMMIT
     *   JNI_FALSE → you got a direct pointer to the Java heap data
     *
     * We pass NULL because we don't care (it's read-only for us).
     *
     * RETURNS NULL if the JVM can't allocate the copy buffer.
     */
    jint *elements = (*env)->GetIntArrayElements(env, values, NULL);
    if (elements == NULL) return 0;  /* OOM - pending exception set */

    /* GetArrayLength returns the number of elements.
     * This is like Java's array.length but from C. */
    jsize len = (*env)->GetArrayLength(env, values);

    /* Do the sum. Note we use jlong (64-bit) to avoid overflow,
     * since summing many jint (32-bit) values could exceed 2^31. */
    jlong sum = 0;
    for (jsize i = 0; i < len; i++) {
        sum += elements[i];
    }

    /*
     * Release with JNI_ABORT because we didn't modify the array.
     *
     * JNI_ABORT says: "I'm done with this buffer. Don't bother copying
     * any data back to the Java array. Just free the native buffer."
     *
     * If the JVM had pinned the array (no copy), JNI_ABORT is essentially
     * a no-op. If it made a copy, JNI_ABORT frees the copy without
     * writing anything back.
     */
    (*env)->ReleaseIntArrayElements(env, values, elements, JNI_ABORT);
    return sum;
}

/*
 * Comparator function for C's qsort().
 *
 * qsort requires a function pointer with this exact signature:
 *   int compare(const void *a, const void *b)
 *
 * Returns: negative if a < b, zero if a == b, positive if a > b.
 *
 * We use (ia > ib) - (ia < ib) instead of (ia - ib) to avoid integer
 * overflow issues that can happen with subtraction.
 */
static int compareInts(const void *a, const void *b) {
    jint ia = *(const jint *)a;
    jint ib = *(const jint *)b;
    return (ia > ib) - (ia < ib);  /* Overflow-safe comparison */
}

/*
 * Java_com_stevenpg_jni_NativeArrayOps_sortArray
 *
 * Sorts a Java int[] array IN-PLACE using C's qsort().
 *
 * This is a WRITE operation - we modify the array and need those changes
 * reflected back in Java. So we release with mode 0 (copy back + free).
 */
JNIEXPORT void JNICALL Java_com_stevenpg_jni_NativeArrayOps_sortArray
  (JNIEnv *env, jobject obj, jintArray values) {

    jint *elements = (*env)->GetIntArrayElements(env, values, NULL);
    if (elements == NULL) return;

    jsize len = (*env)->GetArrayLength(env, values);

    /*
     * Call C standard library's qsort().
     *
     * This is a great example of why you'd use JNI: leveraging decades
     * of optimized C library code. qsort is typically implemented as
     * an introsort (quicksort + heapsort hybrid) and is highly optimized.
     *
     * Parameters: array pointer, number of elements, element size, comparator
     */
    qsort(elements, len, sizeof(jint), compareInts);

    /*
     * Release with mode 0: copy changes back AND free the buffer.
     *
     * This is crucial - if we used JNI_ABORT here, the sort would be
     * discarded and the Java array would remain unsorted. Mode 0 ensures
     * our sorted data is written back to the Java heap.
     */
    (*env)->ReleaseIntArrayElements(env, values, elements, 0);
}

/*
 * Java_com_stevenpg_jni_NativeArrayOps_scaleArray
 *
 * Creates a NEW Java array with each element multiplied by a scalar.
 * Demonstrates: NewIntArray + SetIntArrayRegion (creating arrays in C).
 */
JNIEXPORT jintArray JNICALL Java_com_stevenpg_jni_NativeArrayOps_scaleArray
  (JNIEnv *env, jobject obj, jintArray values, jint scalar) {

    /* Get elements from the input array (read-only access) */
    jint *elements = (*env)->GetIntArrayElements(env, values, NULL);
    if (elements == NULL) return NULL;

    jsize len = (*env)->GetArrayLength(env, values);

    /*
     * NewIntArray creates a fresh Java int[] on the Java heap.
     *
     * This is analogous to "new int[len]" in Java. The array is
     * zero-initialized. It returns a local reference (jintArray) that
     * the GC will manage.
     *
     * Can return NULL if the JVM can't allocate (OOM).
     */
    jintArray result = (*env)->NewIntArray(env, len);
    if (result == NULL) {
        (*env)->ReleaseIntArrayElements(env, values, elements, JNI_ABORT);
        return NULL;  /* OutOfMemoryError pending */
    }

    /* Compute scaled values in a C buffer.
     * We can't write directly to the new Java array's memory (we'd need
     * GetIntArrayElements on it too). Instead, we use a temp buffer and
     * copy it in bulk with SetIntArrayRegion. */
    jint *scaled = (jint *)malloc(len * sizeof(jint));
    if (scaled == NULL) {
        (*env)->ReleaseIntArrayElements(env, values, elements, JNI_ABORT);
        return NULL;
    }

    for (jsize i = 0; i < len; i++) {
        scaled[i] = elements[i] * scalar;
    }

    /*
     * SetIntArrayRegion copies data FROM a C buffer INTO a Java array.
     *
     * Parameters: env, target Java array, start index, count, source buffer
     *
     * This is the counterpart to GetIntArrayRegion (which copies FROM
     * a Java array INTO a C buffer). No release needed for this call.
     *
     * If the range is out of bounds, an ArrayIndexOutOfBoundsException
     * is thrown (but we know our indices are correct here).
     */
    (*env)->SetIntArrayRegion(env, result, 0, len, scaled);

    free(scaled);
    (*env)->ReleaseIntArrayElements(env, values, elements, JNI_ABORT);
    return result;
}
