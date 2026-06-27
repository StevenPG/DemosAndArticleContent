// Tell Cargo where the prebuilt mathlib shared library lives and how to link
// against it. Run `(cd ../c-library && make)` first so the library exists.

use std::path::PathBuf;

fn main() {
    let lib_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../c-library/build")
        .canonicalize()
        .expect("c-library/build not found — run `make` in ../c-library first");

    // Where to find libmathlib at link time...
    println!("cargo:rustc-link-search=native={}", lib_dir.display());
    // ...and what to link (resolves to libmathlib.dylib / .so).
    println!("cargo:rustc-link-lib=dylib=mathlib");
    // ...and where to find it at *run* time, so we don't need DYLD/LD_LIBRARY_PATH.
    println!("cargo:rustc-link-arg=-Wl,-rpath,{}", lib_dir.display());

    println!("cargo:rerun-if-changed=build.rs");
}
