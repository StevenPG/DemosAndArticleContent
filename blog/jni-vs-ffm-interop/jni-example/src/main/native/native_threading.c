/*
 * =====================================================================
 * native_threading.c - JNI Multi-Threading (C Implementation)
 * =====================================================================
 *
 * This file demonstrates one of the most advanced and dangerous JNI
 * patterns: using native threads that call back into Java.
 *
 * THE CORE PROBLEM:
 *   JNIEnv* is thread-local. When Java calls a native method, the JVM
 *   provides the correct JNIEnv* for that thread. But if C code creates
 *   its own threads (via pthread_create), those threads have NO JNIEnv*.
 *   Calling JNI functions without a JNIEnv* will crash the JVM.
 *
 * THE SOLUTION:
 *   1. Cache the JavaVM* pointer (process-global) in JNI_OnLoad
 *   2. On each native thread: call AttachCurrentThread to get a JNIEnv*
 *   3. Use that JNIEnv* for all JNI calls on that thread
 *   4. Call DetachCurrentThread when the thread is done
 *
 * JavaVM* vs JNIEnv*:
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │  JavaVM*  : One per process. Shared by all threads.            │
 *   │             Used for: AttachCurrentThread, DetachCurrentThread  │
 *   │             Get it from: JNI_OnLoad() or GetJavaVM()           │
 *   │                                                                │
 *   │  JNIEnv*  : One per thread. Thread-local, NOT sharable.        │
 *   │             Used for: Everything else (FindClass, CallMethod..) │
 *   │             Get it from: Native method parameter (Java threads) │
 *   │                          AttachCurrentThread (native threads)   │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 * WHAT HAPPENS IF YOU GET THIS WRONG:
 *   - Using JNIEnv* from wrong thread → SEGFAULT or data corruption
 *   - Forgetting AttachCurrentThread → SEGFAULT
 *   - Forgetting DetachCurrentThread → JVM can't shut down cleanly,
 *     memory leak, potential deadlock during JVM shutdown
 *   - Using a local reference from another thread → undefined behavior
 *     (use NewGlobalRef to share references across threads)
 */

#include <jni.h>
#include <pthread.h>   /* POSIX threads: pthread_create, pthread_join */
#include <stdio.h>     /* printf for debugging */

/*
 * Global JavaVM pointer - cached in JNI_OnLoad.
 * This is safe to share across threads (unlike JNIEnv*).
 * static = file-scope only (not exported from the shared library).
 */
static JavaVM *cachedJvm = NULL;

/*
 * Global reference to the NativeThreading class.
 * We cache this because FindClass doesn't work reliably on attached
 * threads (it searches the calling class's classloader, which doesn't
 * exist for native-created threads). See the note in thread_func.
 */
static jclass cachedClass = NULL;

/*
 * JNI_OnLoad - Called by the JVM when System.loadLibrary() loads this .so
 *
 * This is a SPECIAL function recognized by the JVM. It's called once
 * when the library is loaded and is the ideal place to:
 *   1. Cache the JavaVM* pointer for use by native threads
 *   2. Pre-lookup and cache class/method IDs (performance optimization)
 *   3. Return the required JNI version
 *
 * NOTE: This function is shared across ALL classes in this library,
 * not just NativeThreading. That's fine - we just cache what we need.
 *
 * RETURN VALUE:
 *   Must return the JNI version this library expects. If the JVM doesn't
 *   support this version, loading fails with UnsatisfiedLinkError.
 *   JNI_VERSION_1_6 is safe for Java 6+. Use JNI_VERSION_21 for newer features.
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    /* Cache the JavaVM pointer for later use by native threads.
     * This pointer is valid for the lifetime of the JVM process. */
    cachedJvm = vm;

    /* Get a JNIEnv* for this thread (the thread loading the library) */
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;  /* Loading will fail with UnsatisfiedLinkError */
    }

    /*
     * Cache the NativeThreading class as a GLOBAL reference.
     *
     * WHY GLOBAL? FindClass returns a local reference, which is only valid
     * during the current JNI call. We need this reference to be valid when
     * native threads attach later. NewGlobalRef promotes it to a global
     * reference that stays valid until explicitly deleted.
     *
     * WHY CACHE IT? FindClass on attached native threads uses the system
     * classloader, which may not find application classes. Caching it here
     * (where the correct classloader is in scope) avoids this problem.
     */
    jclass localClass = (*env)->FindClass(env, "com/stevenpg/jni/NativeThreading");
    if (localClass == NULL) {
        return JNI_ERR;
    }
    cachedClass = (*env)->NewGlobalRef(env, localClass);
    (*env)->DeleteLocalRef(env, localClass);

    /* Return the JNI version we require */
    return JNI_VERSION_1_6;
}

/*
 * Data passed to each pthread. Each native thread needs to know:
 *   - Which Java object to call back on (global reference!)
 *   - Its thread ID for identification
 */
typedef struct {
    jobject javaObj;      /* GLOBAL reference - safe to use from any thread */
    int threadId;
} ThreadData;

/*
 * Thread function - runs on a native (non-Java) thread.
 *
 * CRITICAL: This function runs on a thread created by pthread_create,
 * NOT by Java. This thread has NO JNIEnv* until we AttachCurrentThread.
 */
static void *thread_func(void *arg) {
    ThreadData *data = (ThreadData *)arg;
    JNIEnv *env;

    /*
     * Step 1: ATTACH this native thread to the JVM.
     *
     * AttachCurrentThread:
     *   - Creates a JNIEnv* for this thread
     *   - Registers the thread with the JVM's thread management
     *   - The thread appears as a Java thread (visible in Thread.getAllStackTraces())
     *   - The thread can now make JNI calls
     *
     * The second parameter is a pointer to JNIEnv* (output).
     * The third parameter allows setting thread name/group (NULL = defaults).
     *
     * IMPORTANT: After this call, the GC knows about this thread and may
     * need to stop it during GC pauses (safepoints).
     */
    if ((*cachedJvm)->AttachCurrentThread(cachedJvm, (void **)&env, NULL) != JNI_OK) {
        fprintf(stderr, "Thread %d: Failed to attach to JVM\n", data->threadId);
        free(data);
        return NULL;
    }

    /* Step 2: Do some work on this native thread */
    long result = 0;
    for (long i = 1; i <= 100000L * (data->threadId + 1); i++) {
        result += i;
    }

    /*
     * Step 3: Call back into Java from this native thread.
     *
     * We use the CACHED class (global reference) because FindClass
     * behaves differently on attached threads - it uses the system
     * classloader instead of the application classloader.
     */
    jmethodID method = (*env)->GetMethodID(env, cachedClass,
        "onThreadResult", "(IJ)V");

    if (method != NULL) {
        /* Call Java's onThreadResult(threadId, result) from this native thread */
        (*env)->CallVoidMethod(env, data->javaObj, method,
            data->threadId, (jlong)result);
    }

    /*
     * Step 4: DETACH this thread from the JVM.
     *
     * CRITICAL: You MUST detach before the thread exits. If you don't:
     *   - The JVM thinks the thread still exists
     *   - JVM shutdown will hang waiting for this thread
     *   - The thread's JNIEnv resources are leaked
     *
     * After detaching, you MUST NOT use 'env' anymore.
     */
    (*cachedJvm)->DetachCurrentThread(cachedJvm);

    /* Clean up: delete the global reference to the Java object.
     * Wait, we can't! We just detached and no longer have a JNIEnv*.
     * The global reference cleanup happens in the calling function. */
    free(data);
    return NULL;
}

/*
 * Java_com_stevenpg_jni_NativeThreading_runNativeThreads
 *
 * Creates N native threads, each of which attaches to the JVM and
 * calls back into Java's onThreadResult() method.
 */
JNIEXPORT void JNICALL Java_com_stevenpg_jni_NativeThreading_runNativeThreads
  (JNIEnv *env, jobject obj, jint numThreads) {

    pthread_t *threads = (pthread_t *)malloc(numThreads * sizeof(pthread_t));
    if (threads == NULL) return;

    /*
     * Create a GLOBAL reference to the Java object.
     *
     * 'obj' is a LOCAL reference - valid only on THIS thread during
     * THIS JNI call. To use it from other threads, we need a GLOBAL
     * reference. Global references:
     *   - Are valid from any thread
     *   - Survive beyond the current JNI call
     *   - MUST be explicitly deleted with DeleteGlobalRef
     *   - Prevent the object from being garbage collected
     */
    jobject globalObj = (*env)->NewGlobalRef(env, obj);

    /* Create N native threads */
    for (jint i = 0; i < numThreads; i++) {
        ThreadData *data = (ThreadData *)malloc(sizeof(ThreadData));
        data->javaObj = globalObj;
        data->threadId = i;

        /*
         * pthread_create spawns a new OS thread that runs thread_func.
         * This thread is NOT known to the JVM until it calls AttachCurrentThread.
         */
        if (pthread_create(&threads[i], NULL, thread_func, data) != 0) {
            fprintf(stderr, "Failed to create thread %d\n", i);
            free(data);
        }
    }

    /* Wait for all threads to complete */
    for (jint i = 0; i < numThreads; i++) {
        pthread_join(threads[i], NULL);
    }

    /* Clean up the global reference now that all threads are done.
     * This allows the Java object to be garbage collected. */
    (*env)->DeleteGlobalRef(env, globalObj);

    free(threads);
}
