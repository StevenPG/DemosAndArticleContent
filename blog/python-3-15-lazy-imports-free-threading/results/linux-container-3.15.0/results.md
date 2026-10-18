# Python 3.15 lazy imports and free-threading - results

Machine: Linux x86_64, 4 CPUs. Interpreters: 3.14 = 3.14.8, 3.14t = 3.14.8, 3.15 = 3.15.0, 3.15t = 3.15.0.

## CLI wall time (median ms) and modules imported

### 3.14 (bare interpreter: 15.8 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 246.4 ms / 375 mods | 246.7 ms / 375 mods | 284.3 ms / 382 mods |
| `lazy_modules` | 255.0 ms / 375 mods | 251.6 ms / 375 mods | 274.3 ms / 382 mods |
| `lazy_keyword` | n/a (SyntaxError) | n/a (SyntaxError) | n/a (SyntaxError) |
| `eager + -X lazy_imports=all` | 255.6 ms / 375 mods | 255.5 ms / 375 mods | 278.3 ms / 382 mods |

### 3.14t (bare interpreter: 21.8 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 315.0 ms / 375 mods | 318.9 ms / 375 mods | 354.8 ms / 382 mods |
| `lazy_modules` | 341.1 ms / 375 mods | 315.5 ms / 375 mods | 368.0 ms / 382 mods |
| `lazy_keyword` | n/a (SyntaxError) | n/a (SyntaxError) | n/a (SyntaxError) |
| `eager + -X lazy_imports=all` | 323.2 ms / 375 mods | 315.5 ms / 375 mods | 346.7 ms / 382 mods |

### 3.15 (bare interpreter: 16.1 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 248.9 ms / 379 mods | 255.0 ms / 379 mods | 287.5 ms / 385 mods |
| `lazy_modules` | 35.8 ms / 61 mods | 44.7 ms / 80 mods | 285.4 ms / 383 mods |
| `lazy_keyword` | 38.0 ms / 61 mods | 46.9 ms / 80 mods | 285.5 ms / 383 mods |
| `eager + -X lazy_imports=all` | 35.6 ms / 54 mods | 40.6 ms / 67 mods | 210.9 ms / 266 mods |

### 3.15t (bare interpreter: 23.8 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 317.4 ms / 379 mods | 331.7 ms / 379 mods | 348.8 ms / 385 mods |
| `lazy_modules` | 44.4 ms / 61 mods | 60.9 ms / 80 mods | 364.6 ms / 383 mods |
| `lazy_keyword` | 48.1 ms / 61 mods | 57.2 ms / 80 mods | 368.2 ms / 383 mods |
| `eager + -X lazy_imports=all` | 41.9 ms / 54 mods | 50.0 ms / 67 mods | 251.4 ms / 266 mods |

## CPU-bound threads (best of N, seconds; speedup vs 1 thread)

Work: Collatz steps for 1..2,000,000.

| Interpreter | GIL at runtime | 1 thread(s) | 2 thread(s) | 4 thread(s) | 4 processes |
|---|---|---|---|---|---|
| `3.14` | on | 17.49 s (1.0x) | 18.68 s (0.9x) | 19.89 s (0.9x) | 4.85 s |
| `3.14 (PYTHON_JIT=1)` | on | 18.30 s (1.0x) | 19.70 s (0.9x) | 20.81 s (0.9x) | 4.96 s |
| `3.14t` | off | 18.97 s (1.0x) | 10.09 s (1.9x) | 5.16 s (3.7x) | 5.20 s |
| `3.15` | on | 18.41 s (1.0x) | 19.71 s (0.9x) | 20.01 s (0.9x) | 5.01 s |
| `3.15 (PYTHON_JIT=1)` | on | 10.30 s (1.0x) | 10.79 s (1.0x) | 10.99 s (0.9x) | 2.88 s |
| `3.15t` | off | 19.29 s (1.0x) | 10.25 s (1.9x) | 5.48 s (3.5x) | 5.38 s |

