#!/usr/bin/env python3
"""Call the C `mathlib` shared library from Python using ctypes (stdlib only).

ctypes ships with CPython, so there is nothing to install. We load the
prebuilt shared library and describe each function's argument/return types so
ctypes can marshal values correctly.
"""

from __future__ import annotations

import ctypes
import platform
from pathlib import Path


class Point(ctypes.Structure):
    """Mirror of `struct { double x; double y; }` from mathlib.h."""

    _fields_ = [("x", ctypes.c_double), ("y", ctypes.c_double)]

    def __repr__(self) -> str:  # nicer printing
        return f"Point(x={self.x}, y={self.y})"


def library_path() -> Path:
    """Locate the shared library built by ../c-library/Makefile."""
    suffix = "dylib" if platform.system() == "Darwin" else "so"
    path = Path(__file__).resolve().parent.parent / "c-library" / "build" / f"libmathlib.{suffix}"
    if not path.exists():
        raise FileNotFoundError(
            f"{path} not found. Build it first: (cd ../c-library && make)"
        )
    return path


def load_mathlib() -> ctypes.CDLL:
    lib = ctypes.CDLL(str(library_path()))

    # Declaring argtypes/restype lets ctypes convert values correctly and
    # catches mismatches instead of silently corrupting the stack.
    lib.add.argtypes = [ctypes.c_int, ctypes.c_int]
    lib.add.restype = ctypes.c_int

    lib.fibonacci.argtypes = [ctypes.c_int]
    lib.fibonacci.restype = ctypes.c_long

    lib.greet.argtypes = [ctypes.c_char_p]
    lib.greet.restype = ctypes.c_void_p  # void* so we keep the raw pointer to free it

    lib.free_string.argtypes = [ctypes.c_void_p]
    lib.free_string.restype = None

    lib.distance.argtypes = [Point, Point]
    lib.distance.restype = ctypes.c_double

    lib.midpoint.argtypes = [Point, Point]
    lib.midpoint.restype = Point

    lib.sum_array.argtypes = [ctypes.POINTER(ctypes.c_int), ctypes.c_size_t]
    lib.sum_array.restype = ctypes.c_long

    return lib


def greet(lib: ctypes.CDLL, name: str) -> str:
    """Call greet() and free the C-allocated string afterwards."""
    ptr = lib.greet(name.encode("utf-8"))
    try:
        # Copy the bytes into a Python str while the C memory is still alive.
        return ctypes.cast(ptr, ctypes.c_char_p).value.decode("utf-8")
    finally:
        lib.free_string(ptr)  # hand ownership back to C


def main() -> None:
    lib = load_mathlib()

    print("== scalars ==")
    print("add(2, 3)        =", lib.add(2, 3))
    print("fibonacci(20)    =", lib.fibonacci(20))

    print("\n== strings ==")
    print('greet("Ada")     =', greet(lib, "Ada"))

    print("\n== structs & arrays ==")
    a, b = Point(0.0, 0.0), Point(3.0, 4.0)
    print("distance(a, b)   =", lib.distance(a, b))
    print("midpoint(a, b)   =", lib.midpoint(a, b))

    numbers = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
    arr = (ctypes.c_int * len(numbers))(*numbers)
    print("sum_array(1..10) =", lib.sum_array(arr, len(numbers)))


if __name__ == "__main__":
    main()
