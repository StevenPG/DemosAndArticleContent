#!/bin/bash
# =====================================================================
# generate-bindings.sh - Runs jextract to auto-generate FFM bindings
# =====================================================================
#
# PREREQUISITES:
#   1. Download jextract from: https://jdk.java.net/jextract/
#   2. Extract it and add the bin/ directory to your PATH
#   3. Verify: jextract --version
#
# WHAT THIS SCRIPT DOES:
#   Reads geometry.h and generates Java source files that contain all
#   the FFM boilerplate (FunctionDescriptors, StructLayouts, MethodHandles,
#   VarHandles) needed to call the geometry library from Java.
#
# GENERATED FILES (approximate):
#   com/stevenpg/generated/
#   ├── geometry_h.java        ← Function bindings (circle_area, etc.)
#   ├── Point2D.java           ← Struct layout + accessors for Point2D
#   ├── Circle.java            ← Struct layout + accessors for Circle
#   ├── Rectangle.java         ← Struct layout + accessors for Rectangle
#   ├── Triangle.java          ← Struct layout + accessors for Triangle
#   ├── ShapeType.java         ← Enum constants
#   └── constants$*.java       ← Internal helper classes
#
# USAGE OF GENERATED CODE:
#   // Before jextract (manual FFM):
#   MethodHandle circleArea = linker.downcallHandle(
#       lib.find("circle_area").orElseThrow(),
#       FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS));
#   double area = (double) circleArea.invoke(circlePtr);
#
#   // After jextract (auto-generated):
#   double area = geometry_h.circle_area(circlePtr);
#
#   // Struct allocation:
#   MemorySegment circle = Circle.allocate(arena);
#   Circle.radius(circle, 5.0);
#   Point2D.x(Circle.center(circle), 10.0);

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
HEADER="$SCRIPT_DIR/src/main/native/geometry.h"
OUTPUT="$SCRIPT_DIR/src/main/generated-java"

echo "Running jextract on: $HEADER"
echo "Output directory:    $OUTPUT"
echo ""

# Check if jextract is available
if ! command -v jextract &> /dev/null; then
    echo "ERROR: jextract is not installed or not on PATH."
    echo ""
    echo "To install jextract:"
    echo "  1. Download from: https://jdk.java.net/jextract/"
    echo "  2. Extract the archive"
    echo "  3. Add the bin/ directory to your PATH"
    echo ""
    echo "Example:"
    echo "  export PATH=\$PATH:/path/to/jextract-25/bin"
    echo ""
    # <!-- TODO: Verify the exact jextract download URL for Java 25 -->
    exit 1
fi

mkdir -p "$OUTPUT"

# Run jextract
#
# Options:
#   --output          : Where to write the generated Java source files
#   --target-package  : Java package for the generated classes
#   --library         : Name of the shared library to load at runtime
#                       (jextract adds System.loadLibrary("geometry") to
#                       the generated code automatically)
#   --header-class-name : Name for the class containing function bindings
#                         (defaults to the header filename)
#
# The generated code will call System.loadLibrary("geometry") at runtime,
# so you need libgeometry.so on the java.library.path.

jextract \
    --output "$OUTPUT" \
    --target-package com.stevenpg.generated \
    --library geometry \
    "$HEADER"

echo ""
echo "Success! Generated Java FFM bindings in: $OUTPUT"
echo ""
echo "Generated files:"
find "$OUTPUT" -name "*.java" -type f | sort | while read f; do
    echo "  $(basename "$f")"
done
echo ""
echo "To use the generated code:"
echo "  1. Add $OUTPUT to your Java source path"
echo "  2. Compile the geometry library: gcc -shared -fPIC -o libgeometry.so geometry.c -lm"
echo "  3. Run with: java --enable-native-access=ALL-UNNAMED -Djava.library.path=. YourApp"
