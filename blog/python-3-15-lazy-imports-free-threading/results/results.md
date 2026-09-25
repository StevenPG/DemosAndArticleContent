# Python 3.15 lazy imports and free-threading - results

## CLI wall time (median ms) and modules imported

### 3.14 (bare interpreter: 17.2 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 190.8 ms / 377 mods | 198.9 ms / 377 mods | 212.4 ms / 384 mods |
| `lazy_modules` | 195.4 ms / 377 mods | 196.2 ms / 377 mods | 211.0 ms / 384 mods |
| `lazy_keyword` | n/a (SyntaxError) | n/a (SyntaxError) | n/a (SyntaxError) |
| `eager + -X lazy_imports=all` | 199.0 ms / 377 mods | 208.9 ms / 377 mods | 213.4 ms / 384 mods |

### 3.14t (bare interpreter: 23.0 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 226.3 ms / 377 mods | 231.6 ms / 377 mods | 256.4 ms / 384 mods |
| `lazy_modules` | 235.9 ms / 377 mods | 237.0 ms / 377 mods | 241.3 ms / 384 mods |
| `lazy_keyword` | n/a (SyntaxError) | n/a (SyntaxError) | n/a (SyntaxError) |
| `eager + -X lazy_imports=all` | 231.0 ms / 377 mods | 231.1 ms / 377 mods | 244.0 ms / 384 mods |

### 3.15 (bare interpreter: 18.2 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 196.9 ms / 381 mods | 202.0 ms / 381 mods | 205.8 ms / 387 mods |
| `lazy_modules` | 30.6 ms / 64 mods | 39.1 ms / 83 mods | 201.9 ms / 385 mods |
| `lazy_keyword` | 31.3 ms / 64 mods | 38.8 ms / 83 mods | 211.5 ms / 385 mods |
| `eager + -X lazy_imports=all` | 30.8 ms / 60 mods | 36.4 ms / 73 mods | 158.6 ms / 268 mods |

### 3.15t (bare interpreter: 21.7 ms)

| Variant | version | summary | report |
|---|---|---|---|
| `eager` | 227.0 ms / 381 mods | 224.5 ms / 381 mods | 238.8 ms / 387 mods |
| `lazy_modules` | 39.6 ms / 64 mods | 52.1 ms / 83 mods | 245.1 ms / 385 mods |
| `lazy_keyword` | 40.1 ms / 64 mods | 49.4 ms / 83 mods | 237.0 ms / 385 mods |
| `eager + -X lazy_imports=all` | 38.3 ms / 60 mods | 48.0 ms / 73 mods | 193.6 ms / 268 mods |

## CPU-bound threads (best of N, seconds; speedup vs 1 thread)

Work: Collatz steps for 1..1,500,000.

| Interpreter | GIL at runtime | 1 thread(s) | 2 thread(s) | 4 thread(s) | 4 processes |
|---|---|---|---|---|---|
| `3.14` | on | 11.24 s (1.0x) | 11.52 s (1.0x) | 12.03 s (0.9x) | 3.05 s |
| `3.14 (PYTHON_JIT=1)` | on | 10.62 s (1.0x) | 10.64 s (1.0x) | 11.02 s (1.0x) | 2.87 s |
| `3.14t` | off | 12.08 s (1.0x) | 6.24 s (1.9x) | 3.21 s (3.8x) | 3.26 s |
| `3.15` | on | 10.96 s (1.0x) | 11.17 s (1.0x) | 11.49 s (1.0x) | 2.91 s |
| `3.15 (PYTHON_JIT=1)` | on | 6.41 s (1.0x) | 6.96 s (0.9x) | 6.95 s (0.9x) | 1.81 s |
| `3.15t` | off | 11.48 s (1.0x) | 5.94 s (1.9x) | 3.10 s (3.7x) | 3.09 s |

