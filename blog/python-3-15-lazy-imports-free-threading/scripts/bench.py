#!/usr/bin/env python3
"""
Benchmark driver. Runs every interpreter in .venvs/ (see scripts/setup.sh).

    python3 scripts/bench.py startup     # lazy imports: CLI wall time per variant x command
    python3 scripts/bench.py threads     # free-threading: strong scaling per interpreter
    python3 scripts/bench.py all
    python3 scripts/bench.py report      # re-render results/results.md from results/raw.json

The driver itself runs on any Python 3.11+; it only launches the interpreters under test.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import statistics
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VENVS = ROOT / ".venvs"
RESULTS = ROOT / "results"
RAW = RESULTS / "raw.json"
DATA = ROOT / "data" / "flights.csv"

# (variant id, script, extra interpreter args)
VARIANTS = [
    ("eager", "app_eager.py", []),
    ("lazy_modules", "app_lazy_modules.py", []),
    ("lazy_keyword", "app_lazy.py", []),
    ("eager + -X lazy_imports=all", "app_eager.py", ["-X", "lazy_imports=all"]),
]
COMMANDS = {
    "version": ["version"],
    "summary": ["summary", str(DATA)],
    "report": ["report", str(DATA), "--quiet"],
}


def interpreters() -> dict[str, Path]:
    found = {}
    for venv in sorted(VENVS.glob("*")):
        python = venv / "bin" / "python"
        if python.exists():
            found[venv.name] = python
    if not found:
        sys.exit("no interpreters in .venvs/ - run scripts/setup.sh first")
    return found


def load_raw() -> dict:
    return json.loads(RAW.read_text()) if RAW.exists() else {}


def save_raw(raw: dict) -> None:
    RESULTS.mkdir(exist_ok=True)
    RAW.write_text(json.dumps(raw, indent=2, sort_keys=True))


def clean_env() -> dict[str, str]:
    env = {k: v for k, v in os.environ.items() if not k.startswith("PYTHON")}
    env["PYTHONDONTWRITEBYTECODE"] = "0"
    return env


# --------------------------------------------------------------- startup

def time_command(python: Path, args: list[str], runs: int, cwd: Path) -> dict:
    env = clean_env()
    # One untimed run warms the OS page cache and writes .pyc files.
    first = subprocess.run([str(python), *args], cwd=cwd, env=env, capture_output=True, text=True)
    if first.returncode != 0:
        return {"error": (first.stderr.strip().splitlines() or ["failed"])[-1]}
    samples = []
    for _ in range(runs):
        t0 = time.perf_counter()
        subprocess.run([str(python), *args], cwd=cwd, env=env, capture_output=True, check=True)
        samples.append((time.perf_counter() - t0) * 1000)
    return {"median_ms": statistics.median(samples), "min_ms": min(samples),
            "stdev_ms": statistics.stdev(samples) if len(samples) > 1 else 0.0}


def modules_loaded(python: Path, args: list[str], cwd: Path) -> int | None:
    """Count modules actually imported, via -X importtime (one line per import)."""
    proc = subprocess.run([str(python), "-X", "importtime", *args], cwd=cwd, env=clean_env(),
                          capture_output=True, text=True)
    if proc.returncode != 0:
        return None
    return sum(1 for line in proc.stderr.splitlines() if line.startswith("import time:") and "|" in line
               and not line.rstrip().endswith("package"))


def cmd_startup(args: argparse.Namespace) -> None:
    raw = load_raw()
    startup = raw.setdefault("startup", {})
    with tempfile.TemporaryDirectory() as tmp:
        cwd = Path(tmp)
        for name, python in interpreters().items():
            baseline = time_command(python, ["-c", "pass"], args.runs, cwd)
            startup.setdefault(name, {})["bare interpreter"] = {"-c pass": baseline}
            for variant, script, extra in VARIANTS:
                row = startup[name].setdefault(variant, {})
                for command, command_args in COMMANDS.items():
                    argv = [*extra, str(ROOT / "fleetctl" / script), *command_args]
                    result = time_command(python, argv, args.runs, cwd)
                    if "error" not in result:
                        result["modules"] = modules_loaded(python, argv, cwd)
                    row[command] = result
                    shown = result.get("error") or f"{result['median_ms']:.1f} ms, {result.get('modules')} modules"
                    print(f"{name:6} {variant:28} {command:8} {shown}", flush=True)
            save_raw(raw)
    cmd_report(args)


# --------------------------------------------------------------- threads

def cmd_threads(args: argparse.Namespace) -> None:
    raw = load_raw()
    threads = raw.setdefault("threads", {})
    counts = [str(n) for n in args.thread_counts]
    for name, python in interpreters().items():
        # The experimental JIT is off unless PYTHON_JIT=1; builds without it are detected and skipped.
        runs = {"default": clean_env(), "PYTHON_JIT=1": clean_env() | {"PYTHON_JIT": "1"}}
        for label, env in runs.items():
            proc = subprocess.run([str(python), str(ROOT / "threads" / "scale.py"), "--work", str(args.work),
                                   "--repeat", str(args.repeat), "--threads", *counts, "--processes"],
                                  env=env, capture_output=True, text=True)
            if proc.returncode != 0:
                print(f"{name} {label}: failed\n{proc.stderr}", file=sys.stderr)
                continue
            result = json.loads(proc.stdout)
            if label != "default" and not result["jit_enabled"]:
                print(f"{name:6} {label}: JIT not available in this build, skipped", flush=True)
                threads.pop(f"{name} ({label})", None)
                continue
            threads[f"{name} ({label})" if label != "default" else name] = result
            print(f"{name:6} {label:13} " + "  ".join(f"{n}t {s:.2f}s" for n, s in result["threads"].items())
                  + f"  gil={result['gil_enabled']}", flush=True)
        save_raw(raw)
    cmd_report(args)


# ---------------------------------------------------------------- report

def fmt(value: float | None, digits: int = 1) -> str:
    return "-" if value is None else f"{value:,.{digits}f}"


def cmd_report(_: argparse.Namespace) -> None:
    raw = load_raw()
    lines = ["# Python 3.15 lazy imports and free-threading - results", ""]
    if "startup" in raw:
        lines += ["## CLI wall time (median ms) and modules imported", ""]
        for name, variants in raw["startup"].items():
            base = variants.get("bare interpreter", {}).get("-c pass", {})
            lines += [f"### {name} (bare interpreter: {fmt(base.get('median_ms'))} ms)", "",
                      "| Variant | " + " | ".join(COMMANDS) + " |",
                      "|---|" + "---|" * len(COMMANDS)]
            for variant, _, _ in VARIANTS:
                cells = []
                for command in COMMANDS:
                    r = variants.get(variant, {}).get(command, {})
                    cells.append("n/a (SyntaxError)" if "SyntaxError" in r.get("error", "")
                                 else (r.get("error") or f"{fmt(r.get('median_ms'))} ms / {r.get('modules')} mods"))
                lines.append(f"| `{variant}` | " + " | ".join(cells) + " |")
            lines.append("")
    if "threads" in raw:
        any_row = next(iter(raw["threads"].values()))
        counts = list(any_row["threads"])
        lines += ["## CPU-bound threads (best of N, seconds; speedup vs 1 thread)", "",
                  f"Work: Collatz steps for 1..{any_row['work']:,}.", "",
                  "| Interpreter | GIL at runtime | " + " | ".join(f"{c} thread(s)" for c in counts)
                  + f" | {counts[-1]} processes |",
                  "|---|---|" + "---|" * (len(counts) + 1)]
        for name, r in raw["threads"].items():
            one = r["threads"][counts[0]]
            cells = [f"{r['threads'][c]:.2f} s ({one / r['threads'][c]:.1f}x)" for c in counts]
            procs = r.get("processes", {}).get(counts[-1])
            lines.append(f"| `{name}` | {'on' if r['gil_enabled'] else 'off'} | " + " | ".join(cells)
                         + f" | {fmt(procs, 2)} s |")
        lines.append("")
    (RESULTS / "results.md").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["startup", "threads", "all", "report"])
    parser.add_argument("--runs", type=int, default=30, help="timed runs per CLI invocation")
    parser.add_argument("--work", type=int, default=2_000_000, help="Collatz range for the threads test")
    parser.add_argument("--repeat", type=int, default=3, help="repeats per thread count (best is kept)")
    parser.add_argument("--thread-counts", type=int, nargs="+", default=[1, 2, 4])
    args = parser.parse_args()
    if args.command in ("startup", "all"):
        cmd_startup(args)
    if args.command in ("threads", "all"):
        cmd_threads(args)
    if args.command == "report":
        cmd_report(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
