/*
 * =====================================================================
 * math_utils.c - A Plain C Library Called by FFM
 * =====================================================================
 *
 * THIS IS THE KEY DIFFERENCE FROM JNI.
 *
 * Look at this file. Notice what's MISSING:
 *   ✗ No #include <jni.h>
 *   ✗ No Java_pkg_Class_method naming convention
 *   ✗ No JNIEnv* or jobject parameters
 *   ✗ No GetStringUTFChars / ReleaseStringUTFChars
 *   ✗ No NewIntArray / SetIntArrayRegion
 *   ✗ No ThrowNew for exception handling
 *
 * This is PLAIN C. It could be called from:
 *   - Java (via FFM, as we do here)
 *   - Python (via ctypes or cffi)
 *   - Rust (via FFI)
 *   - Go (via cgo)
 *   - Ruby (via fiddle)
 *   - Any language that supports C interop
 *
 * Now compare with the JNI example's C files (native_string_utils.c, etc.).
 * Those are littered with JNI-specific code and can ONLY be used from Java.
 *
 * This portability is one of FFM's biggest advantages: you write your C
 * library once, and it works with every language. With JNI, your C code
 * is tied to Java forever.
 *
 * COMPILATION:
 *   gcc -shared -fPIC -o libffmdemo.so math_utils.c -lm
 *
 *   -shared : Create a shared library (not an executable)
 *   -fPIC   : Position-Independent Code (required for shared libraries)
 *   -lm     : Link against the math library (for sqrt in distance())
 */

#include <math.h>    /* sqrt() - that's ALL we need. No jni.h! */

/*
 * A simple 2D point struct.
 *
 * When FFM calls distance(), it passes pointers to MemorySegments
 * that have the same byte layout as this struct:
 *   offset 0: double x (8 bytes)
 *   offset 8: double y (8 bytes)
 *
 * FFM's StructLayout must match this layout exactly. The Java code
 * defines it as:
 *   StructLayout.structLayout(
 *       JAVA_DOUBLE.withName("x"),
 *       JAVA_DOUBLE.withName("y")
 *   );
 */
typedef struct {
    double x;
    double y;
} Point2D;

/*
 * add - The simplest possible function.
 *
 * FFM maps this as:
 *   FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT)
 *   MethodHandle add = linker.downcallHandle(addr, desc);
 *   int result = (int) add.invoke(17, 25);
 *
 * Compare with JNI, which would need:
 *   JNIEXPORT jint JNICALL Java_pkg_Class_add(JNIEnv *env, jobject obj, jint a, jint b) {
 *       return a + b;
 *   }
 */
int add(int a, int b) {
    return a + b;
}

/*
 * factorial - Returns n! as a long.
 *
 * Note: C's 'long' is 8 bytes on 64-bit Linux but 4 bytes on Windows!
 * For portable 64-bit, use int64_t from <stdint.h>.
 * FFM maps C long → JAVA_LONG on Linux (both 8 bytes).
 */
long factorial(int n) {
    if (n <= 1) return 1;
    long result = 1;
    for (int i = 2; i <= n; i++) {
        result *= i;
    }
    return result;
}

/*
 * fibonacci - Returns the nth Fibonacci number.
 *
 * This is called in a loop from Java to demonstrate that MethodHandle
 * invocation is efficient enough for repeated calls.
 */
long fibonacci(int n) {
    if (n <= 0) return 0;
    if (n == 1) return 1;
    long a = 0, b = 1;
    for (int i = 2; i <= n; i++) {
        long temp = a + b;
        a = b;
        b = temp;
    }
    return b;
}

/*
 * distance - Euclidean distance between two Point2D structs.
 *
 * This function takes POINTERS to structs (const Point2D *).
 * In FFM, these are passed as MemorySegments with ADDRESS layout.
 *
 * FFM automatically extracts the native address from the MemorySegment
 * and passes it to this function as a const Point2D*.
 *
 * The Java side allocates the structs in an Arena:
 *   MemorySegment p1 = arena.allocate(point2dLayout);
 *   p1.set(JAVA_DOUBLE, 0, 3.0);  // x
 *   p1.set(JAVA_DOUBLE, 8, 4.0);  // y
 *   double d = (double) distance.invoke(p1, p2);
 */
double distance(const Point2D *p1, const Point2D *p2) {
    double dx = p2->x - p1->x;
    double dy = p2->y - p1->y;
    return sqrt(dx * dx + dy * dy);
}
