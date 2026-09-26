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

`results/results.md` and `raw.json` are the published run: an M3 Pro MacBook (12 cores), Node 24.16, TypeScript
6.0.3 against 7.0.2, median of 5 runs after a warm-up.

| Target | tsc 6 | tsc 7 single-threaded | tsc 7 default | Native | Parallel | Total |
|---|---:|---:|---:|---:|---:|---:|
| coding-steve | 0.84 s | 0.23 s | 0.12 s | 3.7× | 1.9× | 6.8× |
| cesium-spatial (3 packages) | 1.02 s | 0.26 s | 0.19 s | 3.9× | 1.4× | 5.5× |
| playwright | 5.18 s | 1.65 s | 0.81 s | 3.1× | 2.0× | 6.4× |

- **Native code accounts for 3–4×** (`tsc 6` against `tsc 7 --singleThreaded`), and it was similar on a 4-core cloud
  container (3.5–4.8×). Parallelism adds 1.4–2× at the default 4 checkers, growing with codebase size.
- **`--checkers 1` isn't single-threaded.** Parsing and binding still run in parallel, so it was 1.2–1.7× faster than
  `--singleThreaded`.
- **8 checkers vs 4 on playwright:** 7% faster, 30% more peak RSS (1,430 MiB, more than tsc 6's 1,274), 57% more CPU.
- **Total CPU drops too:** 4.1 s at 4 checkers against tsc 6's 10.0 s on playwright.
- **Identical diagnostics** from every configuration on every target (4 / 0 / 12 errors).
- **Exit code:** with type errors under `--noEmit`, `tsc 6` exits **2** and `tsc 7` exits **1**, on every target and
  both machines.
- `tsc6 --version` prints `6.0.3` even though the compat package is `6.0.2`, because it runs the aliased
  `typescript@^6` underneath.
