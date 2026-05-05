package com.stevenpg.jextract;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;

/**
 * =====================================================================
 * JextractExampleMain - Demonstrates What jextract Would Generate
 * =====================================================================
 *
 * WHAT IS JEXTRACT?
 *
 *   jextract is a command-line tool (part of Project Panama) that reads
 *   C header files and AUTOMATICALLY generates Java FFM bindings.
 *
 *   Instead of manually writing:
 *     - FunctionDescriptors for each function
 *     - StructLayouts for each struct
 *     - VarHandles for each field
 *     - MethodHandles for each function
 *
 *   You just run:
 *     jextract --output src/main/java \
 *              --target-package com.stevenpg.generated \
 *              geometry.h
 *
 *   And jextract generates all the Java FFM code for you!
 *
 * WHY JEXTRACT MATTERS:
 *
 *   Real C libraries can have hundreds or thousands of functions and
 *   structs. Writing FFM bindings manually for OpenGL, SQLite, or
 *   libcurl would be a massive undertaking. jextract automates this.
 *
 *   Example: SQLite has ~200 functions and ~30 structs. jextract can
 *   generate all the bindings in seconds.
 *
 * HOW TO USE JEXTRACT:
 *
 *   1. Download jextract from: https://jdk.java.net/jextract/
 *      (It's distributed separately from the JDK)
 *
 *   2. Run it on your C header:
 *      $ jextract --output src/main/java \
 *                 --target-package com.stevenpg.generated \
 *                 --library geometry \
 *                 src/main/native/geometry.h
 *
 *   3. The generated code provides a clean Java API:
 *      // Before (manual FFM - what we do in ffm-example):
 *      MethodHandle circleArea = linker.downcallHandle(
 *          lib.find("circle_area").orElseThrow(),
 *          FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS));
 *      double area = (double) circleArea.invoke(circlePtr);
 *
 *      // After (jextract-generated):
 *      double area = geometry_h.circle_area(circlePtr);
 *      // That's it! jextract generated all the FFM plumbing.
 *
 * THIS EXAMPLE:
 *   Since jextract must be downloaded separately and run as a build step,
 *   this example shows BOTH:
 *     a) The manual FFM approach (what the code looks like without jextract)
 *     b) Comments showing what the jextract-generated code would look like
 *
 *   To actually run jextract, see the README and the generate-bindings.sh script.
 *
 * JEXTRACT INSTALLATION:
 *   <!-- TODO: Verify the exact download URL for jextract compatible with Java 25 -->
 *   Download from: https://jdk.java.net/jextract/
 *   Or build from source: https://github.com/openjdk/jextract
 */
public class JextractExampleMain {

    /* ─────────────────────────────────────────────────────────────────
     * These layouts and handles are what jextract would GENERATE for you.
     * In a real jextract workflow, you'd never write any of this manually.
     * jextract reads geometry.h and produces Java source files containing
     * all of this boilerplate automatically.
     * ───────────────────────────────────────────────────────────────── */

    // ─── Struct layouts (jextract generates these from typedef struct {...}) ───

    static final StructLayout POINT2D_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_DOUBLE.withName("x"),
            ValueLayout.JAVA_DOUBLE.withName("y")
    );

    static final StructLayout CIRCLE_LAYOUT = MemoryLayout.structLayout(
            POINT2D_LAYOUT.withName("center"),       // nested struct!
            ValueLayout.JAVA_DOUBLE.withName("radius")
    );

    static final StructLayout RECTANGLE_LAYOUT = MemoryLayout.structLayout(
            POINT2D_LAYOUT.withName("topLeft"),       // nested struct
            POINT2D_LAYOUT.withName("bottomRight")    // nested struct
    );

    static final StructLayout TRIANGLE_LAYOUT = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(3, POINT2D_LAYOUT).withName("vertices")
    );

    // ─── VarHandles for field access (jextract generates these too) ───

    static final VarHandle POINT2D_X = POINT2D_LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("x"));
    static final VarHandle POINT2D_Y = POINT2D_LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("y"));

    static final VarHandle CIRCLE_RADIUS = CIRCLE_LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("radius"));
    static final VarHandle CIRCLE_CENTER_X = CIRCLE_LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("center"),
            MemoryLayout.PathElement.groupElement("x"));
    static final VarHandle CIRCLE_CENTER_Y = CIRCLE_LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("center"),
            MemoryLayout.PathElement.groupElement("y"));

    public static void main(String[] args) {
        System.out.println("=== jextract Example (Manual FFM Equivalent) ===");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println();
        System.out.println("NOTE: This example manually writes the FFM bindings that");
        System.out.println("jextract would auto-generate from geometry.h.");
        System.out.println("See README.md for how to run jextract to generate them.");
        System.out.println();

        Linker linker = Linker.nativeLinker();

        try {
            String libPath = System.getProperty("java.library.path") + "/libgeometry.so";
            SymbolLookup lib = SymbolLookup.libraryLookup(
                    Path.of(libPath), Arena.ofAuto());

            demonstrateCircle(linker, lib);
            demonstrateRectangle(linker, lib);
            demonstratePointInCircle(linker, lib);
        } catch (Throwable e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Run: ./gradlew :jextract-example:compileNative");
            e.printStackTrace();
        }
    }

    private static void demonstrateCircle(Linker linker, SymbolLookup lib) throws Throwable {
        System.out.println("--- Circle Operations ---");

        /*
         * Manual FFM (what we write without jextract):
         *   MethodHandle circleArea = linker.downcallHandle(
         *       lib.find("circle_area").orElseThrow(),
         *       FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS));
         *
         * With jextract (what gets generated):
         *   // Just call it directly:
         *   double area = geometry_h.circle_area(circleSegment);
         */
        MethodHandle circleArea = linker.downcallHandle(
                lib.find("circle_area").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));

        MethodHandle circlePerimeter = linker.downcallHandle(
                lib.find("circle_perimeter").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));

        try (Arena arena = Arena.ofConfined()) {
            /*
             * Manual: allocate and populate the struct field by field.
             *
             * With jextract: Circle.allocate(arena);
             *                Circle.center(circle, Point2D.allocate(arena));
             *                Circle.radius(circle, 5.0);
             */
            MemorySegment circle = arena.allocate(CIRCLE_LAYOUT);
            CIRCLE_CENTER_X.set(circle, 0L, 0.0);
            CIRCLE_CENTER_Y.set(circle, 0L, 0.0);
            CIRCLE_RADIUS.set(circle, 0L, 5.0);

            double area = (double) circleArea.invoke(circle);
            double perimeter = (double) circlePerimeter.invoke(circle);

            System.out.printf("Circle(center=(0,0), radius=5): area=%.2f, perimeter=%.2f%n",
                    area, perimeter);
        }
    }

    private static void demonstrateRectangle(Linker linker, SymbolLookup lib) throws Throwable {
        System.out.println("--- Rectangle Operations ---");

        MethodHandle rectangleArea = linker.downcallHandle(
                lib.find("rectangle_area").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));

        MethodHandle rectanglePerimeter = linker.downcallHandle(
                lib.find("rectangle_perimeter").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rect = arena.allocate(RECTANGLE_LAYOUT);

            /* Set topLeft = (10, 20), bottomRight = (50, 80) */
            rect.set(ValueLayout.JAVA_DOUBLE, 0, 10.0);   // topLeft.x
            rect.set(ValueLayout.JAVA_DOUBLE, 8, 20.0);   // topLeft.y
            rect.set(ValueLayout.JAVA_DOUBLE, 16, 50.0);  // bottomRight.x
            rect.set(ValueLayout.JAVA_DOUBLE, 24, 80.0);  // bottomRight.y

            double area = (double) rectangleArea.invoke(rect);
            double perimeter = (double) rectanglePerimeter.invoke(rect);

            System.out.printf("Rectangle(topLeft=(10,20), bottomRight=(50,80)): area=%.0f, perimeter=%.0f%n",
                    area, perimeter);
        }
    }

    private static void demonstratePointInCircle(Linker linker, SymbolLookup lib) throws Throwable {
        System.out.println("--- Point-in-Circle Test ---");

        MethodHandle pointInCircle = linker.downcallHandle(
                lib.find("point_in_circle").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        MethodHandle pointDistance = linker.downcallHandle(
                lib.find("point_distance").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        try (Arena arena = Arena.ofConfined()) {
            /* Create a circle centered at (5, 5) with radius 3 */
            MemorySegment circle = arena.allocate(CIRCLE_LAYOUT);
            CIRCLE_CENTER_X.set(circle, 0L, 5.0);
            CIRCLE_CENTER_Y.set(circle, 0L, 5.0);
            CIRCLE_RADIUS.set(circle, 0L, 3.0);

            /* Test several points */
            double[][] testPoints = {{5, 5}, {6, 6}, {8, 8}, {5, 7.9}, {5, 8.1}};
            for (double[] coords : testPoints) {
                MemorySegment point = arena.allocate(POINT2D_LAYOUT);
                POINT2D_X.set(point, 0L, coords[0]);
                POINT2D_Y.set(point, 0L, coords[1]);

                int inside = (int) pointInCircle.invoke(point, circle);

                MemorySegment center = circle.asSlice(0, POINT2D_LAYOUT.byteSize());
                double dist = (double) pointDistance.invoke(point, center);

                System.out.printf("  Point(%.1f, %.1f): %s circle (distance from center: %.2f)%n",
                        coords[0], coords[1],
                        inside == 1 ? "INSIDE" : "OUTSIDE",
                        dist);
            }
        }
        System.out.println();
    }
}
