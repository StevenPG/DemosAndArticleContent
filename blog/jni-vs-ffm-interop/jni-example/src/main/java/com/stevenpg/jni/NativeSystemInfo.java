package com.stevenpg.jni;

/**
 * =====================================================================
 * NativeSystemInfo - JNI System-Level Function Calls
 * =====================================================================
 *
 * One of the primary reasons JNI exists is to access operating system
 * functionality that Java's standard library doesn't expose. While Java's
 * ProcessHandle and InetAddress cover many cases today, there are still
 * OS-specific system calls, hardware interfaces, and legacy C libraries
 * that can only be accessed through native interop.
 *
 * This class demonstrates calling POSIX system functions:
 *   - gethostname() : Get the system's hostname
 *   - getpid()      : Get the current process ID
 *   - malloc/free   : Manual native memory management
 *
 * NATIVE MEMORY MANAGEMENT IN JNI:
 *   The JVM's garbage collector manages Java heap memory automatically.
 *   However, any memory allocated in native code (via malloc, mmap, etc.)
 *   is completely invisible to the GC. This means:
 *
 *     1. You MUST free it manually, or you get a memory leak
 *     2. There's no finalizer or cleaner that runs automatically
 *     3. If the Java object that "owns" native memory gets GC'd without
 *        freeing the native memory, that memory is leaked forever
 *
 *   This is one of the biggest footguns in JNI and a primary motivation
 *   for FFM's Arena-based memory management.
 *
 * ERROR HANDLING PATTERN:
 *   When a native function encounters an error, it can:
 *     1. Return an error code (like NULL or -1)
 *     2. Throw a Java exception using (*env)->ThrowNew()
 *
 *   IMPORTANT: ThrowNew() doesn't immediately unwind the stack like
 *   Java's "throw" keyword. The C function continues executing! You must
 *   explicitly return after calling ThrowNew(). The exception only
 *   propagates to Java after the native function returns.
 *
 * SEE ALSO: The corresponding C implementation is in:
 *   src/main/native/native_system_info.c
 */
public class NativeSystemInfo {

    static {
        System.loadLibrary("jniexamples");
    }

    /**
     * Gets the system hostname via the POSIX gethostname() function.
     *
     * In the C implementation, this:
     *   1. Declares a stack-allocated char buffer
     *   2. Calls gethostname() to fill it
     *   3. On success: creates a new Java String via NewStringUTF()
     *   4. On failure: throws a Java IOException via ThrowNew()
     *
     * This is a good example of how JNI bridges C error handling
     * (return codes) into Java error handling (exceptions).
     *
     * NOTE: Java's InetAddress.getLocalHost().getHostName() does a
     * similar thing, but goes through DNS resolution which can be slow
     * or fail. The direct gethostname() call is instantaneous.
     *
     * @return the system hostname
     * @throws java.io.IOException if the system call fails
     */
    public native String getHostname();

    /**
     * Gets the current process ID via the POSIX getpid() function.
     *
     * This is the simplest possible JNI example - a function that takes
     * no arguments and returns a primitive. In the C code:
     *   return (jlong)getpid();
     *
     * NOTE: Since Java 9, you can do this in pure Java via
     * ProcessHandle.current().pid(). This example is for demonstration
     * purposes - in production, prefer the Java API when available.
     *
     * @return the process ID of the running JVM
     */
    public native long getProcessId();

    /**
     * Allocates native (off-heap) memory, writes a message, and reads it back.
     *
     * This demonstrates the COMPLETE lifecycle of native memory in JNI:
     *   1. Receive a Java String, convert to C string (GetStringUTFChars)
     *   2. Allocate native memory via malloc()
     *   3. Copy the string data into native memory (memcpy)
     *   4. Read it back and create a new Java String (NewStringUTF)
     *   5. Free the native memory (free())
     *   6. Release the original string (ReleaseStringUTFChars)
     *
     * In a real-world scenario, the native memory might be:
     *   - Shared with another native library (e.g., a database driver)
     *   - Mapped to a hardware device (e.g., GPU memory, FPGA buffers)
     *   - Used for memory-mapped files or shared memory between processes
     *   - Passed to an OS API that requires a specific memory layout
     *
     * COMPARE WITH FFM: The FFM equivalent (in SystemCallExamples) does
     * the same thing but in pure Java, and can use Arena for automatic
     * cleanup instead of manual free().
     *
     * @param message the string to write to native memory and read back
     * @return the string read back from native memory (should match input)
     */
    public native String allocateAndReadNativeMemory(String message);
}
