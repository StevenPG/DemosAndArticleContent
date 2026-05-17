/*
 * =====================================================================
 * native_callback.c - JNI Upcalls / Callbacks (C Implementation)
 * =====================================================================
 *
 * "Upcall" = native code calling back into Java (the reverse direction).
 *
 * This is one of the most complex JNI patterns because it requires:
 *   1. Looking up Java classes from C
 *   2. Looking up Java methods from C (using type signature strings)
 *   3. Invoking Java methods from C (choosing the right CallXxxMethod)
 *
 * THE CALLBACK MECHANISM:
 *
 *   To call a Java method from C, you need three things:
 *
 *   a) The Java CLASS (jclass):
 *      Found via FindClass("com/stevenpg/jni/NativeCallback")
 *      Or via GetObjectClass(obj) if you have an instance
 *
 *   b) The METHOD ID (jmethodID):
 *      Found via GetMethodID(class, "methodName", "(params)return")
 *      The signature string uses JNI type codes (see NativeCallback.java)
 *
 *   c) The OBJECT to call it on (jobject):
 *      This is the 'obj' parameter passed to every instance JNI method
 *
 *   Then invoke: CallVoidMethod(env, obj, methodID, arg1, arg2, ...)
 *                CallIntMethod(env, obj, methodID, arg1, arg2, ...)
 *                CallObjectMethod(env, obj, methodID, arg1, arg2, ...)
 *
 * PERFORMANCE TIP:
 *   FindClass and GetMethodID are relatively expensive (they involve
 *   string lookups). If you're calling the same Java method repeatedly
 *   (like in a loop), cache the jclass and jmethodID. They remain valid
 *   for the lifetime of the class (use NewGlobalRef for the jclass if
 *   you need it across JNI calls).
 *
 * EXCEPTION HANDLING:
 *   If the Java callback method throws an exception, it becomes a
 *   "pending exception" in the JNI environment. You should check for
 *   it using ExceptionCheck() after the call. If you ignore it and
 *   make more JNI calls, behavior is undefined.
 */

#include <jni.h>
#include <stdlib.h>    /* for NULL */

/*
 * Java_com_stevenpg_jni_NativeCallback_processWithCallback
 *
 * Simulates work in C and calls back to Java's onProgress() method
 * after each iteration to report progress.
 *
 * This pattern is common in:
 *   - File processing (report bytes processed)
 *   - Image/video encoding (report frames completed)
 *   - Scientific computing (report iterations completed)
 *   - Database operations (report rows processed)
 */
JNIEXPORT void JNICALL Java_com_stevenpg_jni_NativeCallback_processWithCallback
  (JNIEnv *env, jobject obj, jint iterations) {

    /*
     * Step 1: Get the Java class of 'this' object.
     *
     * GetObjectClass returns the runtime class of the given object.
     * This is equivalent to obj.getClass() in Java.
     *
     * We could also use FindClass("com/stevenpg/jni/NativeCallback"),
     * but GetObjectClass is preferred because:
     *   - It works correctly with subclasses
     *   - It doesn't require hardcoding the class name
     *   - It's slightly faster (no string lookup)
     */
    jclass cls = (*env)->GetObjectClass(env, obj);

    /*
     * Step 2: Look up the onProgress method.
     *
     * GetMethodID takes:
     *   - The class to search in
     *   - The method name (as a C string)
     *   - The JNI type signature (as a C string)
     *
     * The signature "(ID)V" means:
     *   ( = start of parameters
     *   I = int parameter (step number)
     *   D = double parameter (percent complete)
     *   ) = end of parameters
     *   V = void return type
     *
     * RETURNS NULL if the method isn't found (NoSuchMethodError pending).
     * This usually means: wrong name, wrong signature, or wrong class.
     *
     * DEBUGGING TIP: Use javap -s ClassName to see the JNI signatures
     * for all methods in a class.
     */
    jmethodID onProgress = (*env)->GetMethodID(env, cls, "onProgress", "(ID)V");
    if (onProgress == NULL) {
        /* Method not found - NoSuchMethodError is already pending.
         * Just return and let it propagate to Java. */
        return;
    }

    /*
     * Step 3: Do work and call back to Java on each iteration.
     */
    for (jint i = 1; i <= iterations; i++) {
        /* Simulate some work (in real code, this would be actual computation) */
        volatile long dummy = 0;
        for (long j = 0; j < 1000000; j++) {
            dummy += j;  /* Busy work to simulate processing */
        }

        /* Calculate progress percentage */
        jdouble percentComplete = ((jdouble)i / (jdouble)iterations) * 100.0;

        /*
         * CallVoidMethod invokes the Java method.
         *
         * This is the actual upcall: control transfers from C to Java,
         * the Java method runs to completion, then control returns here.
         *
         * During the Java method execution:
         *   - The current thread is still in the "native" state from the JVM's perspective
         *   - The Java method can do anything (allocate objects, call other methods, etc.)
         *   - If the Java method throws, it becomes a pending exception
         *
         * The variant to use depends on the return type:
         *   CallVoidMethod    - void methods
         *   CallIntMethod     - int-returning methods
         *   CallLongMethod    - long-returning methods
         *   CallObjectMethod  - methods returning objects (String, arrays, etc.)
         *   CallBooleanMethod - boolean-returning methods
         *   etc.
         */
        (*env)->CallVoidMethod(env, obj, onProgress, i, percentComplete);

        /*
         * Check if the Java callback threw an exception.
         *
         * ExceptionCheck returns JNI_TRUE if there's a pending exception.
         * If we don't check and continue making JNI calls, the behavior
         * is undefined (likely a crash or silent data corruption).
         */
        if ((*env)->ExceptionCheck(env)) {
            /* An exception occurred in the Java callback.
             * We could handle it here, but typically we just bail out
             * and let it propagate to the Java caller. */
            return;
        }
    }
}

/*
 * Java_com_stevenpg_jni_NativeCallback_processStringsNatively
 *
 * Takes a Java String[], calls a Java method to transform each string,
 * and returns a new String[] with the results.
 *
 * This demonstrates:
 *   - Working with Java object arrays (jobjectArray)
 *   - Getting/setting individual array elements
 *   - Calling Java methods that take and return objects
 *   - Creating a new object array from C
 */
JNIEXPORT jobjectArray JNICALL Java_com_stevenpg_jni_NativeCallback_processStringsNatively
  (JNIEnv *env, jobject obj, jobjectArray values) {

    /* Get the length of the input array */
    jsize len = (*env)->GetArrayLength(env, values);

    /* Look up the transformString method.
     * Signature: "(Ljava/lang/String;)Ljava/lang/String;"
     *   L = object type follows (until ;)
     *   java/lang/String = the class name (with slashes)
     *   ; = end of object type
     *
     * So this reads as: takes a String, returns a String. */
    jclass cls = (*env)->GetObjectClass(env, obj);
    jmethodID transformMethod = (*env)->GetMethodID(env, cls,
        "transformString", "(Ljava/lang/String;)Ljava/lang/String;");
    if (transformMethod == NULL) return NULL;

    /*
     * Create a new String[] array for the results.
     *
     * NewObjectArray creates an array of objects (unlike NewIntArray
     * which creates an array of primitives). It needs:
     *   - Length
     *   - Element class (String.class in this case)
     *   - Initial value for all elements (NULL = all null)
     */
    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray result = (*env)->NewObjectArray(env, len, stringClass, NULL);
    if (result == NULL) return NULL;

    /* Process each string: get from input, transform via Java, put in output */
    for (jsize i = 0; i < len; i++) {
        /*
         * GetObjectArrayElement returns the element at index i.
         * Returns a jobject (which is really a jstring here).
         * This is a LOCAL REFERENCE - valid until this JNI call returns
         * or until you explicitly DeleteLocalRef().
         */
        jstring inputStr = (jstring)(*env)->GetObjectArrayElement(env, values, i);

        /*
         * CallObjectMethod invokes transformString() and returns the result.
         * The result is also a local reference.
         */
        jstring transformed = (jstring)(*env)->CallObjectMethod(env, obj,
            transformMethod, inputStr);

        if ((*env)->ExceptionCheck(env)) return NULL;

        /*
         * SetObjectArrayElement puts the transformed string into our result array.
         * This copies the reference into the array - the array now holds a
         * reference to the String object.
         */
        (*env)->SetObjectArrayElement(env, result, i, transformed);

        /*
         * Delete local references to avoid running out.
         *
         * JNI has a limited number of local reference slots (typically 512).
         * In a long loop, you can exhaust them! DeleteLocalRef frees a slot.
         *
         * This is a subtle but IMPORTANT best practice for loops.
         * Without it, processing a large array could crash with:
         *   "JNI ERROR: local reference table overflow"
         */
        (*env)->DeleteLocalRef(env, inputStr);
        (*env)->DeleteLocalRef(env, transformed);
    }

    return result;
}
