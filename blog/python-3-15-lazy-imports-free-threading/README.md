# Python 3.15: Lazy Imports and Free-Threading, Measured

Two measurements for the two changes in Python 3.15 that affect everyday code:

1. **[PEP 810](https://peps.python.org/pep-0810/) explicit lazy imports.** How much does a CLI's startup
   drop when `lazy import` defers modules it doesn't need for the command being run? And what do the
   three ways of turning it on actually do?
2. **Free-threading ([PEP 703](https://peps.python.org/pep-0703/), stable ABI in 3.15 via
   [PEP 803](https://peps.python.org/pep-0803/)).** Does CPU-bound pure-Python work scale across threads on
   the `t` builds, and what does the free-threaded build cost on a single thread? The experimental JIT is
   included as a third axis.

Accompanies the post **[Python 3.15 Lazy Imports and Free-Threading: A Benchmark for People Who Write CLIs and Services](https://stevenpg.com/posts/python-3-15-lazy-imports-free-threading/)**.

## Layout

```
fleetctl/app_eager.py          the CLI - a normal import block (source of truth)
fleetctl/app_lazy.py           GENERATED: every import in the block gets the `lazy` keyword
fleetctl/app_lazy_modules.py   GENERATED: same imports, plus a __lazy_modules__ list
threads/scale.py               CPU-bound strong-scaling test (threads and processes)
scripts/setup.sh               uv: installs 3.14, 3.14t, 3.15, 3.15t and a venv for each
scripts/make_variants.py       regenerates the two lazy variants (--check to verify)
scripts/bench.py               the driver: startup, threads, report
data/flights.csv               500 deterministic rows for fleetctl to chew on
results/                       raw.json + results.md from the container run
```

## Quick start

Requires a recent [uv](https://docs.astral.sh/uv/). uv 0.8.17 doesn't list the 3.15 builds, and 0.12.19 does.

```bash
./scripts/setup.sh                    # ~4 interpreters + rich/requests in each venv
python3 scripts/bench.py startup      # lazy imports
python3 scripts/bench.py threads      # free-threading and JIT
python3 scripts/bench.py report       # re-render results/results.md
```

## Lazy imports: fleetctl

`fleetctl` is a small flight-log CLI with a realistic import block: `asyncio`, `csv`, `decimal`,
`email`, `http.client`, `json`, `sqlite3`, `statistics`, `tomllib`, `urllib.request`, `xml.etree`,
`zipfile`, `pathlib`, and two popular third-party packages, `requests` and `rich`.

It has three subcommands:

| Command | Needs |
|---|---|
| `version` | nothing but `argparse` |
| `summary data/flights.csv` | `csv`, `json`, `statistics` |
| `report data/flights.csv` | everything |

### The four variants

| Variant | How | Runs on 3.14? |
|---|---|---|
| `eager` | a normal import block | yes |
| `lazy_keyword` | `lazy import json`, `lazy from pathlib import Path` | **no**, it's a `SyntaxError` |
| `lazy_modules` | `__lazy_modules__ = ["json", ...]` above the unchanged imports | yes, where the list is an unused variable |
| `eager + -X lazy_imports=all` | the eager file run with the global switch | yes, where the unknown `-X` option is ignored |

`lazy_modules` is the migration path for code that must also run on older Pythons: the same file is
eager on 3.14 and lazy on 3.15.

`-X lazy_imports=all` (or `PYTHON_LAZY_IMPORTS=all`) is different in kind. It makes *every* import in
the process lazy, including the imports inside `requests` and `rich`. That's why it loads fewer
modules than the per-file variants even for `report`.

Measured per interpreter × variant × command: median wall time over N process launches (after one
untimed warm-up run), and the number of modules actually imported, counted from `-X importtime`.

## Free-threading: threads/scale.py

A fixed amount of pure-Python work (total Collatz steps for 1..N) split across 1, 2 and 4 threads. On
a GIL build the wall time stays flat. On a free-threaded build it should fall with the thread count. The
same work in a 4-process `ProcessPoolExecutor` is the "what you'd do today" baseline.

The script also reports whether the GIL is *actually* disabled at runtime (a C extension that isn't
free-threading safe can re-enable it on import) and whether the JIT is available and enabled. The
driver runs every interpreter twice, once with `PYTHON_JIT=1`, and drops the JIT row for builds that
don't have one.

## Results

`results/results.md` and `raw.json` are the published run: an M3 Pro MacBook (12 cores) on **3.14.6, 3.14.6t,
3.15.0b2 and 3.15.0b2t**. Note that 3.15 is a pre-release beta there; 3.15.0 final is due October 1. An earlier run in
a shared 4-core Linux container on 3.15.0rc2 showed the same shape.

- `fleetctl version` on 3.15: **~100 ms eager vs 20–26 ms lazy** (381 vs 64 modules), within about 12 ms of the bare
  interpreter. All three ways of enabling laziness land in the same place.
- Module counts are deterministic per platform. macOS loads one more module than Linux for `report` (388/386 against
  387/385).
- macOS startup timings were noisy for the eager rows (standard deviation 13–30 ms, and 111 ms on one row). The lazy
  rows varied by 1–5 ms.
- `-X lazy_imports=all` on `report` loads 268 modules instead of 388. The wall-time gain was within noise on 3.15, 19%
  on 3.15t, and ~23% in the container run.
- Free-threaded builds: 3.6× at 4 threads, the same as 4 processes. There's no measurable single-thread tax on the M3
  (11.64 s vs 11.62 s on 3.15), about 5% in the container, and about 11 ms more startup.
- `PYTHON_JIT=1` on 3.15: the single-threaded loop went from 11.62 s to 5.45 s (-53%). On 3.14 there was no gain.
  The JIT isn't available in the free-threaded builds, so **JIT + 4 processes (1.60 s) beat free-threaded 4 threads
  (3.22 s)**.

## Gotchas found while building this

- **The `lazy` keyword is a hard `SyntaxError` before 3.15.** There's no `__future__` import for it. If
  the file has to run on 3.14, use `__lazy_modules__`.
- **Anything used at module scope reifies immediately.** A lazy-imported name used in a decorator, a
  base class, or a module-level constant is imported at import time anyway. Laziness only pays when the
  first use is inside a function.
- **A missing module fails at first use, not at the import.** `lazy import does_not_exist` succeeds, and the
  `ModuleNotFoundError` is raised when the name is first touched. The traceback does still point at the
  import line. The optional-dependency idiom `try: import x / except ImportError:` can't be lazy anyway:
  `lazy import` inside `try` is a `SyntaxError`.
- **`-X importtime` is the tool for checking.** A lazily imported module only shows up once something
  touches it.
- **An older uv can't see 3.15.** uv 0.8.17 didn't list 3.15.0rc2 at all. Upgrading (to 0.12.19 here) fixed it.
