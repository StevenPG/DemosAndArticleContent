#!/usr/bin/env python3
"""
Java 27 defaults benchmark.

Runs the same Spring Boot jar under JDK 25, 26 and 27 inside resource-limited
containers and records what the JVM chose on its own (collector, header
layout, heap ceiling) and what that did to footprint and latency.

    python3 scripts/benchmark.py measure            # all profiles, all rows
    python3 scripts/benchmark.py measure --profile small --runs 3
    python3 scripts/benchmark.py report             # re-render results.md

Requires Docker, Python 3.11+, ../.jdks populated by scripts/fetch-jdks.sh and
bench-app/build/libs/bench-app.jar built with ./gradlew bootJar.
"""
from __future__ import annotations

import argparse
import http.client
import json
import random
import statistics
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JDKS = ROOT / ".jdks"
JAR = ROOT / "bench-app" / "build" / "libs" / "bench-app.jar"
RESULTS = ROOT / "results"
RAW = RESULTS / "raw.json"
IMAGE = "debian:trixie-slim"
HOST_PORT = 18080
CONTAINER = "j27bench"

# Container shapes. "small" is below JEP 523's old Serial threshold (1 CPU or
# < 1792 MB), so JDK 25/26 pick Serial there and JDK 27 picks G1. "medium" is
# above it, so every JDK picks G1 and only the header change is in play.
PROFILES = {
    "small": {"cpus": "1", "memory": "1g"},
    "medium": {"cpus": "2", "memory": "2g"},
}

# (row id, jdk dir, extra JVM flags, what the row isolates)
ROWS = [
    ("jdk25", "jdk25", [], "JDK 25 LTS, all defaults"),
    ("jdk25+coh", "jdk25", ["-XX:+UseCompactObjectHeaders"], "JDK 25 with the flag you could already set"),
    ("jdk26", "jdk26", [], "JDK 26, all defaults"),
    ("jdk27", "jdk27", [], "JDK 27, all defaults (G1 + compact headers)"),
    ("jdk27-coh", "jdk27", ["-XX:-UseCompactObjectHeaders"], "JDK 27, legacy 12-byte headers"),
    ("jdk27+serial", "jdk27", ["-XX:+UseSerialGC"], "JDK 27, Serial pinned back"),
]

# Request mix for the load phase, by weight.
MIX = [("track", 70), ("ingest", 20), ("stats", 10)]
AIRCRAFT = 20_000


def run(cmd: list[str], check: bool = True, **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, check=check, text=True, capture_output=True, **kwargs)


def load_raw() -> dict:
    return json.loads(RAW.read_text()) if RAW.exists() else {}


def save_raw(raw: dict) -> None:
    RESULTS.mkdir(exist_ok=True)
    RAW.write_text(json.dumps(raw, indent=2, sort_keys=True))


# --------------------------------------------------------------------- http

def request(method: str, path: str, body: bytes | None = None, timeout: float = 30.0,
            conn: http.client.HTTPConnection | None = None) -> tuple[int, bytes]:
    own = conn is None
    conn = conn or http.client.HTTPConnection("127.0.0.1", HOST_PORT, timeout=timeout)
    headers = {"Content-Type": "application/json"} if body is not None else {}
    try:
        conn.request(method, path, body=body, headers=headers)
        response = conn.getresponse()
        return response.status, response.read()
    finally:
        if own:
            conn.close()


def get_json(path: str, method: str = "GET") -> dict:
    status, body = request(method, path, body=b"" if method == "POST" else None, timeout=120)
    if status != 200:
        raise RuntimeError(f"{method} {path} -> {status}")
    return json.loads(body)


def wait_until_ready(started: float, deadline_s: float = 180) -> float:
    while time.monotonic() - started < deadline_s:
        try:
            status, _ = request("GET", "/actuator/health/readiness", timeout=1)
            if status == 200:
                return time.monotonic() - started
        except OSError:
            pass
        time.sleep(0.05)
    raise TimeoutError("app never became ready")


# ------------------------------------------------------------------- docker

def start(jdk: str, flags: list[str], profile: dict) -> float:
    run(["docker", "rm", "-f", CONTAINER], check=False)
    cmd = [
        "docker", "run", "-d", "--name", CONTAINER,
        "--cpus", profile["cpus"], "--memory", profile["memory"],
        "-p", f"127.0.0.1:{HOST_PORT}:8080",
        "-v", f"{JDKS}:/jdks:ro",
        "-v", f"{JAR}:/app/bench-app.jar:ro",
        IMAGE, f"/jdks/{jdk}/bin/java", *flags, "-jar", "/app/bench-app.jar",
    ]
    started = time.monotonic()
    run(cmd)
    return wait_until_ready(started)


def stop() -> None:
    run(["docker", "rm", "-f", CONTAINER], check=False)


def container_memory_mib() -> float:
    """Container memory as the cgroup sees it: what a Kubernetes limit is enforced against."""
    out = run(["docker", "stats", "--no-stream", "--format", "{{.MemUsage}}", CONTAINER]).stdout
    used = out.split("/")[0].strip()
    units = {"KiB": 1 / 1024, "MiB": 1, "GiB": 1024, "B": 1 / (1024 * 1024)}
    for unit, factor in units.items():
        if used.endswith(unit):
            return float(used[: -len(unit)]) * factor
    raise ValueError(f"unparseable docker stats output: {out!r}")


# --------------------------------------------------------------------- load

def load_test(seconds: int, threads: int) -> dict:
    latencies: dict[str, list[float]] = {name: [] for name, _ in MIX}
    errors = [0]
    stop_at = time.monotonic() + seconds
    lock = threading.Lock()
    names = [name for name, _ in MIX]
    weights = [weight for _, weight in MIX]

    def worker(seed: int) -> None:
        rng = random.Random(seed)
        conn = http.client.HTTPConnection("127.0.0.1", HOST_PORT, timeout=30)
        local: dict[str, list[float]] = {name: [] for name in names}
        local_errors = 0
        while time.monotonic() < stop_at:
            kind = rng.choices(names, weights)[0]
            aircraft = rng.randint(1, AIRCRAFT)
            if kind == "track":
                method, path, body = "GET", f"/api/aircraft/{aircraft}/track?limit=50", None
            elif kind == "ingest":
                batch = [{"epochMillis": int(time.time() * 1000) + i, "lat": 40.0, "lon": -75.0,
                          "altitudeFt": rng.randint(0, 45_000), "groundSpeedKt": rng.randint(80, 520),
                          "heading": rng.randint(0, 359)} for i in range(20)]
                method, path, body = "POST", f"/api/aircraft/{aircraft}/positions", json.dumps(batch).encode()
            else:
                method, path, body = "GET", "/api/stats/altitude-bands", None
            t0 = time.perf_counter()
            try:
                status, _ = request(method, path, body, conn=conn)
                if status != 200:
                    local_errors += 1
                    continue
            except OSError:
                local_errors += 1
                conn.close()
                conn = http.client.HTTPConnection("127.0.0.1", HOST_PORT, timeout=30)
                continue
            local[kind].append((time.perf_counter() - t0) * 1000)
        conn.close()
        with lock:
            for name in names:
                latencies[name].extend(local[name])
            errors[0] += local_errors

    pool = [threading.Thread(target=worker, args=(i,)) for i in range(threads)]
    for t in pool:
        t.start()
    for t in pool:
        t.join()

    everything = sorted(v for values in latencies.values() for v in values)

    def pct(values: list[float], p: float) -> float | None:
        if not values:
            return None
        values = sorted(values)
        return values[min(len(values) - 1, int(p / 100 * len(values)))]

    return {
        "requests": len(everything),
        "errors": errors[0],
        "rps": len(everything) / seconds,
        "p50_ms": pct(everything, 50),
        "p99_ms": pct(everything, 99),
        "by_kind": {name: {"n": len(v), "p50_ms": pct(v, 50), "p99_ms": pct(v, 99)} for name, v in latencies.items()},
    }


# ------------------------------------------------------------------ measure

def measure_once(jdk: str, flags: list[str], profile: dict, seconds: int, threads: int) -> dict:
    ready_s = start(jdk, flags, profile)
    try:
        info = get_json("/bench/info")
        time.sleep(2)
        idle_mib = container_memory_mib()
        live = get_json("/bench/live-set", method="POST")
        # Warm-up so the JIT has compiled the hot paths before the measured window.
        load_test(max(5, seconds // 4), threads)
        gc_before = get_json("/bench/gc")
        load = load_test(seconds, threads)
        gc_after = get_json("/bench/gc")
        loaded_mib = container_memory_mib()
        return {
            "ready_s": ready_s,
            "info": info,
            "idle_container_mib": idle_mib,
            "live_set_mib": live["usedHeapBytes"] / (1024 * 1024),
            "loaded_container_mib": loaded_mib,
            "gc_collections": gc_after["collections"] - gc_before["collections"],
            "gc_millis": gc_after["collectionMillis"] - gc_before["collectionMillis"],
            "load": load,
        }
    finally:
        stop()


def cmd_measure(args: argparse.Namespace) -> None:
    if not JAR.exists():
        sys.exit(f"missing {JAR} - run ./gradlew bootJar in bench-app first")
    for _, jdk, _, _ in ROWS:
        if not (JDKS / jdk / "bin" / "java").exists():
            sys.exit(f"missing {JDKS / jdk} - run scripts/fetch-jdks.sh first")

    raw = load_raw()
    raw.setdefault("_meta", {})["host"] = {
        "docker_arch": run(["docker", "info", "--format", "{{.Architecture}}"]).stdout.strip(),
        "docker_cpus": run(["docker", "info", "--format", "{{.NCPU}}"]).stdout.strip(),
        "load_seconds": args.load_seconds,
        "threads": args.threads,
        "runs": args.runs,
    }
    profiles = [args.profile] if args.profile else list(PROFILES)
    rows = [r for r in ROWS if not args.only or r[0] in args.only]
    for profile_name in profiles:
        for row_id, jdk, flags, _ in rows:
            key = f"{profile_name}/{row_id}"
            samples = []
            for n in range(args.runs):
                print(f"[{key}] run {n + 1}/{args.runs}", flush=True)
                sample = measure_once(jdk, flags, PROFILES[profile_name], args.load_seconds, args.threads)
                print(f"    ready {sample['ready_s']:.2f}s  live {sample['live_set_mib']:.0f} MiB  "
                      f"rps {sample['load']['rps']:.0f}  p99 {sample['load']['p99_ms']:.1f} ms  "
                      f"gc {sample['gc_collections']}/{sample['gc_millis']} ms  "
                      f"collectors {sample['info']['collectors']}", flush=True)
                samples.append(sample)
            raw[key] = samples
            save_raw(raw)
    cmd_report(args)


# ------------------------------------------------------------------- report

def median(values: list[float | None]) -> float | None:
    values = [v for v in values if v is not None]
    return statistics.median(values) if values else None


def fmt(value: float | None, digits: int = 0) -> str:
    return "-" if value is None else f"{value:,.{digits}f}"


def cmd_report(_: argparse.Namespace) -> None:
    raw = load_raw()
    if not raw:
        sys.exit("no results yet - run measure first")
    meta = raw.get("_meta", {}).get("host", {})
    lines = [
        "# Java 27 defaults benchmark - results",
        "",
        f"Docker arch `{meta.get('docker_arch')}`, {meta.get('docker_cpus')} CPUs visible to Docker, "
        f"{meta.get('runs')} runs per row (medians shown), {meta.get('load_seconds')} s load at "
        f"{meta.get('threads')} client threads.",
        "",
    ]
    for profile_name, profile in PROFILES.items():
        keys = [f"{profile_name}/{row[0]}" for row in ROWS if f"{profile_name}/{row[0]}" in raw]
        if not keys:
            continue
        lines += [
            f"## `{profile_name}` - `--cpus {profile['cpus']} --memory {profile['memory']}`",
            "",
            "| Row | Collector | Compact headers | Max heap MiB | Ready s | Live set MiB | Container idle MiB "
            "| Container loaded MiB | req/s | p50 ms | p99 ms | GCs | GC ms |",
            "|---|---|---|---|---|---|---|---|---|---|---|---|---|",
        ]
        for key in keys:
            samples = raw[key]
            first = samples[0]["info"]
            collector = "G1" if any("G1" in c for c in first["collectors"]) else (
                "Serial" if "Copy" in first["collectors"] else ", ".join(first["collectors"]))
            lines.append("| " + " | ".join([
                f"`{key.split('/')[1]}`",
                collector,
                first["compactObjectHeaders"],
                fmt(first["maxHeapBytes"] / (1024 * 1024)),
                fmt(median([s["ready_s"] for s in samples]), 2),
                fmt(median([s["live_set_mib"] for s in samples]), 1),
                fmt(median([s["idle_container_mib"] for s in samples])),
                fmt(median([s["loaded_container_mib"] for s in samples])),
                fmt(median([s["load"]["rps"] for s in samples])),
                fmt(median([s["load"]["p50_ms"] for s in samples]), 1),
                fmt(median([s["load"]["p99_ms"] for s in samples]), 1),
                fmt(median([s["gc_collections"] for s in samples])),
                fmt(median([s["gc_millis"] for s in samples])),
            ]) + " |")
        lines.append("")
    lines += ["## Rows", ""] + [f"- `{r[0]}` - {r[3]}" + (f" (`{' '.join(r[2])}`)" if r[2] else "") for r in ROWS]
    (RESULTS / "results.md").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    measure = sub.add_parser("measure")
    measure.add_argument("--profile", choices=list(PROFILES))
    measure.add_argument("--only", nargs="*", help="row ids to run, e.g. jdk26 jdk27")
    measure.add_argument("--runs", type=int, default=3)
    measure.add_argument("--load-seconds", type=int, default=30)
    measure.add_argument("--threads", type=int, default=16)
    measure.set_defaults(func=cmd_measure)
    report = sub.add_parser("report")
    report.set_defaults(func=cmd_report)
    args = parser.parse_args()
    args.func(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
