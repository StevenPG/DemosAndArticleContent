# Calling C functions from Python, Java, Rust & Go

One small C library, four languages, the same set of calls. This project shows
how each modern language loads and executes a shared C library — and how the
ergonomics, safety, and ceremony differ.

```
calling-c-functions/
├── c-library/      # the shared C library every demo links against (mathlib)
├── python/         # ctypes        (stdlib, no deps)
├── java/           # FFM / Panama  (JDK 22+, no deps)
├── rust/           # extern "C"    (build.rs linking, no crates)
├── golang/         # cgo
└── run-all.sh      # build C lib + run all four demos
```

Every demo loads the **same prebuilt** `libmathlib.dylib` / `libmathlib.so`.
None of them recompile the C source — they link against or `dlopen` the shared
object, which is the realistic "I have a C library, call it from X" scenario.

## The C library

`mathlib` exposes seven functions chosen to exercise the parts of FFI that
actually differ between languages — scalars, heap-allocated strings (with
ownership handed back to C), and structs/arrays. See
[c-library/README.md](c-library/README.md) for the full table.

## Quick start

```sh
./run-all.sh
```

Or build once and run individually:

```sh
make -C c-library                                          # build the shared lib

python3 python/demo.py                                     # Python
( cd java   && java --enable-native-access=ALL-UNNAMED Demo.java )  # Java
( cd rust   && cargo run )                                 # Rust
( cd golang && go run . )                                  # Go
```

All four print the identical results:

```
add(2, 3)        = 5
fibonacci(20)    = 6765
greet("Ada")     = Hello, Ada!
distance(a, b)   = 5
midpoint(a, b)   = Point(x=1.5, y=2.0)
sum_array(1..10) = 55
```

## Toolchain versions used

| | Version |
| --- | --- |
| C compiler | clang / gcc (any C11) |
| Python | 3.x (`ctypes` is stdlib) |
| Java | 22+ (tested on 25) — FFM is final in 22 |
| Rust | 2024 edition (tested on 1.96) |
| Go | 1.x with cgo enabled (tested on 1.26) |

## How they compare

| | Mechanism | Extra deps | Binding style | Memory across boundary | Speed | Ceremony |
| --- | --- | --- | --- | --- | --- | --- |
| **Python** | `ctypes` (runtime `dlopen`) | none (stdlib) | runtime type decls | manual `c_void_p` + `free_string` | slowest | lowest |
| **Java** | FFM / Panama | none (JDK 22+) | `MethodHandle` + `MemoryLayout` | `Arena` (scoped, auto-freed) | fast | highest |
| **Rust** | `extern "C"` + linker | none (build.rs) | compile-time `extern` block | explicit, `unsafe` + safe wrappers | fastest | medium |
| **Go** | cgo | none (toolchain) | C in special comment | `C.CString`/`C.GoString` + `defer` | fast (boundary cost) | low/medium |

### The most instructive difference: string ownership

`greet` returns a `malloc`'d `char*` that the caller must `free`. Each language
models that differently:

- **Python** — keep the raw pointer (`c_void_p`), copy the bytes, call
  `free_string` in a `finally`.
- **Java** — read the string from the returned `MemorySegment`, then call the
  `free_string` handle; surrounding `Arena` frees the *input* memory.
- **Rust** — copy into an owned `String` via `CStr`, then `free_string`, all
  hidden behind a safe wrapper function.
- **Go** — `defer C.free_string(ptr)` after copying with `C.GoString`.

### Picking one

- **Glue / scripting / quick experiments** → Python `ctypes`.
- **On the JVM, want no native build step and GC-friendly lifetimes** → Java FFM.
- **Want zero overhead and compile-time guarantees** → Rust.
- **Already in Go and want C to feel native** → cgo (mind cross-compilation).

## Notes

- The shared library is built position-independent and the Rust/Go demos bake an
  `rpath` so the library is found at runtime without setting
  `DYLD_LIBRARY_PATH` / `LD_LIBRARY_PATH`.
- `Point` is `struct { double x; double y; }`; structs are passed and returned
  **by value** to show value marshalling in each language.
- For binding *real* libraries you'd typically reach for code generators
  (`jextract` for Java, `bindgen` for Rust) rather than hand-writing signatures.
