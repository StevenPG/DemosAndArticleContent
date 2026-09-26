#!/usr/bin/env python3
"""
What does rewriting a bloated table cost the application that is writing to it?

For each method - VACUUM FULL, REPACK, REPACK (CONCURRENTLY) - this rebuilds
the same bloated table, starts a pgbench writer against it, runs the method
mid-load, and reads pgbench's per-second aggregate log to see what the writer
experienced while it ran.

    docker compose up -d
    python3 scripts/repack_bench.py run                # all methods, 2M rows
    python3 scripts/repack_bench.py run --rows 5000000 --clients 16
    python3 scripts/repack_bench.py report

Only needs Docker and Python 3.11+; psql and pgbench run inside the container.
"""
from __future__ import annotations

import argparse
import json
import statistics
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESULTS = ROOT / "results"
RAW = RESULTS / "raw.json"
CONTAINER = "pg19-repack"
LOG_PREFIX = "/tmp/pgbench_agg"

METHODS = {
    "VACUUM FULL": "VACUUM (FULL) positions",
    "REPACK": "REPACK positions",
    "REPACK (CONCURRENTLY)": "REPACK (CONCURRENTLY) positions",
}


def sh(*args: str, check: bool = True, **kw) -> subprocess.CompletedProcess:
    return subprocess.run(list(args), check=check, text=True, capture_output=True, **kw)


def psql(sql: str, *extra: str) -> str:
    out = sh("docker", "exec", "-i", CONTAINER, "psql", "-U", "postgres", "-X", "-q", "-A", "-t",
             "-v", "ON_ERROR_STOP=1", *extra, input=sql)
    return out.stdout.strip()


def table_size_mb() -> dict:
    row = psql("SELECT pg_relation_size('positions'), pg_indexes_size('positions'), count(*) FROM positions;")
    heap, idx, rows = (int(x) for x in row.split("|"))
    return {"heap_mb": heap / 2**20, "indexes_mb": idx / 2**20, "rows": rows}


def wait_ready() -> None:
    for _ in range(120):
        if sh("docker", "exec", CONTAINER, "pg_isready", "-q", check=False).returncode == 0:
            return
        time.sleep(1)
    sys.exit("postgres never became ready - is `docker compose up -d` running?")


def start_writer(clients: int, maxid: int, seconds: int) -> subprocess.Popen:
    sh("docker", "exec", CONTAINER, "sh", "-c", f"rm -f {LOG_PREFIX}*")
    # pgbench buffers its aggregate log and loses the buffer if it is killed,
    # so the writer always runs for a fixed window and exits on its own.
    return subprocess.Popen(
        ["docker", "exec", CONTAINER, "pgbench", "-U", "postgres", "-n", "-c", str(clients), "-j", "2",
         "-T", str(seconds), "-f", "/sql/workload.pgbench", "-D", f"maxid={maxid}",
         "-l", "--aggregate-interval=1", f"--log-prefix={LOG_PREFIX}", "postgres"],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)


def collect_writer(writer: subprocess.Popen) -> list[dict]:
    _, err = writer.communicate()
    if writer.returncode != 0:
        print(f"pgbench exited {writer.returncode}: {err.strip()[-500:]}", file=sys.stderr)
    raw = sh("docker", "exec", CONTAINER, "sh", "-c", f"cat {LOG_PREFIX}*", check=False).stdout
    seconds: dict[int, dict] = {}
    # With -j 2 there is one aggregate log per thread; merge them by interval start.
    # Columns: interval_start num_transactions sum_latency sum_latency_2 min_latency max_latency ...
    for line in raw.splitlines():
        parts = line.split()
        if len(parts) < 6:
            continue
        t, n = int(parts[0]), int(parts[1])
        s = seconds.setdefault(t, {"t": t, "tx": 0, "lat_sum_us": 0, "max_us": 0})
        s["tx"] += n
        s["lat_sum_us"] += int(parts[2])
        s["max_us"] = max(s["max_us"], int(parts[5]) if n else 0)
    return [seconds[t] for t in sorted(seconds)]


def analyse(series: list[dict], start: float, end: float) -> dict:
    """Split the writer's timeline into before / during the method, and summarise each."""
    before = [s for s in series if s["t"] < int(start)]
    during = [s for s in series if int(start) <= s["t"] <= int(end)]
    # A second with no row at all in the log means no transaction *completed* in it.
    during_ts = {s["t"] for s in during}
    missing = [t for t in range(int(start), int(end) + 1) if t not in during_ts]
    stalled = [s for s in during if s["tx"] == 0] + [{"t": t} for t in missing]

    def tps(rows: list[dict]) -> float | None:
        return statistics.mean(r["tx"] for r in rows) if rows else None

    return {
        "baseline_tps": tps(before[-10:]),
        "during_tps": tps(during),
        "stalled_seconds": len(stalled),
        "max_latency_ms": max((s["max_us"] for s in during), default=0) / 1000,
        "baseline_max_latency_ms": max((s["max_us"] for s in before[-10:]), default=0) / 1000,
    }


def run_method(name: str, sql: str, rows: int, clients: int, warmup: int, max_method: int, cooldown: int) -> dict:
    print(f"[{name}] building bloated table ({rows:,} rows, 70% deleted)...", flush=True)
    psql("", "-f", "/sql/setup-bloated.sql", "-v", f"rows={rows}")
    psql("CHECKPOINT;")
    size_before = table_size_mb()

    writer = start_writer(clients, rows // 10 - 1, warmup + max_method + cooldown)
    time.sleep(warmup)
    print(f"[{name}] running: {sql}", flush=True)
    t0 = time.time()
    psql(sql + ";")
    t1 = time.time()
    # Measure immediately: the writer keeps inserting for the rest of its window,
    # so a later measurement would count those new rows as "didn't shrink".
    size_after = table_size_mb()
    if t1 - t0 > max_method:
        print(f"[{name}] WARNING: took {t1 - t0:.0f}s, longer than --max-method-seconds {max_method}; "
              f"the writer stopped before it finished", file=sys.stderr)
    series = collect_writer(writer)

    size_after_window = table_size_mb()
    result = {
        "sql": sql,
        "duration_s": t1 - t0,
        "before": size_before,
        "after": size_after,
        "after_window": size_after_window,
        "writer": analyse(series, t0, t1),
        "series": series,
        "window": [t0, t1],
    }
    w = result["writer"]
    print(f"[{name}] {result['duration_s']:.1f}s  heap {size_before['heap_mb']:.0f} -> {size_after['heap_mb']:.0f} MB  "
          f"tps {w['baseline_tps'] or 0:.0f} -> {w['during_tps'] or 0:.0f}  stalled {w['stalled_seconds']}s  "
          f"max latency {w['max_latency_ms']:.0f} ms", flush=True)
    return result


def cmd_run(args: argparse.Namespace) -> None:
    wait_ready()
    raw = json.loads(RAW.read_text()) if RAW.exists() else {}
    raw["_meta"] = {"rows": args.rows, "clients": args.clients,
                    "server_version": psql("SHOW server_version;"),
                    "docker_cpus": sh("docker", "info", "--format", "{{.NCPU}}").stdout.strip()}
    for name, sql in METHODS.items():
        if args.only and name not in args.only:
            continue
        raw[name] = run_method(name, sql, args.rows, args.clients, args.warmup, args.max_method_seconds, args.cooldown)
        RESULTS.mkdir(exist_ok=True)
        RAW.write_text(json.dumps(raw, indent=2))
    cmd_report(args)


def cmd_report(_: argparse.Namespace) -> None:
    raw = json.loads(RAW.read_text())
    meta = raw.get("_meta", {})
    lines = [
        "# REPACK benchmark - results", "",
        f"PostgreSQL `{meta.get('server_version')}`, {meta.get('rows', 0):,} rows inserted then 70% deleted, "
        f"{meta.get('clients')} pgbench clients writing throughout. Docker saw {meta.get('docker_cpus')} CPUs.", "",
        "| Method | Duration s | Heap MB before -> after | Indexes MB before -> after | Writer tps before | "
        "Writer tps during | Seconds with zero commits | Max writer latency ms (baseline) |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for name in METHODS:
        if name not in raw:
            continue
        r = raw[name]
        w = r["writer"]
        lines.append(
            f"| `{name}` | {r['duration_s']:.1f} | {r['before']['heap_mb']:.0f} -> {r['after']['heap_mb']:.0f} | "
            f"{r['before']['indexes_mb']:.0f} -> {r['after']['indexes_mb']:.0f} | {w['baseline_tps'] or 0:,.0f} | "
            f"{w['during_tps'] or 0:,.0f} | {w['stalled_seconds']} | {w['max_latency_ms']:,.0f} "
            f"({w['baseline_max_latency_ms']:,.0f}) |")
    if any(name in raw and "after_window" not in raw[name] for name in METHODS):
        lines += ["", "_Recorded before the harness measured size right after the command: the \"after\" sizes above were "
                  "taken at the end of the writer window and include every row the writer inserted in the meantime, "
                  "so they understate how much the rewrite reclaimed._"]
    (RESULTS / "results.md").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    run = sub.add_parser("run")
    run.add_argument("--rows", type=int, default=2_000_000)
    run.add_argument("--clients", type=int, default=8)
    run.add_argument("--warmup", type=int, default=10)
    run.add_argument("--max-method-seconds", type=int, default=60,
                     help="writer window reserved for the method; raise it for big tables")
    run.add_argument("--cooldown", type=int, default=10)
    run.add_argument("--only", nargs="*", choices=list(METHODS))
    run.set_defaults(func=cmd_run)
    report = sub.add_parser("report")
    report.set_defaults(func=cmd_report)
    args = parser.parse_args()
    args.func(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
