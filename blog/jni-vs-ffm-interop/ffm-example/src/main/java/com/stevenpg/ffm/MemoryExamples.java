package com.stevenpg.ffm;

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;

/**
 * =====================================================================
 * MemoryExamples - FFM Memory Management and Data Structures
 * =====================================================================
 *
 * FFM provides a structured, safe way to work with native (off-heap) memory.
 * This is one of FFM's biggest improvements over JNI.
 *
 * THE PROBLEM FFM SOLVES:
 *   In JNI, native memory management is entirely manual:
 *     - malloc/free (C)
 *     - Easy to forget free → memory leak
 *     - Easy to use-after-free → crash or security vulnerability
 *     - Easy to double-free → crash
 *     - Easy to buffer overflow → crash or security vulnerability
 *     - No tool in Java to detect these issues
 *
 *   FFM replaces all of this with:
 *     - Arena: Automatically frees all memory when closed
 *     - MemorySegment: Bounds-checked access (no buffer overflows)
 *     - StructLayout: Type-safe struct field access (no offset math)
 *     - VarHandle: Type-safe, named field accessors
 *
 * KEY CONCEPTS:
 *
 *   MemorySegment:
 *     A bounded reference to a region of memory. Think of it as a "fat pointer"
 *     that knows its own size. Every access is bounds-checked at runtime:
 *       - segment.set(JAVA_INT, 0, 42)   ← writes 4 bytes at offset 0 ✓
 *       - segment.set(JAVA_INT, 100, 42) ← throws if offset+4 > segment size
 *
 *   Arena:
 *     A memory lifecycle manager. Allocations from an Arena are freed when
 *     the Arena closes. Like a region-based allocator in systems programming.
 *
 *   MemoryLayout:
 *     Describes the structure of native data. Used to:
 *       - Calculate sizes and alignments automatically
 *       - Generate VarHandles for type-safe field access
 *       - Support nested structs and arrays
 *
 *   VarHandle:
 *     A type-safe, named accessor for a field in a MemoryLayout.
 *     Like a getter/setter, but for native memory fields.
 *
 * COMPARISON WITH ByteBuffer:
 *   Java's ByteBuffer (especially DirectByteBuffer) also provides off-heap
 *   memory, but:
 *     - ByteBuffer max size: ~2GB (int-indexed)
 *     - MemorySegment max size: up to full address space (long-indexed)
 *     - ByteBuffer has no struct concept
 *     - MemorySegment integrates with the FFM function call API
 *     - MemorySegment has deterministic deallocation (via Arena)
 *     - ByteBuffer relies on GC + Cleaner (non-deterministic)
 */
public class MemoryExamples {

    public static void run() {
        System.out.println("--- Memory Operations (FFM MemorySegment & Arena) ---");

        basicMemoryAllocation();
        structLayout();
        sequenceLayoutDemo();
        memorySlicing();

        System.out.println();
    }

    /**
     * Basic off-heap memory allocation, write, and read.
     *
     * This is equivalent to:
     *   int *data = calloc(10, sizeof(int));   // allocate + zero-initialize
     *   data[0] = 0; data[1] = 1; ...         // write
     *   printf("%d", data[0]);                 // read
     *   free(data);                            // free
     *
     * But with FFM, the free() is automatic and access is bounds-checked.
     */
    private static void basicMemoryAllocation() {
        /*
         * Arena.ofConfined() creates a confined arena:
         *   - "Confined" = only the creating thread can allocate from it
         *   - This is the most efficient arena type (no synchronization needed)
         *   - Use Arena.ofShared() if multiple threads need to allocate
         *
         * try-with-resources ensures the arena closes at the end of the block.
         * When the arena closes, ALL memory allocated from it is freed instantly.
         * This is deterministic - not "eventually" like garbage collection.
         */
        try (Arena arena = Arena.ofConfined()) {
            /*
             * arena.allocate(layout, count) allocates count elements of the given type.
             *
             * This allocates 10 * 4 = 40 bytes of off-heap memory, zero-initialized.
             * The returned MemorySegment knows its size (40 bytes) and arena.
             *
             * Equivalent C: int *segment = calloc(10, sizeof(int));
             */
            MemorySegment segment = arena.allocate(ValueLayout.JAVA_INT, 10);

            /*
             * Write values using setAtIndex().
             *
             * setAtIndex(layout, index, value) writes at byte offset (index * layout.byteSize()).
             * For JAVA_INT (4 bytes): index 0 → offset 0, index 1 → offset 4, etc.
             *
             * BOUNDS CHECKING: If you try setAtIndex(JAVA_INT, 10, 0) on a 10-element
             * array, you get IndexOutOfBoundsException. In C, this would silently
             * corrupt adjacent memory (buffer overflow vulnerability).
             */
            for (int i = 0; i < 10; i++) {
                segment.setAtIndex(ValueLayout.JAVA_INT, i, i * i);
            }

            /* Read values back with getAtIndex() - same bounds checking applies. */
            StringBuilder sb = new StringBuilder("Squares: [");
            for (int i = 0; i < 10; i++) {
                if (i > 0) sb.append(", ");
                sb.append(segment.getAtIndex(ValueLayout.JAVA_INT, i));
            }
            sb.append("]");
            System.out.println(sb);
        }
        /*
         * After the arena closes:
         *   - The 40 bytes of native memory are freed
         *   - 'segment' is now a dangling reference
         *   - Calling segment.getAtIndex() would throw IllegalStateException
         *   - This is MUCH safer than C, where a dangling pointer silently reads garbage
         */
    }

    /**
     * Demonstrates StructLayout - mapping C structs to Java.
     *
     * In JNI, accessing struct fields requires either:
     *   a) Manual byte offset calculations (error-prone)
     *   b) Writing C wrapper functions for each field (tedious)
     *
     * FFM's StructLayout provides named, type-safe access.
     *
     * Equivalent C struct:
     *   struct Point3D {
     *       double x;    // 8 bytes, offset 0
     *       double y;    // 8 bytes, offset 8
     *       double z;    // 8 bytes, offset 16
     *   };               // total: 24 bytes
     */
    private static void structLayout() {
        /*
         * Define the struct layout.
         *
         * MemoryLayout.structLayout() creates a layout matching C struct rules:
         *   - Fields are laid out sequentially
         *   - Alignment/padding is added automatically (like C compilers do)
         *   - Each field has a name for type-safe access
         *
         * withName() gives fields human-readable names that can be used to
         * create VarHandles (type-safe accessors).
         */
        StructLayout point3dLayout = MemoryLayout.structLayout(
                ValueLayout.JAVA_DOUBLE.withName("x"),  // 8 bytes at offset 0
                ValueLayout.JAVA_DOUBLE.withName("y"),  // 8 bytes at offset 8
                ValueLayout.JAVA_DOUBLE.withName("z")   // 8 bytes at offset 16
        );

        /*
         * Create VarHandles for each field.
         *
         * VarHandle is like a type-safe getter/setter for a field in native memory.
         * It knows:
         *   - The byte offset of the field within the struct
         *   - The type of the field (double, int, etc.)
         *   - How to read/write it correctly (byte order, alignment)
         *
         * PathElement.groupElement("x") says: "find the field named 'x' in
         * the struct". For nested structs, you can chain path elements.
         *
         * COMPARE WITH JNI: In JNI you'd manually write:
         *   double x = *(double*)(buffer + 0);   // hardcoded offset, no safety
         *   double y = *(double*)(buffer + 8);   // wrong offset = silent corruption
         */
        VarHandle xHandle = point3dLayout.varHandle(MemoryLayout.PathElement.groupElement("x"));
        VarHandle yHandle = point3dLayout.varHandle(MemoryLayout.PathElement.groupElement("y"));
        VarHandle zHandle = point3dLayout.varHandle(MemoryLayout.PathElement.groupElement("z"));

        try (Arena arena = Arena.ofConfined()) {
            /*
             * Allocate a Point3D struct.
             *
             * arena.allocate(layout) allocates exactly the right number of bytes
             * with the correct alignment. No manual size calculation needed.
             * point3dLayout.byteSize() = 24 bytes for three doubles.
             */
            MemorySegment point = arena.allocate(point3dLayout);

            /*
             * Set fields using VarHandles.
             *
             * The parameters are: (segment, baseOffset, value)
             *   - segment: the memory where the struct lives
             *   - baseOffset: 0L because 'point' starts at the struct's base
             *   - value: the field value (must match the VarHandle's type)
             *
             * If you pass the wrong type (e.g., int instead of double),
             * you get a compile-time or runtime error. In C, you'd just
             * get silent data corruption.
             */
            xHandle.set(point, 0L, 1.0);
            yHandle.set(point, 0L, 2.5);
            zHandle.set(point, 0L, 3.7);

            /* Read fields back - same pattern */
            double x = (double) xHandle.get(point, 0L);
            double y = (double) yHandle.get(point, 0L);
            double z = (double) zHandle.get(point, 0L);

            System.out.println("Point3D { x=" + x + ", y=" + y + ", z=" + z + " }");
            System.out.println("Point3D size: " + point3dLayout.byteSize() + " bytes");
        }
    }

    /**
     * Demonstrates SequenceLayout - arrays of structs in native memory.
     *
     * This is one of FFM's most powerful features for working with bulk data.
     * It maps naturally to C patterns like:
     *   struct Point2D points[100];
     *
     * SequenceLayout knows the stride (bytes between elements) and gives
     * you indexed access to each element's fields.
     */
    private static void sequenceLayoutDemo() {
        /*
         * Define a Point2D struct: { double x; double y; }
         */
        StructLayout point2dLayout = MemoryLayout.structLayout(
                ValueLayout.JAVA_DOUBLE.withName("x"),
                ValueLayout.JAVA_DOUBLE.withName("y")
        );

        /*
         * SequenceLayout creates an array of 5 Point2D structs.
         *
         * This is equivalent to: struct Point2D points[5];
         *
         * The layout knows:
         *   - Total size: 5 * 16 = 80 bytes
         *   - Element stride: 16 bytes (size of one Point2D)
         *   - How to index into individual elements
         */
        SequenceLayout pointsArrayLayout = MemoryLayout.sequenceLayout(5, point2dLayout);

        /*
         * Create VarHandles that can access x and y for ANY element in the array.
         *
         * PathElement.sequenceElement() says: "the array index is a parameter"
         * PathElement.groupElement("x") says: "access the 'x' field of that element"
         *
         * The resulting VarHandle takes: (segment, baseOffset, arrayIndex, value)
         */
        VarHandle xHandle = pointsArrayLayout.varHandle(
                MemoryLayout.PathElement.sequenceElement(),
                MemoryLayout.PathElement.groupElement("x")
        );
        VarHandle yHandle = pointsArrayLayout.varHandle(
                MemoryLayout.PathElement.sequenceElement(),
                MemoryLayout.PathElement.groupElement("y")
        );

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment points = arena.allocate(pointsArrayLayout);

            /* Initialize 5 points forming a pattern */
            for (int i = 0; i < 5; i++) {
                xHandle.set(points, 0L, (long) i, (double) i * 1.5);
                yHandle.set(points, 0L, (long) i, (double) i * i);
            }

            /* Read them back */
            StringBuilder sb = new StringBuilder("Point2D[5]: [");
            for (int i = 0; i < 5; i++) {
                if (i > 0) sb.append(", ");
                double x = (double) xHandle.get(points, 0L, (long) i);
                double y = (double) yHandle.get(points, 0L, (long) i);
                sb.append("(").append(x).append(",").append(y).append(")");
            }
            sb.append("]");
            System.out.println(sb);
            System.out.println("Points array size: " + pointsArrayLayout.byteSize() + " bytes");
        }
    }

    /**
     * Demonstrates memory slicing - creating views into larger memory regions.
     *
     * Slicing is useful for:
     *   - Parsing binary protocols (e.g., slice out a header, then the payload)
     *   - Working with subsets of large data buffers
     *   - Implementing scatter/gather I/O patterns
     *   - Passing a portion of a buffer to a native function
     *
     * A slice is a VIEW - it shares the same underlying memory as the
     * original segment. Writes to the slice are visible in the original,
     * and vice versa. The slice's bounds are checked independently.
     */
    private static void memorySlicing() {
        try (Arena arena = Arena.ofConfined()) {
            /* Allocate an array of 20 ints (80 bytes) */
            MemorySegment buffer = arena.allocate(ValueLayout.JAVA_INT, 20);

            /* Fill with data: [1, 2, 3, ..., 20] */
            for (int i = 0; i < 20; i++) {
                buffer.setAtIndex(ValueLayout.JAVA_INT, i, i + 1);
            }

            /*
             * Create a SLICE (view) of elements at indices 5-9.
             *
             * asSlice(byteOffset, byteSize) creates a new MemorySegment
             * that shares the same memory but with different bounds.
             *
             * byte offset = index * element size = 5 * 4 = 20
             * byte size   = count * element size = 5 * 4 = 20
             *
             * The slice is bounded: accessing beyond its 20-byte range
             * throws IndexOutOfBoundsException, even though the original
             * buffer has more data. This prevents accidental out-of-bounds access.
             */
            MemorySegment slice = buffer.asSlice(
                    5L * ValueLayout.JAVA_INT.byteSize(),   // start at element 5
                    5L * ValueLayout.JAVA_INT.byteSize()    // take 5 elements
            );

            StringBuilder sb = new StringBuilder("Slice [5..9]: [");
            for (int i = 0; i < 5; i++) {
                if (i > 0) sb.append(", ");
                /* Index 0 of the slice = index 5 of the original buffer */
                sb.append(slice.getAtIndex(ValueLayout.JAVA_INT, i));
            }
            sb.append("]");
            System.out.println(sb);
        }
    }
}
