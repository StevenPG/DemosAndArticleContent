package com.stevenpg.jni;

/**
 * =====================================================================
 * NativeStringUtils - JNI String Operations
 * =====================================================================
 *
 * This class demonstrates how JNI handles Java Strings crossing the
 * Java/native boundary. Strings are one of the trickiest parts of JNI
 * because Java uses UTF-16 internally, while C uses null-terminated
 * char arrays (typically UTF-8 or ASCII).
 *
 * HOW IT WORKS:
 * 1. You declare methods as "native" - this tells the JVM that the
 *    implementation lives in a compiled C/C++ shared library, not in Java.
 *
 * 2. At class load time, the static initializer calls System.loadLibrary()
 *    to load the compiled .so (Linux), .dylib (macOS), or .dll (Windows).
 *
 * 3. When you call a native method, the JVM looks up the C function by
 *    its mangled name (Java_com_stevenpg_jni_NativeStringUtils_toUpperCase)
 *    and invokes it, passing a JNIEnv* pointer and the method arguments.
 *
 * THE NATIVE METHOD LIFECYCLE:
 *    Java code               JVM                  Native code (.so)
 *    ─────────────────────────────────────────────────────────────────
 *    stringUtils.toUpperCase("hello")
 *         │
 *         └──────────────► JVM looks up the       C function is called with:
 *                          C function by name ──► (JNIEnv *env, jobject this,
 *                                                  jstring input)
 *                                                      │
 *                                                      ▼
 *                                                 GetStringUTFChars()
 *                                                 ... do work ...
 *                                                 ReleaseStringUTFChars()
 *                                                 NewStringUTF(result)
 *                                                      │
 *         result ◄─────────────────────────────────────┘
 *
 * IMPORTANT GOTCHA - String Encoding:
 *   JNI's "Modified UTF-8" is NOT standard UTF-8. It encodes the null
 *   character as a two-byte sequence (0xC0, 0x80) and uses a modified
 *   encoding for supplementary characters. For ASCII text this doesn't
 *   matter, but be careful with international text or binary data.
 *
 * SEE ALSO: The corresponding C implementation is in:
 *   src/main/native/native_string_utils.c
 */
public class NativeStringUtils {

    /*
     * Static initializer block - runs exactly once when this class is first loaded.
     *
     * System.loadLibrary("jniexamples") tells the JVM to search for:
     *   - Linux:   libjniexamples.so
     *   - macOS:   libjniexamples.dylib
     *   - Windows: jniexamples.dll
     *
     * The JVM searches these locations (in order):
     *   1. Paths in the java.library.path system property
     *   2. The LD_LIBRARY_PATH (Linux) / DYLD_LIBRARY_PATH (macOS) / PATH (Windows)
     *
     * If the library isn't found, you get an UnsatisfiedLinkError at runtime.
     * This is one of the most common JNI errors - it means either:
     *   - The library isn't compiled yet
     *   - It's not on the library path
     *   - It was compiled for the wrong architecture (e.g., x86 vs ARM)
     */
    static {
        System.loadLibrary("jniexamples");
    }

    /**
     * Converts a string to uppercase using native C code.
     *
     * The "native" keyword means: "this method has no Java body; the JVM
     * should look for a C function named Java_com_stevenpg_jni_NativeStringUtils_toUpperCase".
     *
     * What happens at the C level:
     *   1. The JVM passes a jstring (an opaque handle to the Java String object)
     *   2. C code calls GetStringUTFChars() to get a const char* copy
     *   3. C code does the uppercase conversion using toupper()
     *   4. C code creates a new Java String with NewStringUTF()
     *   5. C code releases the original string with ReleaseStringUTFChars()
     *
     * @param input the string to convert (passed as a jstring handle to C)
     * @return a new uppercase string (created in C via NewStringUTF)
     */
    public native String toUpperCase(String input);

    /**
     * Reverses a string using native C code.
     *
     * This demonstrates the same string-crossing pattern as toUpperCase,
     * but with different logic in the C layer. The point is that ALL
     * string operations in JNI follow the same Get/Use/Release pattern,
     * regardless of what you're doing with the string data.
     *
     * @param input the string to reverse
     * @return a new reversed string
     */
    public native String reverse(String input);

    /**
     * Counts occurrences of a character in a string.
     *
     * This method demonstrates passing BOTH an object (String) and a
     * primitive (char) to native code. In JNI:
     *   - Java String  → jstring (opaque handle, needs GetStringUTFChars)
     *   - Java char    → jchar (16-bit unsigned value, passed directly)
     *   - Java int     → jint (32-bit signed, passed directly)
     *
     * Primitives are simple - they map directly to C types. Objects are
     * always passed as opaque handles that need JNI accessor functions.
     *
     * @param input  the string to search through
     * @param target the character to count
     * @return number of occurrences of target in input
     */
    public native int countChar(String input, char target);
}
