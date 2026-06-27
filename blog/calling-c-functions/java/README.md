# Java → C with the Foreign Function & Memory API (Panama)

The FFM API (`java.lang.foreign`) is **final since Java 22** — no JNI, no
hand-written C glue, no third-party libraries. This demo runs as a single source
file on Java 22+ (tested on Java 25).

## Run

```sh
(cd ../c-library && make)                       # build libmathlib once
java --enable-native-access=ALL-UNNAMED Demo.java
```

`--enable-native-access=ALL-UNNAMED` suppresses the restricted-native-access
warning; the code runs without it too.

## How it works

- `Linker.nativeLinker()` bridges to the platform C ABI.
- `SymbolLookup.libraryLookup(path, arena)` loads the shared library and finds
  exported symbols.
- Each function becomes a `MethodHandle` built from a `FunctionDescriptor` that
  lists the argument/return `MemoryLayout`s.
- `Arena` owns off-heap memory; `try (Arena arena = Arena.ofConfined())` frees
  everything deterministically at the end of the block.
- The `Point` struct is a `StructLayout`; `VarHandle`s read/write its fields.
- For `greet`, the returned pointer is reinterpreted to read the string, then
  passed to `free_string`.

## Tradeoffs

- ✅ Pure JDK, statically describable, integrates with the GC and `Arena`
  lifetimes; `jextract` can generate the bindings for real libraries.
- ✅ Much faster and safer than JNI; no native compile step for your glue.
- ⚠️ Most verbose of the four — layouts and `MethodHandle` signatures are
  explicit, and `invokeExact` is strict about argument/return types.
