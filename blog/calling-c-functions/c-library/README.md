# mathlib — the shared C library

A tiny C library that every language demo links against. It is intentionally
small but covers the three cases that make FFI (Foreign Function Interface)
work interesting:

| Function | Signature | What it exercises |
| --- | --- | --- |
| `add` | `int add(int, int)` | scalar in / scalar out |
| `fibonacci` | `long fibonacci(int)` | scalar in / wider scalar out |
| `greet` | `char *greet(const char *)` | string in / **heap-allocated** string out |
| `free_string` | `void free_string(char *)` | releasing memory the C side allocated |
| `distance` | `double distance(Point, Point)` | structs passed **by value** |
| `midpoint` | `Point midpoint(Point, Point)` | struct **returned** by value |
| `sum_array` | `long sum_array(const int *, size_t)` | pointer + length (arrays) |

`Point` is `struct { double x; double y; }`.

## Build

```sh
make          # -> build/libmathlib.dylib (macOS) or build/libmathlib.so (Linux)
make clean
```

The output is a position-independent shared library. Every language demo loads
**this exact file** at runtime (or links against it) — none of them recompile
the C source. Build it first, then run any of the language demos.

## The interesting part: `greet` ownership

`greet` returns a pointer to memory allocated with `malloc` on the C side. The
caller becomes the owner and must hand it back via `free_string`. Watching how
each language models that ownership (a manual free, a `try`-with-resources arena,
RAII, or a deferred call) is the most instructive part of this comparison.
