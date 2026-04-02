/*
 * =====================================================================
 * native_string_utils.c - JNI String Operations (C Implementation)
 * =====================================================================
 *
 * This file contains the C implementations of the native methods declared
 * in NativeStringUtils.java. Each function MUST follow the JNI naming
 * convention exactly, or the JVM won't find it at runtime.
 *
 * JNI NAMING CONVENTION:
 *   Java_<package_underscored>_<ClassName>_<methodName>
 *
 *   For com.stevenpg.jni.NativeStringUtils.toUpperCase():
 *     → Java_com_stevenpg_jni_NativeStringUtils_toUpperCase
 *
 *   If your package or method name contains underscores, they're encoded
 *   as "_1" in the C function name. This gets ugly fast, which is one
 *   reason FFM is preferred for new code.
 *
 * JNI FUNCTION PARAMETERS:
 *   Every JNI function receives at minimum:
 *     - JNIEnv *env  : Pointer to the JNI function table. This is how
 *                       you access ALL JNI functionality (string ops,
 *                       array ops, exception handling, etc.)
 *     - jobject obj   : The "this" reference (the Java object that owns
 *                       the native method). For static native methods,
 *                       this would be jclass instead.
 *
 * JNIEXPORT and JNICALL:
 *   - JNIEXPORT: Ensures the function is exported from the shared library
 *                (equivalent to __attribute__((visibility("default"))) on GCC)
 *   - JNICALL:   Specifies the calling convention (usually empty on Linux,
 *                __stdcall on Windows)
 *
 * MEMORY MANAGEMENT RULES:
 *   1. GetStringUTFChars → MUST call ReleaseStringUTFChars (even on error paths!)
 *   2. malloc → MUST call free
 *   3. NewStringUTF → creates a Java object on the heap, managed by GC (don't free it)
 *   4. The returned jstring is a "local reference" - valid only during this
 *      JNI call. If you need to store it longer, use NewGlobalRef().
 */

#include <jni.h>       /* JNI types: JNIEnv, jstring, jint, JNIEXPORT, etc. */
#include <string.h>    /* strlen, memcpy */
#include <stdlib.h>    /* malloc, free */
#include <ctype.h>     /* toupper */

/*
 * Java_com_stevenpg_jni_NativeStringUtils_toUpperCase
 *
 * Converts a Java String to uppercase.
 *
 * DETAILED WALKTHROUGH:
 *
 * Step 1: GetStringUTFChars
 *   Converts the Java String (internally UTF-16) to a C string (Modified UTF-8).
 *   The third parameter (isCopy) can be set to a jboolean* to know if the JVM
 *   made a copy or gave you a direct pointer. We pass NULL because we don't care.
 *
 *   IMPORTANT: The returned pointer is only valid until ReleaseStringUTFChars.
 *   The JVM may be pointing you at internal data that could move during GC.
 *
 * Step 2: Process the data
 *   We malloc our own buffer and do the conversion. We can't modify the
 *   string from GetStringUTFChars - it may point to JVM internal data.
 *
 * Step 3: NewStringUTF
 *   Creates a new Java String object from our C string. This allocates on
 *   the Java heap - the GC will manage it. Don't free the returned jstring.
 *
 * Step 4: Cleanup
 *   Release the input string AND free our malloc'd buffer.
 */
JNIEXPORT jstring JNICALL Java_com_stevenpg_jni_NativeStringUtils_toUpperCase
  (JNIEnv *env, jobject obj, jstring input) {

    /* Step 1: Get the C string from the Java String.
     *
     * CRITICAL: Check for NULL! GetStringUTFChars returns NULL if:
     *   - The JVM can't allocate memory for the copy
     *   - An OutOfMemoryError has been thrown
     *
     * When it returns NULL, a pending exception is set. We must return
     * immediately - trying to use a NULL pointer would SEGFAULT.
     */
    const char *str = (*env)->GetStringUTFChars(env, input, NULL);
    if (str == NULL) {
        return NULL;  /* OutOfMemoryError already pending in the JVM */
    }

    /* Step 2: Allocate a C buffer and do the uppercase conversion.
     *
     * We use malloc here because we need a mutable copy. The string from
     * GetStringUTFChars is read-only (const char*).
     *
     * IMPORTANT: If malloc fails, we MUST still release the input string
     * before returning. Forgetting this is one of the most common JNI bugs.
     */
    size_t len = strlen(str);
    char *result = (char *)malloc(len + 1);  /* +1 for null terminator */
    if (result == NULL) {
        /* Clean up the input string BEFORE throwing the exception */
        (*env)->ReleaseStringUTFChars(env, input, str);

        /* Throw a Java exception from C.
         *
         * ThrowNew creates and throws a Java exception. The class name uses
         * JNI's slash-separated format (not dots): "java/lang/OutOfMemoryError"
         *
         * CRITICAL: ThrowNew does NOT stop execution like Java's "throw".
         * The C function keeps running! You MUST return after ThrowNew.
         * The exception is delivered to Java only after this C function returns.
         */
        (*env)->ThrowNew(env,
            (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
            "Failed to allocate memory in native code");
        return NULL;
    }

    /* Do the actual work - convert each character to uppercase */
    for (size_t i = 0; i < len; i++) {
        /* Cast to unsigned char to avoid undefined behavior with toupper()
         * when the char value is negative (extended ASCII characters) */
        result[i] = toupper((unsigned char)str[i]);
    }
    result[len] = '\0';  /* Null-terminate the C string */

    /* Step 3: Release the input string.
     *
     * MUST be called before we return. If the JVM gave us a direct pointer
     * (not a copy), this tells the GC it can move the string again.
     * If it gave us a copy, this frees the copy.
     */
    (*env)->ReleaseStringUTFChars(env, input, str);

    /* Step 4: Create a new Java String from our C string.
     *
     * NewStringUTF allocates a new java.lang.String on the Java heap.
     * The JVM copies our C data into its own internal representation.
     * The returned jstring is a "local reference" that's valid until
     * this native method returns (the JVM cleans it up automatically).
     */
    jstring jResult = (*env)->NewStringUTF(env, result);

    /* Free our malloc'd buffer - the JVM has already copied the data
     * into its own String object. If we don't free this, it's a leak. */
    free(result);

    return jResult;
}

/*
 * Java_com_stevenpg_jni_NativeStringUtils_reverse
 *
 * Reverses a Java String using native code.
 * Same Get/Process/Release pattern as toUpperCase.
 */
JNIEXPORT jstring JNICALL Java_com_stevenpg_jni_NativeStringUtils_reverse
  (JNIEnv *env, jobject obj, jstring input) {
    const char *str = (*env)->GetStringUTFChars(env, input, NULL);
    if (str == NULL) return NULL;

    size_t len = strlen(str);
    char *result = (char *)malloc(len + 1);
    if (result == NULL) {
        (*env)->ReleaseStringUTFChars(env, input, str);
        return NULL;  /* Let the JVM's pending OOM propagate */
    }

    /* Reverse the string character by character.
     * NOTE: This works for ASCII but is INCORRECT for multi-byte UTF-8!
     * A production implementation would need to handle multi-byte sequences.
     * This is yet another way JNI string handling is error-prone. */
    for (size_t i = 0; i < len; i++) {
        result[i] = str[len - 1 - i];
    }
    result[len] = '\0';

    /* Always release BEFORE creating the result string. This ordering
     * isn't strictly required, but it's good practice to release resources
     * as early as possible. */
    (*env)->ReleaseStringUTFChars(env, input, str);

    jstring jResult = (*env)->NewStringUTF(env, result);
    free(result);
    return jResult;
}

/*
 * Java_com_stevenpg_jni_NativeStringUtils_countChar
 *
 * Counts occurrences of a character in a string.
 *
 * NOTE: jchar is an unsigned 16-bit value (like Java's char, which is UTF-16).
 * We're comparing it to individual bytes from GetStringUTFChars, which gives
 * us Modified UTF-8. This works correctly for ASCII characters but would need
 * more sophisticated handling for characters outside the ASCII range.
 */
JNIEXPORT jint JNICALL Java_com_stevenpg_jni_NativeStringUtils_countChar
  (JNIEnv *env, jobject obj, jstring input, jchar target) {
    const char *str = (*env)->GetStringUTFChars(env, input, NULL);
    if (str == NULL) return 0;

    int count = 0;
    size_t len = strlen(str);
    for (size_t i = 0; i < len; i++) {
        if (str[i] == (char)target) {
            count++;
        }
    }

    (*env)->ReleaseStringUTFChars(env, input, str);

    /* Primitive return types (jint, jlong, jdouble, etc.) are returned
     * directly - no NewXxx call needed. Only objects need JNI creation. */
    return count;
}
