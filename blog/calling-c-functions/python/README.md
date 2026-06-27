# Python → C with `ctypes`

`ctypes` ships with CPython, so this demo has **zero dependencies**. It loads
the prebuilt shared library at runtime and you describe each function's types in
Python.

## Run

```sh
(cd ../c-library && make)   # build libmathlib once
python3 demo.py
```

## How it works

- `ctypes.CDLL(path)` dlopen's the shared library.
- You set `.argtypes` / `.restype` on each function so ctypes marshals values
  and catches type mismatches.
- C structs become `ctypes.Structure` subclasses (`Point`).
- The `greet` result is kept as a raw `c_void_p` so we can copy the bytes out
  **and** pass the pointer back to `free_string` — Python does not own C's heap.

## Tradeoffs

- ✅ Simplest possible: no build step, no codegen, no extra packages.
- ✅ Great for scripting/glue and quick experiments.
- ⚠️ Type declarations are runtime strings, not compiler-checked — a wrong
  `argtype` corrupts the stack silently. (`cffi` adds compile-time checking if
  you want it.)
- ⚠️ Per-call overhead is the highest of the four.
