# Go → C with cgo

cgo lets you write C in a comment above `import "C"` and call it directly. The
`#cgo` directives point at the prebuilt shared library and bake in an rpath.

## Run

```sh
(cd ../c-library && make)   # build libmathlib once
go run .
```

(If cgo is ever disabled in your environment, set `CGO_ENABLED=1`.)

## How it works

- The C `#include`s and `#cgo CFLAGS/LDFLAGS` live in the comment directly above
  `import "C"`. `${SRCDIR}` expands to this package's directory.
- C names are referenced through the pseudo-package `C`: `C.add`, `C.Point`,
  `C.size_t`, etc.
- `C.CString` allocates a C string (freed with `defer C.free(...)`); `C.GoString`
  copies a C string into a Go string.
- For `greet`, `defer C.free_string(ptr)` returns the C-allocated buffer.

## Tradeoffs

- ✅ Very ergonomic: C feels almost native, structs map directly to `C.Point`.
- ✅ No separate binding/codegen step.
- ⚠️ cgo adds build complexity and call overhead (each call crosses the Go⇄C
  boundary and stack), and it complicates cross-compilation.
- ⚠️ Memory passed across the boundary has rules (Go pointers into C are
  restricted); copying with `C.CString`/`C.GoString` keeps it simple here.
