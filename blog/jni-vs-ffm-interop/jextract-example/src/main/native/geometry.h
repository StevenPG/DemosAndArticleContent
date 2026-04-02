/*
 * =====================================================================
 * geometry.h - A Sample C Header for jextract
 * =====================================================================
 *
 * This header file is designed to be processed by jextract, which will
 * automatically generate Java FFM bindings for every:
 *   - Function declaration
 *   - Struct definition
 *   - Enum definition
 *   - Constant (#define)
 *   - Typedef
 *
 * jextract reads this header and produces Java source files that contain:
 *   - MethodHandle definitions for each function
 *   - MemoryLayout definitions for each struct
 *   - Constants for each #define and enum value
 *   - Helper methods for allocating and accessing structs
 *
 * This eliminates the need to manually write FunctionDescriptors,
 * StructLayouts, and VarHandles like we did in the ffm-example project.
 *
 * REAL-WORLD USE CASES:
 *   jextract is especially valuable for large C APIs where manually
 *   writing FFM bindings would take weeks:
 *     - OpenGL (hundreds of functions)
 *     - SQLite (dozens of functions + complex structs)
 *     - libcurl (many functions + nested structs + callbacks)
 *     - POSIX APIs (thousands of functions)
 */

#ifndef GEOMETRY_H
#define GEOMETRY_H

#include <math.h>

/* ─── Constants ─── */

/* jextract generates: static final double PI = 3.14159265358979323846; */
#define PI 3.14159265358979323846

/* jextract generates: static final int MAX_POLYGON_VERTICES = 100; */
#define MAX_POLYGON_VERTICES 100

/* ─── Enums ─── */

/*
 * jextract generates a class with static final int constants:
 *   static final int SHAPE_CIRCLE = 0;
 *   static final int SHAPE_RECTANGLE = 1;
 *   static final int SHAPE_TRIANGLE = 2;
 */
typedef enum {
    SHAPE_CIRCLE = 0,
    SHAPE_RECTANGLE = 1,
    SHAPE_TRIANGLE = 2
} ShapeType;

/* ─── Structs ─── */

/*
 * jextract generates a complete StructLayout + VarHandles:
 *
 *   StructLayout Point2D = MemoryLayout.structLayout(
 *       JAVA_DOUBLE.withName("x"),
 *       JAVA_DOUBLE.withName("y")
 *   );
 *
 * Plus helper methods:
 *   static MemorySegment allocate(SegmentAllocator allocator) { ... }
 *   static double x(MemorySegment struct) { ... }
 *   static void x(MemorySegment struct, double value) { ... }
 */
typedef struct {
    double x;
    double y;
} Point2D;

/*
 * jextract handles nested structs automatically:
 *
 *   StructLayout Circle = MemoryLayout.structLayout(
 *       Point2D.layout().withName("center"),  // nested!
 *       JAVA_DOUBLE.withName("radius")
 *   );
 */
typedef struct {
    Point2D center;
    double radius;
} Circle;

typedef struct {
    Point2D topLeft;
    Point2D bottomRight;
} Rectangle;

typedef struct {
    Point2D vertices[3];
} Triangle;

/* ─── Functions ─── */

/*
 * For each function, jextract generates:
 *   1. A MethodHandle with the correct FunctionDescriptor
 *   2. A Java wrapper method with proper parameter types
 *
 * For example, circle_area becomes:
 *   static double circle_area(MemorySegment circle) { ... }
 */

/* Calculate the area of a circle */
double circle_area(const Circle *c);

/* Calculate the perimeter (circumference) of a circle */
double circle_perimeter(const Circle *c);

/* Calculate the area of a rectangle */
double rectangle_area(const Rectangle *r);

/* Calculate the perimeter of a rectangle */
double rectangle_perimeter(const Rectangle *r);

/* Calculate the area of a triangle using the cross product method */
double triangle_area(const Triangle *t);

/* Calculate the distance between two points */
double point_distance(const Point2D *a, const Point2D *b);

/* Translate (move) a point by dx, dy. Returns a new point. */
Point2D point_translate(const Point2D *p, double dx, double dy);

/* Check if a point is inside a circle (returns 1 if true, 0 if false) */
int point_in_circle(const Point2D *p, const Circle *c);

#endif /* GEOMETRY_H */
