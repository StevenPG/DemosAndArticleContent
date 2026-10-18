# Python 3.15 lazy imports and free-threading - results

Machine: Darwin arm64, 12 CPUs. Interpreters: 3.14 = 3.14.8, 3.14t = 3.14.8, 3.15 = 3.15.0, 3.15t = 3.15.0.

## CLI wall time (median ms) and modules imported

### 3.14 (bare interpreter: 11.2 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 82.3 ms / 376 mods | 83.2 ms / 376 mods | 97.7 ms / 383 mods |
| `lazy_modules` | 86.4 ms / 376 mods | 89.3 ms / 376 mods | 90.4 ms / 383 mods |
| `lazy_keyword` | n/a (SyntaxError) | n/a (SyntaxError) | n/a (SyntaxError) |
| `eager + -X lazy_imports=all` | 83.5 ms / 376 mods | 85.8 ms / 376 mods | 89.8 ms / 383 mods |

### 3.14t (bare interpreter: 13.0 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 90.3 ms / 376 mods | 91.1 ms / 376 mods | 98.7 ms / 383 mods |
| `lazy_modules` | 91.1 ms / 376 mods | 97.0 ms / 376 mods | 110.4 ms / 383 mods |
| `lazy_keyword` | n/a (SyntaxError) | n/a (SyntaxError) | n/a (SyntaxError) |
| `eager + -X lazy_imports=all` | 91.0 ms / 376 mods | 92.2 ms / 376 mods | 101.7 ms / 383 mods |

### 3.15 (bare interpreter: 11.6 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 81.0 ms / 380 mods | 81.3 ms / 380 mods | 88.2 ms / 386 mods |
| `lazy_modules` | 18.6 ms / 61 mods | 21.5 ms / 80 mods | 86.9 ms / 384 mods |
| `lazy_keyword` | 18.4 ms / 61 mods | 21.5 ms / 80 mods | 90.8 ms / 384 mods |
| `eager + -X lazy_imports=all` | 18.2 ms / 54 mods | 20.1 ms / 67 mods | 68.8 ms / 266 mods |

### 3.15t (bare interpreter: 12.3 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 89.4 ms / 380 mods | 89.0 ms / 380 mods | 96.4 ms / 386 mods |
| `lazy_modules` | 20.9 ms / 61 mods | 24.2 ms / 80 mods | 97.7 ms / 384 mods |
| `lazy_keyword` | 20.5 ms / 61 mods | 23.9 ms / 80 mods | 99.4 ms / 384 mods |
| `eager + -X lazy_imports=all` | 19.6 ms / 54 mods | 22.2 ms / 67 mods | 76.6 ms / 266 mods |

## CPU-bound threads (best of N, seconds; speedup vs 1 thread)

Work: Collatz steps for 1..2,000,000.

| Interpreter | GIL at runtime | 1 thread(s) | 2 thread(s) | 4 thread(s) | 4 processes |
|---|---|---|---|---|---|
| `3.14` | on | 11.27 s (1.0x) | 10.92 s (1.0x) | 10.83 s (1.0x) | 3.09 s |
| `3.14 (PYTHON_JIT=1)` | on | 11.14 s (1.0x) | 11.00 s (1.0x) | 10.88 s (1.0x) | 3.02 s |
| `3.14t` | off | 11.02 s (1.0x) | 5.90 s (1.9x) | 3.00 s (3.7x) | 2.99 s |
| `3.15` | on | 11.29 s (1.0x) | 11.32 s (1.0x) | 11.29 s (1.0x) | 3.18 s |
| `3.15 (PYTHON_JIT=1)` | on | 5.48 s (1.0x) | 5.43 s (1.0x) | 5.44 s (1.0x) | 1.61 s |
| `3.15t` | off | 11.73 s (1.0x) | 6.25 s (1.9x) | 3.22 s (3.6x) | 3.21 s |

