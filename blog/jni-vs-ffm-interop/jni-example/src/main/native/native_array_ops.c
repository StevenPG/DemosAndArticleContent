#include <jni.h>
#include <stdlib.h>

/*
 * JNI Array Operations
 *
 * Key JNI concepts demonstrated here:
 * - GetIntArrayElements / ReleaseIntArrayElements: Pin a Java array in memory
 *   so native code can read/write it directly. The JNI_ABORT and 0 flags on
 *   release control whether changes are copied back to the Java array.
 * - GetArrayLength: Get the length of a Java array.
 * - NewIntArray / SetIntArrayRegion: Create new Java arrays from native code.
 *
 * IMPORTANT: Array pinning can block the garbage collector. For large arrays
 * or performance-critical code, consider GetPrimitiveArrayCritical instead,
 * but be aware of its stricter usage constraints.
 */

JNIEXPORT jlong JNICALL Java_com_stevenpg_jni_NativeArrayOps_sumArray
  (JNIEnv *env, jobject obj, jintArray values) {
    jint *elements = (*env)->GetIntArrayElements(env, values, NULL);
    if (elements == NULL) return 0;

    jsize len = (*env)->GetArrayLength(env, values);
    jlong sum = 0;
    for (jsize i = 0; i < len; i++) {
        sum += elements[i];
    }

    // JNI_ABORT: release without copying changes back (read-only use)
    (*env)->ReleaseIntArrayElements(env, values, elements, JNI_ABORT);
    return sum;
}

// Comparator for qsort
static int compareInts(const void *a, const void *b) {
    jint ia = *(const jint *)a;
    jint ib = *(const jint *)b;
    return (ia > ib) - (ia < ib);
}

JNIEXPORT void JNICALL Java_com_stevenpg_jni_NativeArrayOps_sortArray
  (JNIEnv *env, jobject obj, jintArray values) {
    jint *elements = (*env)->GetIntArrayElements(env, values, NULL);
    if (elements == NULL) return;

    jsize len = (*env)->GetArrayLength(env, values);

    // Use C standard library qsort
    qsort(elements, len, sizeof(jint), compareInts);

    // 0: copy changes back to Java array and free the buffer
    (*env)->ReleaseIntArrayElements(env, values, elements, 0);
}

JNIEXPORT jintArray JNICALL Java_com_stevenpg_jni_NativeArrayOps_scaleArray
  (JNIEnv *env, jobject obj, jintArray values, jint scalar) {
    jint *elements = (*env)->GetIntArrayElements(env, values, NULL);
    if (elements == NULL) return NULL;

    jsize len = (*env)->GetArrayLength(env, values);

    // Create a new Java array for the result
    jintArray result = (*env)->NewIntArray(env, len);
    if (result == NULL) {
        (*env)->ReleaseIntArrayElements(env, values, elements, JNI_ABORT);
        return NULL; // OutOfMemoryError thrown by JVM
    }

    // Allocate temp buffer, scale, then set into the new array
    jint *scaled = (jint *)malloc(len * sizeof(jint));
    if (scaled == NULL) {
        (*env)->ReleaseIntArrayElements(env, values, elements, JNI_ABORT);
        return NULL;
    }

    for (jsize i = 0; i < len; i++) {
        scaled[i] = elements[i] * scalar;
    }

    // Copy scaled values into the new Java array
    (*env)->SetIntArrayRegion(env, result, 0, len, scaled);

    free(scaled);
    (*env)->ReleaseIntArrayElements(env, values, elements, JNI_ABORT);
    return result;
}
