# TypeScript 6 vs 7 - results

`Version 6.0.3` vs `Version 7.0.2`, Node v24.16.0, Darwin arm64, 12 CPUs, median of 5 runs after a warm-up.

## coding-steve

Astro 6 blog (this site). Small. `astro sync` generates the content-collection types first, as `astro check` would.

| Configuration | Wall s | vs tsc 6 | vs single-threaded 7 | CPU s | Peak RSS MiB | Errors reported |
|---|---:|---:|---:|---:|---:|---:|
| `tsc 6` | 0.84 | 1.0x | - | 1.91 | 464 | 4 |
| `tsc 7 --singleThreaded` | 0.23 | 3.7x | 1.0x | 0.34 | 192 | 4 |
| `tsc 7 --checkers 1` | 0.13 | 6.4x | 1.7x | 0.50 | 198 | 4 |
| `tsc 7 --checkers 2` | 0.13 | 6.5x | 1.8x | 0.54 | 209 | 4 |
| `tsc 7 (default: 4 checkers)` | 0.12 | 6.8x | 1.9x | 0.59 | 220 | 4 |
| `tsc 7 --checkers 8` | 0.12 | 6.8x | 1.8x | 0.67 | 247 | 4 |

## cesium-spatial

pnpm monorepo, three library packages checked one after another (its own typecheck script does the same). The core package is built first so h3 and s2 can resolve its types.

| Configuration | Wall s | vs tsc 6 | vs single-threaded 7 | CPU s | Peak RSS MiB | Errors reported |
|---|---:|---:|---:|---:|---:|---:|
| `tsc 6` | 1.02 | 1.0x | - | 2.27 | 258 | 0 |
| `tsc 7 --singleThreaded` | 0.26 | 3.9x | 1.0x | 0.35 | 80 | 0 |
| `tsc 7 --checkers 1` | 0.19 | 5.3x | 1.4x | 0.46 | 85 | 0 |
| `tsc 7 --checkers 2` | 0.19 | 5.4x | 1.4x | 0.49 | 87 | 0 |
| `tsc 7 (default: 4 checkers)` | 0.19 | 5.5x | 1.4x | 0.50 | 89 | 0 |
| `tsc 7 --checkers 8` | 0.18 | 5.5x | 1.4x | 0.52 | 90 | 0 |

## playwright

microsoft/playwright, one of the TypeScript team's own published benchmarks (12.8s -> 1.47s). Its generated/ sources come from a build step that isn't run here, so both compilers report the same 12 missing-module errors.

| Configuration | Wall s | vs tsc 6 | vs single-threaded 7 | CPU s | Peak RSS MiB | Errors reported |
|---|---:|---:|---:|---:|---:|---:|
| `tsc 6` | 5.18 | 1.0x | - | 9.95 | 1274 | 12 |
| `tsc 7 --singleThreaded` | 1.65 | 3.1x | 1.0x | 2.22 | 778 | 12 |
| `tsc 7 --checkers 1` | 1.41 | 3.7x | 1.2x | 2.77 | 763 | 12 |
| `tsc 7 --checkers 2` | 1.05 | 4.9x | 1.6x | 3.53 | 896 | 12 |
| `tsc 7 (default: 4 checkers)` | 0.81 | 6.4x | 2.0x | 4.12 | 1102 | 12 |
| `tsc 7 --checkers 8` | 0.75 | 6.9x | 2.2x | 6.47 | 1430 | 12 |

