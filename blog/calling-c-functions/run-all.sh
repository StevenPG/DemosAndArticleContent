#!/usr/bin/env bash
# Build the C library once, then run every language demo against it.
set -euo pipefail

cd "$(dirname "$0")"

hr() { printf '\n========== %s ==========\n' "$1"; }

hr "Building C library"
make -C c-library

hr "Python (ctypes)"
python3 python/demo.py

hr "Java (FFM / Panama)"
( cd java && java --enable-native-access=ALL-UNNAMED Demo.java )

hr "Rust (extern \"C\")"
( cd rust && cargo run --quiet )

hr "Go (cgo)"
( cd golang && go run . )

hr "Done"
