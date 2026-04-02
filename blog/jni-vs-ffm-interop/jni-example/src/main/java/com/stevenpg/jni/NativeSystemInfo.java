package com.stevenpg.jni;

/**
 * Demonstrates JNI interaction with system-level C functions.
 * Shows how JNI can access OS-level information that Java
 * cannot easily access through standard APIs.
 */
public class NativeSystemInfo {

    static {
        System.loadLibrary("jniexamples");
    }

    /**
     * Gets the system hostname via the POSIX gethostname() function.
     * Demonstrates: calling standard C library functions from JNI.
     */
    public native String getHostname();

    /**
     * Gets the current process ID.
     * Demonstrates: returning primitive values from native code.
     */
    public native long getProcessId();

    /**
     * Allocates native memory, writes to it, and reads it back.
     * Demonstrates: native memory management (malloc/free) from JNI.
     * This is a common use case - managing memory that lives outside the JVM heap.
     */
    public native String allocateAndReadNativeMemory(String message);
}
