# Rust → C with `extern "C"`

Rust speaks the C ABI natively. We declare the functions in an
`unsafe extern "C"` block (Rust 2024 edition) and a small `build.rs` wires the
linker to the prebuilt shared library. **No third-party crates.**

## Run

```sh
(cd ../c-library && make)   # build libmathlib once
cargo run
```

## How it works

- `build.rs` emits `cargo:rustc-link-search` / `rustc-link-lib` so the linker
  finds `libmathlib`, plus an `-rpath` so it loads at runtime without
  `DYLD_LIBRARY_PATH`/`LD_LIBRARY_PATH`.
- `#[repr(C)]` on `Point` guarantees the field layout matches C.
- Calls live in `unsafe` blocks — the compiler can't verify the foreign side.
- `greet_safe` wraps the raw call: copy out a `String` via `CStr`, then return
  the buffer to C with `free_string`. This is the idiomatic "safe wrapper around
  unsafe FFI" pattern.

## Tradeoffs

- ✅ Zero-overhead, no GC, compile-time-checked struct layouts.
- ✅ The type system pushes you toward safe wrappers around the `unsafe` core.
- ⚠️ You manage memory and ownership explicitly across the boundary.
- ℹ️ For binding real libraries, `bindgen` generates these `extern` blocks for
  you; `libloading` is the alternative if you want runtime `dlopen` instead of
  link-time binding.
