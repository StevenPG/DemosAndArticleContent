#include <jni.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>

/*
 * JNI System Info Operations
 *
 * Demonstrates calling POSIX/system-level C functions from Java via JNI.
 * This is one of the primary use cases for native interop - accessing
 * OS capabilities not exposed by the Java standard library.
 */

JNIEXPORT jstring JNICALL Java_com_stevenpg_jni_NativeSystemInfo_getHostname
  (JNIEnv *env, jobject obj) {
    char hostname[256];
    if (gethostname(hostname, sizeof(hostname)) != 0) {
        // Throw an exception back to Java if the system call fails
        (*env)->ThrowNew(env,
            (*env)->FindClass(env, "java/io/IOException"),
            "Failed to get hostname");
        return NULL;
    }
    return (*env)->NewStringUTF(env, hostname);
}

JNIEXPORT jlong JNICALL Java_com_stevenpg_jni_NativeSystemInfo_getProcessId
  (JNIEnv *env, jobject obj) {
    return (jlong)getpid();
}

JNIEXPORT jstring JNICALL Java_com_stevenpg_jni_NativeSystemInfo_allocateAndReadNativeMemory
  (JNIEnv *env, jobject obj, jstring message) {
    const char *msg = (*env)->GetStringUTFChars(env, message, NULL);
    if (msg == NULL) return NULL;

    size_t len = strlen(msg);

    // Allocate native (off-heap) memory
    char *nativeBuffer = (char *)malloc(len + 1);
    if (nativeBuffer == NULL) {
        (*env)->ReleaseStringUTFChars(env, message, msg);
        (*env)->ThrowNew(env,
            (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
            "Native malloc failed");
        return NULL;
    }

    // Copy into native memory, then read back
    // In a real scenario, this memory might be shared with another native
    // library, passed to a hardware driver, or used for memory-mapped I/O.
    memcpy(nativeBuffer, msg, len + 1);

    (*env)->ReleaseStringUTFChars(env, message, msg);

    // Read back from native memory and return as Java String
    jstring result = (*env)->NewStringUTF(env, nativeBuffer);

    // IMPORTANT: Always free native memory to prevent leaks.
    // The JVM garbage collector does NOT manage native memory.
    free(nativeBuffer);

    return result;
}
