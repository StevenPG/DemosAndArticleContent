package com.stevenpg.ffm;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

/**
 * Demonstrates FFM's memory management capabilities.
 *
 * FFM provides a structured, safe way to work with off-heap (native) memory.
 * Unlike JNI, where native memory management is entirely manual and error-prone,
 * FFM uses Arenas for deterministic deallocation and MemorySegments for
 * bounds-checked access.
 */
public class MemoryExamples {

    public static void run() {
        System.out.println("--- Memory Operations (FFM MemorySegment & Arena) ---");

        basicMemoryAllocation();
        structLayout();
        memorySlicing();

        System.out.println();
    }

    /**
     * Basic off-heap memory allocation, write, and read.
     * Equivalent to malloc + memset + read + free, but safe.
     */
    private static void basicMemoryAllocation() {
        // Arena.ofConfined() creates a memory scope tied to the current thread
        try (Arena arena = Arena.ofConfined()) {
            // Allocate 10 ints worth of off-heap memory (initialized to zero)
            MemorySegment segment = arena.allocate(ValueLayout.JAVA_INT, 10);

            // Write values
            for (int i = 0; i < 10; i++) {
                segment.setAtIndex(ValueLayout.JAVA_INT, i, i * i);
            }

            // Read values back
            StringBuilder sb = new StringBuilder("Squares: [");
            for (int i = 0; i < 10; i++) {
                if (i > 0) sb.append(", ");
                sb.append(segment.getAtIndex(ValueLayout.JAVA_INT, i));
            }
            sb.append("]");
            System.out.println(sb);
        }
        // Memory is automatically freed when the arena closes
        // Attempting to access 'segment' after this point would throw IllegalStateException
    }

    /**
     * Demonstrates StructLayout - FFM's way to map C structs into Java.
     * This replaces the tedious JNI approach of accessing struct fields
     * through individual JNI calls or manual byte offset calculations.
     *
     * Equivalent C struct:
     *   struct Point3D {
     *       double x;
     *       double y;
     *       double z;
     *   };
     */
    private static void structLayout() {
        // Define the struct layout with named fields
        StructLayout point3dLayout = MemoryLayout.structLayout(
                ValueLayout.JAVA_DOUBLE.withName("x"),
                ValueLayout.JAVA_DOUBLE.withName("y"),
                ValueLayout.JAVA_DOUBLE.withName("z")
        );

        // Get type-safe accessors for each field
        VarHandle xHandle = point3dLayout.varHandle(MemoryLayout.PathElement.groupElement("x"));
        VarHandle yHandle = point3dLayout.varHandle(MemoryLayout.PathElement.groupElement("y"));
        VarHandle zHandle = point3dLayout.varHandle(MemoryLayout.PathElement.groupElement("z"));

        try (Arena arena = Arena.ofConfined()) {
            // Allocate a Point3D struct in native memory
            MemorySegment point = arena.allocate(point3dLayout);

            // Set fields using type-safe VarHandles
            xHandle.set(point, 0L, 1.0);
            yHandle.set(point, 0L, 2.5);
            zHandle.set(point, 0L, 3.7);

            // Read fields back
            double x = (double) xHandle.get(point, 0L);
            double y = (double) yHandle.get(point, 0L);
            double z = (double) zHandle.get(point, 0L);

            System.out.println("Point3D { x=" + x + ", y=" + y + ", z=" + z + " }");
            System.out.println("Point3D size: " + point3dLayout.byteSize() + " bytes");
        }
    }

    /**
     * Demonstrates memory slicing - creating views into larger memory regions.
     * Useful for parsing binary protocols, file formats, or shared memory.
     */
    private static void memorySlicing() {
        try (Arena arena = Arena.ofConfined()) {
            // Allocate a larger buffer
            MemorySegment buffer = arena.allocate(ValueLayout.JAVA_INT, 20);

            // Fill it with data
            for (int i = 0; i < 20; i++) {
                buffer.setAtIndex(ValueLayout.JAVA_INT, i, i + 1);
            }

            // Create a slice (view) of elements 5-9
            // asSlice(byteOffset, byteSize)
            MemorySegment slice = buffer.asSlice(
                    5L * ValueLayout.JAVA_INT.byteSize(),
                    5L * ValueLayout.JAVA_INT.byteSize()
            );

            StringBuilder sb = new StringBuilder("Slice [5..9]: [");
            for (int i = 0; i < 5; i++) {
                if (i > 0) sb.append(", ");
                sb.append(slice.getAtIndex(ValueLayout.JAVA_INT, i));
            }
            sb.append("]");
            System.out.println(sb);
        }
    }
}
