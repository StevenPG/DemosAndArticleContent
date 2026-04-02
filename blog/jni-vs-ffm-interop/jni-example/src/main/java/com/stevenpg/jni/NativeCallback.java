package com.stevenpg.jni;

/**
 * =====================================================================
 * NativeCallback - JNI Upcalls (Native Code Calling Back into Java)
 * =====================================================================
 *
 * One of JNI's most powerful (and complex) features is the ability for
 * native C code to call BACK into Java. This is called an "upcall" or
 * "callback" and is essential for scenarios like:
 *
 *   - Event-driven native libraries (GUI toolkits, hardware events)
 *   - Progress reporting from long-running native operations
 *   - Native code that needs to use Java's networking, logging, etc.
 *   - Sort comparators where the comparison logic is in Java
 *
 * HOW JNI CALLBACKS WORK:
 *
 *   Java                        C (native)
 *   ────────────────────────────────────────────────────────────
 *   obj.processWithCallback()
 *        │
 *        └──► C function starts work
 *                   │
 *                   │  FindClass("com/stevenpg/jni/NativeCallback")
 *                   │  GetMethodID(class, "onProgress", "(ID)V")
 *                   │  CallVoidMethod(obj, methodID, i, progress)
 *                   │           │
 *                   │           └──► onProgress(i, progress) runs in Java
 *                   │           ◄────  returns to C
 *                   │
 *                   │  ... continues work, calls back again ...
 *                   │
 *        ◄──────── C function returns
 *
 * The key JNI functions for callbacks are:
 *   - FindClass()       : Look up a Java class by its JNI name (with / not .)
 *   - GetMethodID()     : Look up a method by name and JNI type signature
 *   - CallVoidMethod()  : Invoke a void Java method from C
 *   - CallIntMethod()   : Invoke an int-returning Java method from C
 *   - CallObjectMethod(): Invoke an object-returning Java method from C
 *
 * JNI TYPE SIGNATURES (the cryptic strings):
 *   These describe method parameters and return types:
 *     (ID)V  means: takes an int (I) and a double (D), returns void (V)
 *     ()I    means: takes nothing, returns int
 *     (Ljava/lang/String;)V means: takes a String, returns void
 *
 *   Type codes: Z=boolean, B=byte, C=char, S=short, I=int, J=long,
 *               F=float, D=double, V=void, L<class>;=object, [=array
 *
 * THREADING CONCERN:
 *   The JNIEnv* pointer is THREAD-LOCAL. If a native library creates its
 *   own threads and wants to call back into Java from those threads, each
 *   thread must first call AttachCurrentThread() to get its own JNIEnv*.
 *   Forgetting this will crash the JVM. This is demonstrated in the
 *   NativeThreading class.
 *
 * SEE ALSO: The corresponding C implementation is in:
 *   src/main/native/native_callback.c
 */
public class NativeCallback {

    static {
        System.loadLibrary("jniexamples");
    }

    /**
     * Starts a native operation that calls back into Java to report progress.
     *
     * The C implementation will:
     *   1. Look up the onProgress method on this Java object
     *   2. Do some simulated "work" (a loop)
     *   3. Call onProgress() on each iteration to report back to Java
     *
     * This demonstrates the full round-trip: Java → C → Java → C → ...
     *
     * @param iterations how many work steps to simulate
     */
    public native void processWithCallback(int iterations);

    /**
     * This method is called FROM C code during processWithCallback().
     * It's a regular Java method - the fact that it's invoked from C
     * is completely transparent. You could even override it in a subclass.
     *
     * The C code finds this method using:
     *   GetMethodID(env, class, "onProgress", "(ID)V")
     *
     * Where "(ID)V" is the JNI type signature:
     *   I = int parameter (step)
     *   D = double parameter (percentComplete)
     *   V = void return type
     *
     * @param step the current iteration number
     * @param percentComplete progress as a percentage (0.0 to 100.0)
     */
    public void onProgress(int step, double percentComplete) {
        System.out.printf("  [Callback from C] Step %d: %.1f%% complete%n",
                step, percentComplete);
    }

    /**
     * Demonstrates a native function that calls a Java method to transform data.
     *
     * The C code calls transformString() for each element, letting Java
     * do the string processing while C manages the iteration. This pattern
     * is common in native libraries that handle I/O or data structures but
     * delegate business logic to Java.
     *
     * @param values an array of strings to transform
     * @return a new array with each string transformed by transformString()
     */
    public native String[] processStringsNatively(String[] values);

    /**
     * Called from C to transform a single string.
     * C looks this up as: GetMethodID(..., "transformString",
     *     "(Ljava/lang/String;)Ljava/lang/String;")
     *
     * The JNI type signature breakdown:
     *   ( = start of parameters
     *   Ljava/lang/String; = one parameter of type java.lang.String
     *   ) = end of parameters
     *   Ljava/lang/String; = return type is java.lang.String
     *
     * @param input the string to transform
     * @return the transformed string
     */
    public String transformString(String input) {
        // This Java logic is called from C - any Java code works here
        return "[" + input.toUpperCase() + "]";
    }
}
