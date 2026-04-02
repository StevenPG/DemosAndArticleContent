package com.stevenpg.ffm;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * =====================================================================
 * UpcallExamples - Native Code Calling Back Into Java via FFM
 * =====================================================================
 *
 * "Upcall" = native code calls UP into Java (the reverse of "downcall").
 *
 * Many C APIs use FUNCTION POINTERS for callbacks. For example:
 *   - qsort() takes a comparator function pointer
 *   - signal() takes a signal handler function pointer
 *   - pthread_create() takes a thread function pointer
 *   - GUI toolkits use callback functions for event handling
 *
 * FFM allows you to create a native function pointer that, when called
 * by C code, invokes a Java method. This is called an "upcall stub".
 *
 * HOW IT WORKS:
 *
 *   Java                          FFM                         C (native)
 *   ──────────────────────────────────────────────────────────────────────
 *   Define a Java method     →
 *   Create a MethodHandle    →    linker.upcallStub()
 *                                 generates native code
 *                                 that calls the Java method  → function pointer
 *                                                                    │
 *   Pass func ptr to C      →    downcallHandle.invoke(funcPtr)     │
 *                                                                    │
 *                                                             C code calls
 *                                                             the function pointer
 *                                                                    │
 *   Java method executes    ←    upcall stub bridges back     ←────┘
 *
 * COMPARISON WITH JNI CALLBACKS:
 *
 *   JNI (see NativeCallback.java):
 *     1. C code must call FindClass + GetMethodID to find the Java method
 *     2. C code must use CallVoidMethod/CallObjectMethod to invoke it
 *     3. Requires JNIEnv* which is thread-local
 *     4. Must handle ExceptionCheck after each callback
 *     5. For native threads: AttachCurrentThread is required
 *     → Complex, error-prone, lots of boilerplate in C
 *
 *   FFM (this class):
 *     1. Create a MethodHandle pointing to the Java method
 *     2. Create an upcall stub (function pointer) from the MethodHandle
 *     3. Pass the function pointer to C like any other argument
 *     4. C code calls it like a normal function pointer
 *     → Simple, type-safe, everything stays in Java
 *
 * THE CLASSIC EXAMPLE: qsort with a Java comparator.
 *   C's qsort needs a function pointer: int (*compar)(const void*, const void*)
 *   We create an upcall stub that wraps a Java comparison method.
 *   When qsort calls the function pointer, our Java code runs.
 */
public class UpcallExamples {

    public static void run() {
        System.out.println("--- Upcall Examples (Native → Java Callbacks via FFM) ---");

        Linker linker = Linker.nativeLinker();
        SymbolLookup stdlib = linker.defaultLookup();

        try {
            qsortWithJavaComparator(linker, stdlib);
        } catch (Throwable e) {
            System.err.println("Error in upcall examples: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * Sorts an array using C's qsort() with a comparator written in Java.
     *
     * This is the classic upcall example because qsort is a well-known
     * C function that REQUIRES a function pointer callback.
     *
     * C signature:
     *   void qsort(void *base, size_t nmemb, size_t size,
     *              int (*compar)(const void *, const void *))
     *
     * The fourth parameter is a function pointer. We'll create an FFM
     * upcall stub that wraps our Java comparator method, turning a
     * Java method into a C function pointer.
     */
    private static void qsortWithJavaComparator(Linker linker, SymbolLookup stdlib) throws Throwable {

        /* Step 1: Look up qsort in the C standard library. */
        MethodHandle qsort = linker.downcallHandle(
                stdlib.find("qsort").orElseThrow(),
                FunctionDescriptor.ofVoid(           // returns void
                        ValueLayout.ADDRESS,         // void *base (array to sort)
                        ValueLayout.JAVA_LONG,       // size_t nmemb (number of elements)
                        ValueLayout.JAVA_LONG,       // size_t size (size of each element)
                        ValueLayout.ADDRESS           // int (*compar)(...) - FUNCTION POINTER
                )
        );

        /*
         * Step 2: Create a MethodHandle pointing to our Java comparator.
         *
         * MethodHandles.lookup().findStatic() finds a static method by:
         *   - Class: UpcallExamples.class
         *   - Name: "compareInts"
         *   - Type: MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class)
         *
         * The MethodType describes the Java method's signature:
         *   - Returns int
         *   - Takes two MemorySegment parameters (the void* pointers that qsort passes)
         *
         * This is similar to JNI's GetMethodID but done entirely in Java
         * with compile-time-checkable types.
         */
        MethodHandle comparatorHandle = MethodHandles.lookup().findStatic(
                UpcallExamples.class,
                "compareInts",
                MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class)
        );

        /*
         * Step 3: Describe the C callback signature.
         *
         * This describes what qsort EXPECTS the callback to look like:
         *   int comparator(const void *a, const void *b)
         *
         * The FunctionDescriptor must match both:
         *   - What qsort expects (the C function pointer type)
         *   - What our Java method provides (the MethodHandle signature)
         */
        FunctionDescriptor comparatorDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,   // return: int
                ValueLayout.ADDRESS,    // param: const void *a
                ValueLayout.ADDRESS     // param: const void *b
        );

        try (Arena arena = Arena.ofConfined()) {
            /*
             * Step 4: Create the upcall stub.
             *
             * linker.upcallStub() generates a native function pointer that,
             * when called by C code, invokes our Java MethodHandle.
             *
             * Under the hood, FFM generates a small piece of native code that:
             *   1. Takes the C arguments (two void* pointers)
             *   2. Converts them to Java MemorySegments
             *   3. Calls our compareInts Java method
             *   4. Takes the Java int return value
             *   5. Returns it to C as an int
             *
             * The returned MemorySegment IS a function pointer - it points
             * to the generated native code. We can pass it to qsort.
             *
             * The Arena controls the stub's lifetime. When the arena closes,
             * the stub is destroyed. Using it after that would crash.
             */
            MemorySegment comparatorPtr = linker.upcallStub(
                    comparatorHandle, comparatorDesc, arena);

            /* Step 5: Prepare the data to sort */
            int[] data = {42, 17, 8, 99, 3, 61, 25, 50, 1, 88};

            /*
             * Allocate a native int array and copy our Java data into it.
             *
             * We need native memory because qsort operates on native pointers,
             * not Java arrays. This is different from JNI, where the JVM can
             * pin Java arrays for native access.
             */
            MemorySegment nativeArray = arena.allocate(
                    ValueLayout.JAVA_INT, data.length);
            for (int i = 0; i < data.length; i++) {
                nativeArray.setAtIndex(ValueLayout.JAVA_INT, i, data[i]);
            }

            /* Print before sorting */
            System.out.print("Before qsort: [");
            for (int i = 0; i < data.length; i++) {
                if (i > 0) System.out.print(", ");
                System.out.print(nativeArray.getAtIndex(ValueLayout.JAVA_INT, i));
            }
            System.out.println("]");

            /*
             * Step 6: Call qsort with our Java comparator!
             *
             * When qsort needs to compare two elements, it calls the
             * function pointer (comparatorPtr), which triggers our
             * Java compareInts method.
             *
             * This is the magic of upcall stubs: C code calls what it
             * thinks is a normal C function, but Java code runs.
             */
            qsort.invoke(
                    nativeArray,                            // array to sort
                    (long) data.length,                     // number of elements
                    (long) ValueLayout.JAVA_INT.byteSize(), // element size (4 bytes)
                    comparatorPtr                           // OUR JAVA COMPARATOR!
            );

            /* Print after sorting */
            System.out.print("After qsort:  [");
            for (int i = 0; i < data.length; i++) {
                if (i > 0) System.out.print(", ");
                System.out.print(nativeArray.getAtIndex(ValueLayout.JAVA_INT, i));
            }
            System.out.println("]");
            System.out.println("(Sorted using a Java comparator called from C's qsort!)");
        }
    }

    /**
     * Java comparator method called FROM C's qsort via the upcall stub.
     *
     * This method has NO idea it's being called from C - it's just a
     * regular Java static method. The upcall stub handles all the
     * marshalling between C and Java types.
     *
     * Parameters are MemorySegments because qsort passes void* pointers.
     * We must:
     *   1. Reinterpret them to know their size (qsort passes raw pointers)
     *   2. Read the int values from the pointed-to memory
     *   3. Return comparison result: negative/zero/positive
     *
     * @param a pointer to the first int (from qsort)
     * @param b pointer to the second int (from qsort)
     * @return negative if a < b, zero if equal, positive if a > b
     */
    static int compareInts(MemorySegment a, MemorySegment b) {
        /*
         * reinterpret(4) tells FFM that these pointers point to 4 bytes
         * of valid memory (the size of one int). Without this, the segments
         * have zero size and get() would throw.
         *
         * This is necessary because qsort passes void* (untyped pointers)
         * and FFM conservatively gives them zero size.
         */
        int va = a.reinterpret(4).get(ValueLayout.JAVA_INT, 0);
        int vb = b.reinterpret(4).get(ValueLayout.JAVA_INT, 0);
        return Integer.compare(va, vb);
    }
}
