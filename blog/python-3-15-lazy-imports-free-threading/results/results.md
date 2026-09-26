# Python 3.15 lazy imports and free-threading - results

## CLI wall time (median ms) and modules imported

### 3.14 (bare interpreter: 16.8 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 114.0 ms / 378 mods | 129.5 ms / 378 mods | 112.7 ms / 385 mods |
| `lazy_modules` | 95.8 ms / 378 mods | 91.3 ms / 378 mods | 96.5 ms / 385 mods |
| `lazy_keyword` | n/a (SyntaxError) | n/a (SyntaxError) | n/a (SyntaxError) |
| `eager + -X lazy_imports=all` | 88.8 ms / 378 mods | 90.4 ms / 378 mods | 115.7 ms / 385 mods |

### 3.14t (bare interpreter: 17.9 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 133.6 ms / 378 mods | 141.5 ms / 378 mods | 112.8 ms / 385 mods |
| `lazy_modules` | 97.3 ms / 378 mods | 97.9 ms / 378 mods | 105.3 ms / 385 mods |
| `lazy_keyword` | n/a (SyntaxError) | n/a (SyntaxError) | n/a (SyntaxError) |
| `eager + -X lazy_imports=all` | 96.5 ms / 378 mods | 97.8 ms / 378 mods | 104.5 ms / 385 mods |

### 3.15 (bare interpreter: 14.0 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 99.9 ms / 381 mods | 112.3 ms / 381 mods | 119.8 ms / 388 mods |
| `lazy_modules` | 19.8 ms / 64 mods | 28.5 ms / 83 mods | 117.2 ms / 386 mods |
| `lazy_keyword` | 25.1 ms / 64 mods | 25.8 ms / 83 mods | 137.7 ms / 386 mods |
| `eager + -X lazy_imports=all` | 26.3 ms / 60 mods | 23.9 ms / 73 mods | 116.9 ms / 268 mods |

### 3.15t (bare interpreter: 25.2 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 151.4 ms / 381 mods | 109.0 ms / 381 mods | 115.1 ms / 388 mods |
| `lazy_modules` | 33.1 ms / 64 mods | 38.7 ms / 83 mods | 156.0 ms / 386 mods |
| `lazy_keyword` | 23.5 ms / 64 mods | 27.7 ms / 83 mods | 153.7 ms / 386 mods |
| `eager + -X lazy_imports=all` | 24.7 ms / 60 mods | 36.5 ms / 73 mods | 93.6 ms / 268 mods |

## CPU-bound threads (best of N, seconds; speedup vs 1 thread)

Work: Collatz steps for 1..2,000,000.

| Interpreter | GIL at runtime | 1 thread(s) | 2 thread(s) | 4 thread(s) | 4 processes |
|---|---|---|---|---|---|
| `3.14` | on | 12.19 s (1.0x) | 11.78 s (1.0x) | 13.49 s (0.9x) | 3.22 s |
| `3.14 (PYTHON_JIT=1)` | on | 13.17 s (1.0x) | 11.77 s (1.1x) | 11.47 s (1.1x) | 3.10 s |
| `3.14t` | off | 10.74 s (1.0x) | 5.77 s (1.9x) | 3.01 s (3.6x) | 2.97 s |
| `3.15` | on | 11.62 s (1.0x) | 11.55 s (1.0x) | 11.53 s (1.0x) | 3.19 s |
| `3.15 (PYTHON_JIT=1)` | on | 5.45 s (1.0x) | 5.42 s (1.0x) | 5.51 s (1.0x) | 1.60 s |
| `3.15t` | off | 11.64 s (1.0x) | 6.30 s (1.8x) | 3.22 s (3.6x) | 3.26 s |

