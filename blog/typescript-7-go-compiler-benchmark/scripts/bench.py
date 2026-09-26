#!/usr/bin/env python3
"""
tsc 6 (JavaScript) vs tsc 7 (Go) on real repositories.

    npm install                          # typescript@7 + @typescript/typescript6 side by side
    python3 scripts/bench.py prepare     # clone + install every target in targets.json
    python3 scripts/bench.py run         # time every configuration
    python3 scripts/bench.py report

The configurations split TypeScript 7's speedup into its two sources:

    tsc6                        the JavaScript compiler, single-threaded by nature
    tsc7 --singleThreaded       the Go port with every form of parallelism off  -> "native" speedup
    tsc7 --checkers 1/2/4/8     type-checking workers (4 is the default)          -> "parallel" speedup

Wall time and peak RSS per run. Peak RSS comes from os.wait4 on the child, so it
works on Linux and macOS without GNU time.
"""
from __future__ import annotations

import argparse
import json
import os
import platform
import re
import shutil
import statistics
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGETS = ROOT / ".targets"
RESULTS = ROOT / "results"
RAW = RESULTS / "raw.json"

# Called by path, never via `npx tsc`: installing @typescript/typescript6 pulls in
# typescript@6 under the alias @typescript/old, and npm links *its* tsc into
# node_modules/.bin over TypeScript 7's. See the README.
TSC6 = ROOT / "node_modules" / "@typescript" / "typescript6" / "bin" / "tsc6"
TSC7 = ROOT / "node_modules" / "typescript" / "bin" / "tsc"

CONFIGS = [
    ("tsc 6", TSC6, []),
    ("tsc 7 --singleThreaded", TSC7, ["--singleThreaded"]),
    ("tsc 7 --checkers 1", TSC7, ["--checkers", "1"]),
    ("tsc 7 --checkers 2", TSC7, ["--checkers", "2"]),
    ("tsc 7 (default: 4 checkers)", TSC7, []),
    ("tsc 7 --checkers 8", TSC7, ["--checkers", "8"]),
]


def load_targets() -> list[dict]:
    return json.loads((ROOT / "targets.json").read_text())


def version(binary: Path) -> str:
    return subprocess.run(["node", str(binary), "--version"], capture_output=True, text=True).stdout.strip()


# ------------------------------------------------------------------ prepare

def cmd_prepare(args: argparse.Namespace) -> None:
    TARGETS.mkdir(exist_ok=True)
    for target in load_targets():
        if args.only and target["name"] not in args.only:
            continue
        path = TARGETS / target["name"]
        if not path.exists():
            print(f"[{target['name']}] cloning {target['repo']} @ {target['ref']}", flush=True)
            subprocess.run(["git", "clone", "--quiet", "--filter=blob:none", target["repo"], str(path)], check=True)
        subprocess.run(["git", "-C", str(path), "checkout", "--quiet", "--force", target["ref"]], check=True)
        subprocess.run(["git", "-C", str(path), "clean", "--quiet", "-fdx", "-e", "node_modules"], check=True)
        for dest, source in target.get("patches", {}).items():
            shutil.copyfile(ROOT / source, path / dest)
            print(f"[{target['name']}] patched {dest} from {source}", flush=True)
        print(f"[{target['name']}] installing: {' '.join(target['install'])}", flush=True)
        subprocess.run(target["install"], cwd=path, check=True, stdout=subprocess.DEVNULL)
        if "post_install" in target:
            print(f"[{target['name']}] {' '.join(target['post_install'])}", flush=True)
            subprocess.run(target["post_install"], cwd=path, check=True, stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL)


# ---------------------------------------------------------------------- run

def run_once(cmd: list[str], cwd: Path) -> dict:
    t0 = time.perf_counter()
    proc = subprocess.Popen(cmd, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    out_chunks = []
    # Drain output as it arrives so a chatty compiler can't block on a full pipe.
    for chunk in iter(lambda: proc.stdout.read(65536), b""):
        out_chunks.append(chunk)
    _, status, rusage = os.wait4(proc.pid, 0)
    wall = time.perf_counter() - t0
    proc.returncode = os.waitstatus_to_exitcode(status)
    output = b"".join(out_chunks).decode(errors="replace")
    # ru_maxrss is KiB on Linux and bytes on macOS.
    rss_mib = rusage.ru_maxrss / (1024 * 1024 if sys.platform == "darwin" else 1024)
    errors = len(re.findall(r"error TS\d+", output))
    return {"wall_s": wall, "rss_mib": rss_mib, "exit": proc.returncode, "errors": errors,
            "cpu_s": rusage.ru_utime + rusage.ru_stime, "tail": output.strip().splitlines()[-3:]}


def measure(binary: Path, extra: list[str], target: dict, runs: int) -> dict:
    cwd = TARGETS / target["name"]
    # The last arg is the project path; extra_projects re-run the same command on other paths.
    projects = [target["args"]] + [target["args"][:-1] + [p] for p in target.get("extra_projects", [])]
    samples = []
    for i in range(runs + 1):  # first run is a warm-up (disk cache, node module compile cache)
        total = {"wall_s": 0.0, "cpu_s": 0.0, "rss_mib": 0.0, "errors": 0, "exit": 0, "tail": []}
        for project_args in projects:
            r = run_once(["node", str(binary), *project_args, *extra], cwd)
            total["wall_s"] += r["wall_s"]
            total["cpu_s"] += r["cpu_s"]
            total["rss_mib"] = max(total["rss_mib"], r["rss_mib"])
            total["errors"] += r["errors"]
            total["exit"] = total["exit"] or r["exit"]
            total["tail"] = r["tail"]
        if i > 0:
            samples.append(total)
    return {
        "wall_s": statistics.median(s["wall_s"] for s in samples),
        "wall_min_s": min(s["wall_s"] for s in samples),
        "cpu_s": statistics.median(s["cpu_s"] for s in samples),
        "rss_mib": statistics.median(s["rss_mib"] for s in samples),
        "errors": samples[-1]["errors"],
        "exit": samples[-1]["exit"],
        "tail": samples[-1]["tail"],
    }


def cmd_run(args: argparse.Namespace) -> None:
    for binary in (TSC6, TSC7):
        if not binary.exists():
            sys.exit(f"missing {binary} - run npm install first")
    raw = json.loads(RAW.read_text()) if RAW.exists() else {}
    raw["_meta"] = {"tsc6": version(TSC6), "tsc7": version(TSC7),
                    "node": subprocess.run(["node", "--version"], capture_output=True, text=True).stdout.strip(),
                    "machine": f"{platform.system()} {platform.machine()}, {os.cpu_count()} CPUs",
                    "runs": args.runs}
    for target in load_targets():
        if args.only and target["name"] not in args.only:
            continue
        if not (TARGETS / target["name"]).exists():
            sys.exit(f"missing .targets/{target['name']} - run prepare first")
        rows = raw.setdefault(target["name"], {})
        for label, binary, extra in CONFIGS:
            r = measure(binary, extra, target, args.runs)
            rows[label] = r
            print(f"[{target['name']}] {label:30} {r['wall_s']:7.2f}s  cpu {r['cpu_s']:7.2f}s  "
                  f"rss {r['rss_mib']:6.0f} MiB  errors {r['errors']}", flush=True)
        RESULTS.mkdir(exist_ok=True)
        RAW.write_text(json.dumps(raw, indent=2))
    cmd_report(args)


# ------------------------------------------------------------------- report

def cmd_report(_: argparse.Namespace) -> None:
    raw = json.loads(RAW.read_text())
    meta = raw["_meta"]
    lines = ["# TypeScript 6 vs 7 - results", "",
             f"`{meta['tsc6']}` vs `{meta['tsc7']}`, Node {meta['node']}, {meta['machine']}, "
             f"median of {meta['runs']} runs after a warm-up.", ""]
    targets = {t["name"]: t for t in load_targets()}
    for name, rows in raw.items():
        if name.startswith("_"):
            continue
        base = rows["tsc 6"]["wall_s"]
        single = rows.get("tsc 7 --singleThreaded", {}).get("wall_s")
        lines += [f"## {name}", "", targets.get(name, {}).get("about", ""), "",
                  "| Configuration | Wall s | vs tsc 6 | vs single-threaded 7 | CPU s | Peak RSS MiB | Errors reported |",
                  "|---|---:|---:|---:|---:|---:|---:|"]
        for label, _, _ in CONFIGS:
            r = rows.get(label)
            if not r:
                continue
            vs_single = f"{single / r['wall_s']:.1f}x" if single and label.startswith("tsc 7") else "-"
            lines.append(f"| `{label}` | {r['wall_s']:.2f} | {base / r['wall_s']:.1f}x | {vs_single} | "
                         f"{r['cpu_s']:.2f} | {r['rss_mib']:.0f} | {r['errors']} |")
        lines.append("")
    (RESULTS / "results.md").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["prepare", "run", "report"])
    parser.add_argument("--runs", type=int, default=5)
    parser.add_argument("--only", nargs="*", help="target names from targets.json")
    args = parser.parse_args()
    {"prepare": cmd_prepare, "run": cmd_run, "report": cmd_report}[args.command](args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
