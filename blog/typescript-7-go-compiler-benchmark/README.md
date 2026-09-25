# TypeScript 7's Go Compiler: Where the 10x Comes From

TypeScript 7.0 (GA July 2026) replaced the JavaScript compiler with a line-by-line port to Go. Microsoft
reports 8–12× faster type-checking on large codebases. This project reproduces that on three real
repositories and **splits the speedup into its two sources**:

- **Native code.** `tsc 7 --singleThreaded` against `tsc 6`: the same algorithm, compiled Go instead of
  JIT-compiled JavaScript, no parallelism.
- **Parallelism.** `tsc 7` with 1, 2, 4 (the default) and 8 type-checking workers (`--checkers`), against
  `--singleThreaded`: what shared-memory concurrency in Go adds on top.

Accompanies the post **[TypeScript 7 Is Written in Go: Where the 10x Actually Comes From](https://stevenpg.com/posts/typescript-7-go-compiler-benchmark/)**.

## Layout

```
package.json                 typescript@7.0.2 + @typescript/typescript6@6.0.2, side by side
targets.json                 the repositories, pinned to a commit, with their install steps
patches/                     tsconfig migrations a target needs before TypeScript 7 will accept it
scripts/bench.py             prepare (clone + install), run (time everything), report
results/                     raw.json + results.md from the container run
```

## Run it

Requires Node 20+, Python 3.11+ and git.

```bash
npm install
python3 scripts/bench.py prepare       # clones into .targets/, installs each target's deps
python3 scripts/bench.py run           # 5 timed runs per configuration, after a warm-up
python3 scripts/bench.py report
```

`--only playwright` and `--runs N` narrow a run.

## Targets

| Target | What it is | Command |
|---|---|---|
| `coding-steve` | This blog: Astro 6, small | `tsc --noEmit -p .` after `astro sync` |
| `cesium-spatial` | My pnpm monorepo, three library packages | `tsc --noEmit -p packages/{core,h3,s2}`, summed |
| `playwright` | microsoft/playwright, one of Microsoft's own published benchmarks | `tsc --noEmit -p .` |

Every configuration must report the **same number of errors** as `tsc 6`. The results table includes
the count, so a faster run that checked less would show up.

## Measured

Median wall time over 5 runs after one warm-up, total CPU time (user + sys), and peak RSS for each
configuration. Peak RSS comes from `os.wait4` on the child process, so no GNU `time` is needed and the
same code works on macOS.

## Gotchas found while building this

- **`npx tsc` may run TypeScript 6.** `@typescript/typescript6` depends on `typescript@^6` under the
  npm alias `@typescript/old`. With npm 10.9.7, installing both packages links `@typescript/old`'s
  `tsc` into `node_modules/.bin`, *over* TypeScript 7's. `npx tsc --version` prints `6.0.3`. It
  reproduced on every clean install. The harness calls both compilers by path. In your own project,
  check `npx tsc --version` after adding the compat package.
- **TypeScript 7 rejects `baseUrl`, and TypeScript 6 already errors on it.** This blog's tsconfig used
  `baseUrl: "src"` with bare `paths` like `"@components/*": ["components/*"]`.
  - `tsc 6` fails with TS5101: `baseUrl` is deprecated.
  - `tsc 7` fails with TS5090: non-relative paths aren't allowed.
  - The fix is to drop `baseUrl` and make every path relative to the tsconfig (`"./src/components/*"`).
  `patches/coding-steve.tsconfig.json` is that migration.
- **Other removed options are hard errors now.** `target: es5`, `moduleResolution: node`/`node10`/`classic`,
  `module: amd`/`umd`/`system`/`none`, `downlevelIteration`, and `esModuleInterop: false` all fail in
  TypeScript 7. `strict` defaults to `true` and `types` to `[]`.
- **No programmatic API yet.** Tools that `import ts from "typescript"` (some ESLint setups, ts-morph,
  custom transformers) need TypeScript 6 until the API lands in 7.1. That's what the compat package
  is for.

## Results

`results/results.md` is a run on a shared 4-core x86_64 cloud container, 3 runs per configuration. **Read the
timings as shape only.** Parallel speedups are capped at 4 cores there. The blog post's numbers come from a
re-run on dedicated hardware.

The shape:

| Target | tsc 6 | tsc 7 single-threaded | tsc 7 default | Native | Parallel | Total |
|---|---:|---:|---:|---:|---:|---:|
| coding-steve | 2.37 s | 0.56 s | 0.52 s | 4.2× | 1.1× | 4.6× |
| cesium-spatial (3 packages) | 3.11 s | 0.65 s | 0.55 s | 4.8× | 1.2× | 5.7× |
| playwright | 16.48 s | 4.77 s | 3.54 s | 3.5× | 1.35× | 4.7× |

- **Most of the speedup is native code, not threads.** Even with parallelism off, TypeScript 7 was 3.5–4.8×
  faster. On the two small targets the difference is mostly startup: Node loading and JIT-warming a large
  compiler, against a Go binary that's ready in milliseconds.
- **More checkers means more memory, and past the core count, slower.** On playwright, peak RSS went from 751 MiB
  single-threaded to 1,123 MiB at 4 checkers and 1,452 MiB at 8. On 4 cores, 8 checkers was slower than 4. Size
  `--checkers` to your CI runner, not above it.
- **`tsc 6` burns about 2× its wall time in CPU** (Node's GC and JIT threads). `tsc 7 --singleThreaded` burns about
  1.2×.
- **Identical diagnostics.** Every configuration reported the same errors as `tsc 6`: 4 in this blog (real ones),
  0 in cesium-spatial, and 12 in playwright (missing `generated/` modules from a build step that isn't run here).
- `tsc6 --version` prints `6.0.3` even though the compat package is `6.0.2`, because it runs the aliased
  `typescript@^6` underneath.

`--checkers` scaling on playwright:

| Configuration | Wall s | CPU s | Peak RSS MiB |
|---|---:|---:|---:|
| `--singleThreaded` | 4.77 | 5.78 | 751 |
| `--checkers 1` | 4.55 | 7.25 | 730 |
| `--checkers 2` | 3.65 | 9.23 | 886 |
| default (4) | 3.54 | 12.90 | 1,123 |
| `--checkers 8` | 4.52 | 17.22 | 1,452 |
