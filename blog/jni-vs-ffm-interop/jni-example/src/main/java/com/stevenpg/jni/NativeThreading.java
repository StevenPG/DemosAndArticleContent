package com.stevenpg.jni;

/**
 * =====================================================================
 * NativeThreading - JNI Thread Safety and Multi-Threading
 * =====================================================================
 *
 * Threading is one of the MOST DANGEROUS aspects of JNI. The core rule:
 *
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │  JNIEnv* is THREAD-LOCAL. Each thread MUST have its own.       │
 *   │  Sharing a JNIEnv* across threads WILL crash the JVM.          │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 * When Java calls a native method, the JVM automatically provides the
 * correct JNIEnv* for the current thread. But if your C code creates
 * its own threads (via pthread_create, for example), those threads
 * DON'T have a JNIEnv*. To call Java from such threads, you must:
 *
 *   1. Call JavaVM->AttachCurrentThread() to get a JNIEnv*
 *   2. Use that JNIEnv* for all JNI calls on this thread
 *   3. Call JavaVM->DetachCurrentThread() when done
 *
 * THE JavaVM vs JNIEnv:
 *   - JavaVM* : Process-wide, shared across all threads. You get it
 *               from JNI_OnLoad() or via GetJavaVM().
 *   - JNIEnv* : Thread-local, one per thread. You get it from the JVM
 *               automatically (for Java-created threads) or from
 *               AttachCurrentThread (for native-created threads).
 *
 * COMMON THREADING BUGS:
 *   1. Sharing JNIEnv* across threads → SEGFAULT
 *   2. Forgetting to Detach a thread → memory leak, JVM hangs on exit
 *   3. Using local references from another thread → undefined behavior
 *      (Use NewGlobalRef() if you need to share object references)
 *   4. Calling JNI functions after DetachCurrentThread → SEGFAULT
 *
 * JNI_OnLoad:
 *   This is a special C function that the JVM calls when your library
 *   is first loaded (System.loadLibrary). It's the perfect place to:
 *     - Cache the JavaVM* pointer for later use by native threads
 *     - Pre-cache class/method IDs to avoid repeated lookups
 *     - Validate the JNI version
 *
 * SEE ALSO: The corresponding C implementation is in:
 *   src/main/native/native_threading.c
 */
public class NativeThreading {

    static {
        System.loadLibrary("jniexamples");
    }

    /**
     * Demonstrates work done on native threads that call back into Java.
     *
     * The C implementation will:
     *   1. Get the JavaVM* pointer (cached from JNI_OnLoad)
     *   2. Create N native threads using pthread_create
     *   3. Each thread calls AttachCurrentThread to get its own JNIEnv*
     *   4. Each thread calls this object's onThreadResult() method
     *   5. Each thread calls DetachCurrentThread when done
     *   6. The main thread waits (pthread_join) for all threads to complete
     *
     * @param numThreads how many native threads to spawn
     */
    public native void runNativeThreads(int numThreads);

    /**
     * Called from NATIVE threads (not the main Java thread).
     *
     * The native threads must:
     *   1. AttachCurrentThread() to get their own JNIEnv*
     *   2. Use that env to find this class and method
     *   3. Call this method via CallVoidMethod
     *   4. DetachCurrentThread() when finished
     *
     * Since this is called from multiple threads, it must be thread-safe.
     * printf from C is thread-safe, but if this method modified shared
     * Java state, you'd need synchronization.
     *
     * @param threadId which native thread is calling
     * @param result a computed result from the native thread
     */
    public void onThreadResult(int threadId, long result) {
        // Thread.currentThread() shows this runs on a native-attached thread
        System.out.printf("  [Thread %d → Java] result=%d (Java thread: %s)%n",
                threadId, result, Thread.currentThread().getName());
    }
}
