package com.stevenpg.ffm;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

/**
 * =====================================================================
 * AdvancedExamples - Pushing FFM Further
 * =====================================================================
 *
 * This class demonstrates advanced FFM patterns that go beyond the basics:
 *
 *   1. Variadic functions (like printf)
 *   2. Nested struct layouts
 *   3. Union layouts
 *   4. Passing structs by value (not just by pointer)
 *
 * These patterns cover scenarios that are particularly painful in JNI
 * and showcase FFM's expressiveness.
 *
 * VARIADIC FUNCTIONS:
 *   C's printf, snprintf, and similar functions accept a variable number
 *   of arguments. JNI has NO support for calling variadic functions -
 *   you'd have to write a C wrapper for each combination of arguments.
 *
 *   FFM supports variadic calls directly using Linker.Option.firstVariadicArg(),
 *   which tells the linker where the fixed parameters end and the
 *   variadic ones begin. This is necessary because some platforms (like
 *   macOS ARM64) use different calling conventions for variadic arguments.
 *
 * NESTED STRUCTS:
 *   C structs often contain other structs:
 *     struct Line { struct Point start; struct Point end; };
 *
 *   FFM handles this naturally with nested StructLayouts.
 *   JNI would require either manual offset calculations or multiple
 *   C wrapper functions to access nested fields.
 */
public class AdvancedExamples {

    public static void run() {
        System.out.println("--- Advanced FFM Examples ---");

        Linker linker = Linker.nativeLinker();
        SymbolLookup stdlib = linker.defaultLookup();

        try {
            callSnprintf(linker, stdlib);
            nestedStructDemo();
            unionLayoutDemo();
        } catch (Throwable e) {
            System.err.println("Error in advanced examples: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * Calls C's snprintf() - a VARIADIC function.
     *
     * C signature:
     *   int snprintf(char *str, size_t size, const char *format, ...);
     *
     * The "..." means it accepts a variable number of arguments after 'format'.
     * The actual arguments depend on the format string:
     *   snprintf(buf, 100, "Hello %s, you are %d years old", name, age);
     *
     * JNI CANNOT call variadic functions directly. You'd need to write a
     * separate C wrapper for each combination of argument types.
     *
     * FFM CAN call variadic functions using Linker.Option.firstVariadicArg()
     * to mark where the variadic arguments start.
     */
    private static void callSnprintf(Linker linker, SymbolLookup stdlib) throws Throwable {
        MemorySegment snprintfAddr = stdlib.find("snprintf")
                .orElseThrow(() -> new RuntimeException("snprintf not found"));

        /*
         * Define the function descriptor for THIS SPECIFIC CALL.
         *
         * Because variadic functions have different arguments each time,
         * we create a new descriptor for each call pattern.
         *
         * For: snprintf(buf, size, "%s scored %d points!", name, score)
         *   Fixed parameters: char *str, size_t size, const char *format
         *   Variadic parameters: const char *name, int score
         *
         * firstVariadicArg(3) tells the linker that parameter index 3
         * (0-based) is the first variadic argument. This is critical on
         * ARM64 macOS where variadic args use a different register/stack
         * convention than fixed args.
         */
        FunctionDescriptor snprintfDescStringInt = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,    // return: int (chars written)
                ValueLayout.ADDRESS,     // param 0: char *str (output buffer)
                ValueLayout.JAVA_LONG,   // param 1: size_t size (buffer size)
                ValueLayout.ADDRESS,     // param 2: const char *format (LAST FIXED)
                ValueLayout.ADDRESS,     // param 3: variadic - const char* (name)
                ValueLayout.JAVA_INT     // param 4: variadic - int (score)
        );

        /*
         * Create the downcall handle with the variadic option.
         *
         * Linker.Option.firstVariadicArg(3) says: "parameters 0-2 are
         * fixed, parameters 3+ are variadic."
         *
         * Without this option, the call might work on x86_64 Linux (where
         * fixed and variadic calling conventions are the same) but would
         * FAIL on ARM64 macOS (where they differ). Always specify it for
         * correctness and portability.
         */
        MethodHandle snprintf = linker.downcallHandle(
                snprintfAddr,
                snprintfDescStringInt,
                Linker.Option.firstVariadicArg(3)  // args after index 2 are variadic
        );

        try (Arena arena = Arena.ofConfined()) {
            /* Allocate output buffer and format string */
            MemorySegment buffer = arena.allocate(256);
            MemorySegment format = arena.allocateFrom("%s scored %d points!");
            MemorySegment name = arena.allocateFrom("Java");

            /* Call snprintf with variadic arguments */
            int charsWritten = (int) snprintf.invoke(
                    buffer,       // output buffer
                    256L,         // buffer size
                    format,       // format string
                    name,         // variadic arg 1: %s → "Java"
                    42            // variadic arg 2: %d → 42
            );

            String result = buffer.getString(0);
            System.out.println("snprintf:    \"" + result + "\" (" + charsWritten + " chars)");
        }

        /*
         * CALLING PRINTF WITH DIFFERENT ARGS:
         * Each different combination of variadic argument types needs its
         * own FunctionDescriptor and MethodHandle. For example, to call
         * snprintf(buf, size, "%f + %f = %f", a, b, a+b) you'd need:
         *
         * FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS,
         *     JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE)
         *
         * This is a limitation compared to C where the compiler handles
         * variadic argument promotion automatically.
         */
    }

    /**
     * Demonstrates nested struct layouts.
     *
     * C structs:
     *   struct Point2D { double x; double y; };
     *   struct Rectangle {
     *       struct Point2D topLeft;
     *       struct Point2D bottomRight;
     *       int color;      // RGBA packed into 32 bits
     *   };
     *
     * In JNI, accessing topLeft.x of a Rectangle requires either:
     *   a) Knowing the exact byte offset (0) and writing *(double*)(buf + 0)
     *   b) Writing a C helper function getTopLeftX(struct Rectangle *r)
     *
     * FFM gives you path-based access: "groupElement(topLeft) → groupElement(x)"
     */
    private static void nestedStructDemo() {
        /* Define Point2D layout */
        StructLayout point2dLayout = MemoryLayout.structLayout(
                ValueLayout.JAVA_DOUBLE.withName("x"),
                ValueLayout.JAVA_DOUBLE.withName("y")
        );

        /*
         * Define Rectangle layout with NESTED Point2D structs.
         *
         * structLayout can contain other StructLayouts as fields.
         * The nested struct's bytes are inlined (not a pointer to another struct).
         *
         * Memory layout:
         *   [topLeft.x(8)] [topLeft.y(8)] [bottomRight.x(8)] [bottomRight.y(8)] [color(4)] [padding(4)]
         *   offset: 0       8              16                  24                 32         36
         *   total: 40 bytes (with 4 bytes padding for alignment)
         *
         * NOTE: The padding after 'color' may vary by platform. FFM handles
         * this automatically based on the platform's struct layout rules.
         */
        StructLayout rectangleLayout = MemoryLayout.structLayout(
                point2dLayout.withName("topLeft"),
                point2dLayout.withName("bottomRight"),
                ValueLayout.JAVA_INT.withName("color"),
                MemoryLayout.paddingLayout(4)  // explicit padding for alignment
        );

        /*
         * Create VarHandles for NESTED field access.
         *
         * The path "topLeft" → "x" navigates into the nested struct.
         * This is like writing rect.topLeft.x in C, but type-safe.
         */
        VarHandle topLeftX = rectangleLayout.varHandle(
                MemoryLayout.PathElement.groupElement("topLeft"),
                MemoryLayout.PathElement.groupElement("x")
        );
        VarHandle topLeftY = rectangleLayout.varHandle(
                MemoryLayout.PathElement.groupElement("topLeft"),
                MemoryLayout.PathElement.groupElement("y")
        );
        VarHandle bottomRightX = rectangleLayout.varHandle(
                MemoryLayout.PathElement.groupElement("bottomRight"),
                MemoryLayout.PathElement.groupElement("x")
        );
        VarHandle bottomRightY = rectangleLayout.varHandle(
                MemoryLayout.PathElement.groupElement("bottomRight"),
                MemoryLayout.PathElement.groupElement("y")
        );
        VarHandle colorHandle = rectangleLayout.varHandle(
                MemoryLayout.PathElement.groupElement("color")
        );

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rect = arena.allocate(rectangleLayout);

            /* Set nested fields using path-based VarHandles */
            topLeftX.set(rect, 0L, 10.0);
            topLeftY.set(rect, 0L, 20.0);
            bottomRightX.set(rect, 0L, 100.0);
            bottomRightY.set(rect, 0L, 80.0);
            colorHandle.set(rect, 0L, 0xFF0000FF);  // Red in RGBA

            /* Read them back */
            System.out.printf("Rectangle { topLeft=(%.0f,%.0f), bottomRight=(%.0f,%.0f), color=0x%08X }%n",
                    (double) topLeftX.get(rect, 0L),
                    (double) topLeftY.get(rect, 0L),
                    (double) bottomRightX.get(rect, 0L),
                    (double) bottomRightY.get(rect, 0L),
                    (int) colorHandle.get(rect, 0L));
            System.out.println("Rectangle size: " + rectangleLayout.byteSize() + " bytes");
        }
    }

    /**
     * Demonstrates union layouts.
     *
     * A C union is like a struct where all fields share the SAME memory:
     *   union Value {
     *       int i;        // 4 bytes at offset 0
     *       float f;      // 4 bytes at offset 0 (SAME location!)
     *       char bytes[4]; // 4 bytes at offset 0 (SAME location!)
     *   };
     *
     * The union's total size is the size of its LARGEST member.
     * Writing to one field and reading another reinterprets the bytes.
     * This is used in C for:
     *   - Type punning (viewing the same bits as different types)
     *   - Variant types (tagged unions)
     *   - Hardware register access (different views of the same register)
     *
     * FFM supports unions via MemoryLayout.unionLayout().
     */
    private static void unionLayoutDemo() {
        /*
         * Define a union where an int and a float share the same 4 bytes.
         *
         * unionLayout places all fields at offset 0. The total size is
         * max(sizeof(int), sizeof(float)) = 4 bytes.
         */
        UnionLayout valueUnion = MemoryLayout.unionLayout(
                ValueLayout.JAVA_INT.withName("asInt"),
                ValueLayout.JAVA_FLOAT.withName("asFloat")
        );

        VarHandle asInt = valueUnion.varHandle(
                MemoryLayout.PathElement.groupElement("asInt"));
        VarHandle asFloat = valueUnion.varHandle(
                MemoryLayout.PathElement.groupElement("asFloat"));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(valueUnion);

            /*
             * Write a float value, then read the same bytes as an int.
             * This is "type punning" - reinterpreting the same bit pattern
             * as a different type.
             *
             * The float 3.14f has IEEE 754 bit pattern: 0x4048F5C3
             * Reading those same bits as an int gives: 1078523331
             */
            asFloat.set(value, 0L, 3.14f);
            int rawBits = (int) asInt.get(value, 0L);
            float original = (float) asFloat.get(value, 0L);

            System.out.printf("Union: float %.2f → raw int bits: %d (0x%08X)%n",
                    original, rawBits, rawBits);
            System.out.println("Union size: " + valueUnion.byteSize()
                    + " bytes (shared by all members)");
        }
    }
}
