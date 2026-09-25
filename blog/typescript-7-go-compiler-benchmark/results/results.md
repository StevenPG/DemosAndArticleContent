# TypeScript 6 vs 7 - results

`Version 6.0.3` vs `Version 7.0.2`, Node v22.22.2, Linux x86_64, 4 CPUs, median of 3 runs after a warm-up.

## coding-steve

Astro 6 blog (this site). Small. `astro sync` generates the content-collection types first, as `astro check` would.

| Configuration | Wall s | vs tsc 6 | vs single-threaded 7 | CPU s | Peak RSS MiB | Errors reported |
|---|---:|---:|---:|---:|---:|---:|
| `tsc 6` | 2.37 | 1.0x | - | 5.30 | 365 | 4 |
| `tsc 7 --singleThreaded` | 0.56 | 4.2x | 1.0x | 0.77 | 178 | 4 |
| `tsc 7 --checkers 1` | 0.50 | 4.8x | 1.1x | 1.34 | 181 | 4 |
| `tsc 7 --checkers 2` | 0.50 | 4.7x | 1.1x | 1.42 | 194 | 4 |
| `tsc 7 (default: 4 checkers)` | 0.52 | 4.6x | 1.1x | 1.66 | 211 | 4 |
| `tsc 7 --checkers 8` | 0.59 | 4.0x | 1.0x | 1.87 | 230 | 4 |

## cesium-spatial

pnpm monorepo, three library packages checked one after another (its own typecheck script does the same). The core package is built first so h3 and s2 can resolve its types.

| Configuration | Wall s | vs tsc 6 | vs single-threaded 7 | CPU s | Peak RSS MiB | Errors reported |
|---|---:|---:|---:|---:|---:|---:|
| `tsc 6` | 3.11 | 1.0x | - | 6.66 | 210 | 0 |
| `tsc 7 --singleThreaded` | 0.65 | 4.8x | 1.0x | 0.83 | 73 | 0 |
| `tsc 7 --checkers 1` | 0.57 | 5.5x | 1.2x | 1.19 | 74 | 0 |
| `tsc 7 --checkers 2` | 0.58 | 5.4x | 1.1x | 1.31 | 76 | 0 |
| `tsc 7 (default: 4 checkers)` | 0.55 | 5.7x | 1.2x | 1.28 | 78 | 0 |
| `tsc 7 --checkers 8` | 0.55 | 5.7x | 1.2x | 1.27 | 79 | 0 |

## playwright

microsoft/playwright, one of the TypeScript team's own published benchmarks (12.8s -> 1.47s). Its generated/ sources come from a build step that isn't run here, so both compilers report the same 12 missing-module errors.

| Configuration | Wall s | vs tsc 6 | vs single-threaded 7 | CPU s | Peak RSS MiB | Errors reported |
|---|---:|---:|---:|---:|---:|---:|
| `tsc 6` | 16.48 | 1.0x | - | 30.95 | 1158 | 12 |
| `tsc 7 --singleThreaded` | 4.77 | 3.5x | 1.0x | 5.78 | 751 | 12 |
| `tsc 7 --checkers 1` | 4.55 | 3.6x | 1.0x | 7.25 | 730 | 12 |
| `tsc 7 --checkers 2` | 3.65 | 4.5x | 1.3x | 9.23 | 886 | 12 |
| `tsc 7 (default: 4 checkers)` | 3.54 | 4.7x | 1.3x | 12.90 | 1123 | 12 |
| `tsc 7 --checkers 8` | 4.52 | 3.6x | 1.1x | 17.22 | 1452 | 12 |

