/*
 * =====================================================================
 * native_system_info.c - JNI System-Level Operations (C Implementation)
 * =====================================================================
 *
 * This file demonstrates calling POSIX system functions from JNI.
 * These are functions that exist in the C standard library or POSIX
 * specification, available on any Unix-like system.
 *
 * KEY CONCEPT - Error Handling Across the Boundary:
 *   C functions typically indicate errors via return values (-1, NULL, etc.)
 *   and set errno for details. Java uses exceptions. JNI bridges these
 *   two worlds:
 *
 *   C-style error:          JNI bridge:               Java-style error:
 *   ─────────────           ──────────                ─────────────────
 *   int result = func();    if (result < 0) {         catch (IOException e) {
 *   if (result < 0) {         ThrowNew(env,             e.getMessage()
 *     perror("func");           "java/io/IOException",  }
 *   }                          strerror(errno));
 *                            return;
 *                           }
 *
 * ABOUT NATIVE MEMORY:
 *   When JNI code calls malloc(), that memory is allocated outside the
 *   JVM heap. The garbage collector:
 *     ✗ CANNOT see it
 *     ✗ CANNOT free it
 *     ✗ CANNOT move it
 *     ✗ Does NOT know how much native memory is in use
 *
 *   This means:
 *     - Your Java app could appear to use 200MB (Java heap) but actually
 *       use 2GB if native code allocates a lot
 *     - Tools like jmap and VisualVM won't show native memory by default
 *     - Memory leaks in native code won't trigger Java OutOfMemoryError
 *       until the OS runs out of memory (then you get a process kill)
 *
 *   FFM's Arena-based approach solves this by tracking native allocations
 *   and freeing them deterministically when the Arena closes.
 */

#include <jni.h>
#include <unistd.h>    /* gethostname, getpid */
#include <string.h>    /* strlen, memcpy */
#include <stdlib.h>    /* malloc, free */

/*
 * Java_com_stevenpg_jni_NativeSystemInfo_getHostname
 *
 * Calls POSIX gethostname() and returns the result as a Java String.
 *
 * gethostname() is a system call that reads the kernel's hostname
 * into a user-space buffer. It returns 0 on success, -1 on error.
 */
JNIEXPORT jstring JNICALL Java_com_stevenpg_jni_NativeSystemInfo_getHostname
  (JNIEnv *env, jobject obj) {

    /* Stack-allocated buffer - automatically freed when this function returns.
     * 256 bytes is more than enough; POSIX guarantees hostnames ≤ 255 chars. */
    char hostname[256];

    if (gethostname(hostname, sizeof(hostname)) != 0) {
        /*
         * System call failed. Throw an IOException to Java.
         *
         * FindClass uses JNI's slash-notation for class names:
         *   "java/io/IOException" instead of "java.io.IOException"
         *
         * ThrowNew creates a new instance of the exception class,
         * calls its String constructor, and sets it as the pending exception.
         *
         * REMEMBER: ThrowNew does NOT stop this C function! We must return
         * explicitly. If we kept going and tried to use 'hostname', we'd
         * be reading garbage data.
         */
        (*env)->ThrowNew(env,
            (*env)->FindClass(env, "java/io/IOException"),
            "Failed to get hostname via gethostname()");
        return NULL;
    }

    /* Convert the C string to a Java String.
     * hostname is stack-allocated, so it's automatically cleaned up
     * when this function returns. NewStringUTF copies the data. */
    return (*env)->NewStringUTF(env, hostname);
}

/*
 * Java_com_stevenpg_jni_NativeSystemInfo_getProcessId
 *
 * Returns the current process ID via POSIX getpid().
 *
 * This is the simplest possible JNI function - no objects, no memory
 * management, no error handling. Just call a C function and return.
 *
 * getpid() is guaranteed to succeed (it can never fail), so no error
 * checking is needed.
 */
JNIEXPORT jlong JNICALL Java_com_stevenpg_jni_NativeSystemInfo_getProcessId
  (JNIEnv *env, jobject obj) {
    /* Cast pid_t (typically int) to jlong (Java's long = int64_t) */
    return (jlong)getpid();
}

/*
 * Java_com_stevenpg_jni_NativeSystemInfo_allocateAndReadNativeMemory
 *
 * Demonstrates the complete lifecycle of native (off-heap) memory:
 *   1. Extract string data from Java
 *   2. Allocate native memory with malloc
 *   3. Write data to native memory
 *   4. Read data back from native memory
 *   5. Create a Java String from the native data
 *   6. Free all resources
 *
 * This pattern appears in real-world JNI code when:
 *   - Interfacing with C libraries that require their own memory buffers
 *   - Implementing custom serialization that works across languages
 *   - Managing GPU memory or memory-mapped hardware
 *   - Working with memory-mapped files for IPC
 *
 * RESOURCE CLEANUP ORDER MATTERS:
 *   We must track every resource and free them all, even on error paths.
 *   Missing a single free/release call = memory leak. This is error-prone
 *   in complex functions with multiple error paths, which is why FFM's
 *   Arena (try-with-resources) pattern is so much better.
 */
JNIEXPORT jstring JNICALL Java_com_stevenpg_jni_NativeSystemInfo_allocateAndReadNativeMemory
  (JNIEnv *env, jobject obj, jstring message) {

    /* Step 1: Extract the Java String as a C string.
     * RESOURCE ACQUIRED: Must release with ReleaseStringUTFChars */
    const char *msg = (*env)->GetStringUTFChars(env, message, NULL);
    if (msg == NULL) return NULL;

    size_t len = strlen(msg);

    /* Step 2: Allocate native (off-heap) memory with malloc.
     * RESOURCE ACQUIRED: Must free with free()
     *
     * This memory lives outside the JVM heap entirely. It's managed
     * by the C runtime's allocator, not the JVM garbage collector.
     * The GC has NO knowledge of this allocation. */
    char *nativeBuffer = (char *)malloc(len + 1);
    if (nativeBuffer == NULL) {
        /* ERROR PATH: Must release the string before returning */
        (*env)->ReleaseStringUTFChars(env, message, msg);
        (*env)->ThrowNew(env,
            (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
            "Native malloc failed");
        return NULL;
    }

    /* Step 3: Write to native memory.
     * memcpy copies len+1 bytes (including the null terminator).
     *
     * In a real scenario, this buffer might be:
     *   - Passed to a hardware driver (DMA transfer)
     *   - Shared with another process via shared memory
     *   - Written to a memory-mapped file
     *   - Sent to a native library like OpenSSL, SQLite, etc. */
    memcpy(nativeBuffer, msg, len + 1);

    /* Step 4: Release the Java string (we've copied the data out).
     * RESOURCE RELEASED: GetStringUTFChars / ReleaseStringUTFChars */
    (*env)->ReleaseStringUTFChars(env, message, msg);

    /* Step 5: Read back from native memory and create a Java String.
     * In real code, the native memory may have been modified by hardware
     * or another process between the write and read. */
    jstring result = (*env)->NewStringUTF(env, nativeBuffer);

    /* Step 6: Free the native memory.
     * RESOURCE RELEASED: malloc / free
     *
     * CRITICAL: If we forget this free(), the memory is leaked forever.
     * Unlike Java objects, there's no finalizer or GC to clean it up.
     * Tools like Valgrind or AddressSanitizer can help detect this. */
    free(nativeBuffer);

    return result;
}
