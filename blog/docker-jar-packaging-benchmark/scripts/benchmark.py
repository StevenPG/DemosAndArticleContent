#!/usr/bin/env python3
"""Build and measure every packaging variant in ../docker.

Subcommands:

    build     cold-build every variant, then rebuild after a one-line source
              change, recording build time, image size and how many bytes of
              layers a code change invalidates
    measure   run every image N times, recording time-to-first-served-request,
              resident memory at idle and under load, throughput and p99
    report    render results/results.md and results/results.json from the
              recorded raw data
    all       build, then measure, then report

Everything is shelled out to the docker CLI on purpose - no SDK, no compose, so
you can run any individual step by hand from the README.
"""

from __future__ import annotations

import argparse
import json
import re
import statistics
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCKER_DIR = ROOT / "docker"
RESULTS_DIR = ROOT / "results"
RAW_FILE = RESULTS_DIR / "raw.json"

IMAGE_PREFIX = "jarbench"
HOST_PORT = 18080
CONTAINER_MEMORY = "1g"
CONTAINER_CPUS = "2"

# Endpoints the load generator hits, weighted by how a read-heavy API actually
# gets used: mostly list and by-id, a little aggregation.
LOAD_PATHS = [
    "/api/aircraft?size=20",
    "/api/parts?size=20",
    "/api/telemetry?size=20",
    "/api/aircraft/stats",
    "/api/parts/summaries?size=20",
    "/api/flight-plans?size=20",
    "/actuator/health",
]

DESCRIPTIONS = {
    "01-fatjar-jdk": "Fat jar on the full JDK image",
    "02-fatjar-jre": "Fat jar on the JRE image",
    "03-layered": "Spring Boot layered jar, JRE image",
    "04-extracted": "Extracted jar (thin jar + lib/), JRE image",
    "05-extracted-cds": "Extracted + AppCDS archive",
    "06-extracted-aot": "Extracted + JDK 25 AOT cache",
    "07-jlink-fatjar": "jlink runtime + fat jar, debian-slim",
    "08-jlink-extracted-aot": "jlink + extracted + AOT cache",
    "09-jlink-alpine": "jlink (musl) + extracted, Alpine",
    "10-jlink-distroless": "jlink + extracted, distroless",
}


def run(cmd: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, check=True, capture_output=True, text=True, **kwargs)


def variants() -> list[str]:
    return sorted(path.name.removesuffix(".Dockerfile") for path in DOCKER_DIR.glob("*.Dockerfile"))


def load_raw() -> dict:
    if RAW_FILE.exists():
        return json.loads(RAW_FILE.read_text())
    return {"variants": {}}


def save_raw(raw: dict) -> None:
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    raw["updated"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
    RAW_FILE.write_text(json.dumps(raw, indent=2, sort_keys=True) + "\n")


# --------------------------------------------------------------------------- build


def image_size(image: str) -> int:
    """Compressed size - what a registry stores and what a node pulls."""
    return int(run(["docker", "image", "inspect", image, "--format", "{{.Size}}"]).stdout.strip())


def image_size_uncompressed(image: str) -> int:
    """On-disk size once unpacked, as reported by `docker images`."""
    text = run(["docker", "images", "--format", "{{.Size}}", image]).stdout.strip().splitlines()[0]
    match = re.match(r"([0-9.]+)\s*([A-Za-z]+)", text)
    if not match:
        return 0
    factor = {"B": 1, "KB": 1e3, "MB": 1e6, "GB": 1e9, "KIB": 1024, "MIB": 1024**2, "GIB": 1024**3}
    return int(float(match.group(1)) * factor.get(match.group(2).upper(), 1))


def layer_digests(image: str) -> list[str]:
    out = run(["docker", "image", "inspect", image, "--format", "{{json .RootFS.Layers}}"]).stdout
    return json.loads(out)


def layer_sizes(image: str) -> dict[str, int]:
    """Uncompressed size of every layer in the image, keyed by diff id.

    `docker history` reports per-layer sizes in creation order, which lines up
    with RootFS.Layers once the zero-byte metadata-only entries are dropped.
    """
    history = json.loads(
        run(["docker", "image", "inspect", image, "--format", "{{json .RootFS.Layers}}"]).stdout
    )
    rows = [
        json.loads(line)
        for line in run(
            ["docker", "history", "--no-trunc", "--human=false", "--format", "{{json .}}", image]
        ).stdout.splitlines()
    ]
    sizes = [int(row["Size"]) for row in reversed(rows) if int(row["Size"]) > 0]
    if len(sizes) != len(history):
        # Fall back to an even split rather than guessing wrong - only used for
        # the "bytes changed by a code edit" figure.
        total = image_size(image)
        return {digest: total // max(len(history), 1) for digest in history}
    return dict(zip(history, sizes))


def build_variant(variant: str, offline: bool, no_cache: bool, tag_suffix: str = "") -> float:
    image = f"{IMAGE_PREFIX}:{variant}{tag_suffix}"
    cmd = [
        "docker",
        "build",
        "--progress=plain",
        "-f",
        str(DOCKER_DIR / f"{variant}.Dockerfile"),
        "-t",
        image,
    ]
    if offline:
        cmd += [
            "--build-arg",
            "GRADLE_ARGS=--offline",
            "--build-arg",
            "BUILD_IMAGE=jarbench:builder",
        ]
    if no_cache:
        cmd.append("--no-cache")
    cmd.append(str(ROOT))
    started = time.monotonic()
    result = subprocess.run(cmd, capture_output=True, text=True)
    elapsed = time.monotonic() - started
    if result.returncode != 0:
        tail = "\n".join(result.stderr.splitlines()[-40:])
        raise SystemExit(f"build failed for {variant}:\n{tail}")
    return elapsed


def touch_source() -> tuple[Path, str]:
    """Simulate the most common rebuild: one line of application code changed.

    Rewriting a string constant is the smallest edit that actually changes a
    class file - a comment would be stripped by javac and the rebuilt jar would
    come out byte-identical.
    """
    source = ROOT / "bench-app/src/main/java/com/stevenpg/fleet/support/BuildInfo.java"
    original = source.read_text()
    source.write_text(original.replace('REVISION = "dev"', f'REVISION = "rev{time.time_ns()}"'))
    return source, original


def cmd_build(args: argparse.Namespace) -> None:
    raw = load_raw()
    for variant in variants():
        if args.only and variant not in args.only:
            continue
        print(f"==> cold build {variant}", flush=True)
        cold = build_variant(variant, args.offline, no_cache=True)
        image = f"{IMAGE_PREFIX}:{variant}"
        before_digests = layer_digests(image)
        sizes = layer_sizes(image)
        size = image_size(image)

        print(f"==> rebuild after code change {variant}", flush=True)
        source, original = touch_source()
        try:
            warm = build_variant(variant, args.offline, no_cache=False)
        finally:
            source.write_text(original)
        after_digests = layer_digests(image)
        after_sizes = layer_sizes(image)
        changed = [digest for digest in after_digests if digest not in before_digests]
        changed_bytes = sum(after_sizes.get(digest, 0) for digest in changed)

        # Rebuild once more so the published image matches the committed source.
        build_variant(variant, args.offline, no_cache=False)

        entry = raw["variants"].setdefault(variant, {})
        entry.update(
            {
                "description": DESCRIPTIONS.get(variant, variant),
                "cold_build_s": round(cold, 1),
                "code_change_rebuild_s": round(warm, 1),
                "image_size_bytes": size,
                "image_size_uncompressed_bytes": image_size_uncompressed(image),
                "layer_count": len(before_digests),
                "code_change_layers": len(changed),
                "code_change_bytes": changed_bytes,
            }
        )
        save_raw(raw)
        print(
            f"    {variant}: cold {cold:.1f}s, rebuild {warm:.1f}s, "
            f"size {size / 1e6:.0f}MB, changed {changed_bytes / 1e6:.1f}MB",
            flush=True,
        )


# ------------------------------------------------------------------------- measure


def http_get(path: str, timeout: float = 5.0) -> tuple[int, int]:
    """GET against the container under test, bypassing any ambient proxy."""
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    request = urllib.request.Request(f"http://127.0.0.1:{HOST_PORT}{path}")
    started = time.perf_counter_ns()
    try:
        with opener.open(request, timeout=timeout) as response:
            response.read()
            status = response.status
    except urllib.error.HTTPError as error:
        status = error.code
    return status, time.perf_counter_ns() - started


def wait_until_ready(deadline_s: float) -> float | None:
    """Poll /actuator/health every 5ms. Returns ms until the first 200."""
    started = time.monotonic()
    while time.monotonic() - started < deadline_s:
        try:
            status, _ = http_get("/actuator/health", timeout=2.0)
            if status == 200:
                return (time.monotonic() - started) * 1000
        except Exception:
            pass
        time.sleep(0.005)
    return None


def container_memory_mb(name: str) -> float:
    out = run(
        ["docker", "stats", "--no-stream", "--format", "{{.MemUsage}}", name]
    ).stdout.strip()
    value = out.split("/")[0].strip()
    match = re.match(r"([0-9.]+)\s*([A-Za-z]+)", value)
    if not match:
        return 0.0
    number, unit = float(match.group(1)), match.group(2).upper()
    factor = {"B": 1 / 1e6, "KIB": 1024 / 1e6, "MIB": 1024**2 / 1e6, "GIB": 1024**3 / 1e6}
    return round(number * factor.get(unit, 1.0), 1)


def load_test(duration_s: int, threads: int) -> dict:
    latencies: list[int] = []
    errors = 0
    lock = threading.Lock()
    stop_at = time.monotonic() + duration_s

    def worker(offset: int) -> None:
        nonlocal errors
        local: list[int] = []
        local_errors = 0
        index = offset
        while time.monotonic() < stop_at:
            path = LOAD_PATHS[index % len(LOAD_PATHS)]
            index += 1
            try:
                status, nanos = http_get(path, timeout=10.0)
                if status >= 400:
                    local_errors += 1
                else:
                    local.append(nanos)
            except Exception:
                local_errors += 1
        with lock:
            latencies.extend(local)
            errors += local_errors

    workers = [threading.Thread(target=worker, args=(i,)) for i in range(threads)]
    started = time.monotonic()
    for worker_thread in workers:
        worker_thread.start()
    for worker_thread in workers:
        worker_thread.join()
    elapsed = time.monotonic() - started

    if not latencies:
        return {"requests": 0, "errors": errors}
    ordered = sorted(latencies)
    return {
        "requests": len(latencies),
        "errors": errors,
        "throughput_rps": round(len(latencies) / elapsed, 1),
        "p50_ms": round(ordered[len(ordered) // 2] / 1e6, 2),
        "p99_ms": round(ordered[min(len(ordered) - 1, int(len(ordered) * 0.99))] / 1e6, 2),
    }


def start_container(variant: str, name: str) -> None:
    run(
        [
            "docker",
            "run",
            "-d",
            "--rm",
            "--name",
            name,
            "--memory",
            CONTAINER_MEMORY,
            "--cpus",
            CONTAINER_CPUS,
            "-p",
            f"{HOST_PORT}:8080",
            f"{IMAGE_PREFIX}:{variant}",
        ]
    )


def stop_container(name: str) -> None:
    subprocess.run(["docker", "rm", "-f", name], capture_output=True, text=True)
    # docker rm returns before the port is released; wait for it.
    time.sleep(1.5)


def cmd_measure(args: argparse.Namespace) -> None:
    raw = load_raw()
    for variant in variants():
        if args.only and variant not in args.only:
            continue
        name = f"bench-{variant}"
        stop_container(name)
        startups: list[float] = []
        idle_mb = loaded_mb = 0.0
        load_result: dict = {}
        logs_tail = ""

        for run_index in range(args.runs):
            start_container(variant, name)
            ready_ms = wait_until_ready(args.timeout)
            if ready_ms is None:
                logs = subprocess.run(
                    ["docker", "logs", "--tail", "30", name], capture_output=True, text=True
                )
                stop_container(name)
                raise SystemExit(f"{variant} never became ready:\n{logs.stdout}\n{logs.stderr}")
            startups.append(round(ready_ms, 1))

            if run_index == args.runs - 1:
                time.sleep(3)  # let post-startup allocation settle
                idle_mb = container_memory_mb(name)
                load_result = load_test(args.load_seconds, args.threads)
                loaded_mb = container_memory_mb(name)
                logs_tail = subprocess.run(
                    ["docker", "logs", "--tail", "60", name], capture_output=True, text=True
                ).stdout
            stop_container(name)

        loaded_classes = None
        match = re.search(r"BENCH_LOADED_CLASSES=(\d+)", logs_tail)
        if match:
            loaded_classes = int(match.group(1))

        entry = raw["variants"].setdefault(variant, {})
        entry.update(
            {
                "description": DESCRIPTIONS.get(variant, variant),
                "startup_ms_runs": startups,
                "startup_ms_median": round(statistics.median(startups), 1),
                "startup_ms_min": min(startups),
                "rss_idle_mb": idle_mb,
                "rss_loaded_mb": loaded_mb,
                "loaded_classes": loaded_classes,
                **{f"load_{key}": value for key, value in load_result.items()},
            }
        )
        save_raw(raw)
        print(
            f"    {variant}: startup median {entry['startup_ms_median']}ms, "
            f"idle {idle_mb}MB, loaded {loaded_mb}MB, "
            f"{entry.get('load_throughput_rps')} rps",
            flush=True,
        )


# -------------------------------------------------------------------------- report


def mb(value: int | None) -> str:
    if value is None:
        return "-"
    megabytes = value / 1e6
    return f"{megabytes:.1f} MB" if megabytes < 10 else f"{megabytes:.0f} MB"


def cmd_report(_: argparse.Namespace) -> None:
    raw = load_raw()
    rows = [(name, data) for name, data in sorted(raw["variants"].items())]
    if not rows:
        raise SystemExit("no results recorded yet - run `benchmark.py build` first")

    lines = [
        "# Results",
        "",
        f"Recorded {raw.get('updated', 'unknown')} by `scripts/benchmark.py`.",
        "",
        "## Image size and build",
        "",
        "| Variant | What it is | Pull size (compressed) | On disk | Layers | Cold build | Rebuild after code change | Re-pulled after a code change |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for name, data in rows:
        lines.append(
            "| `{name}` | {desc} | {size} | {disk} | {layers} | {cold}s | {warm}s | {changed} |".format(
                name=name,
                desc=data.get("description", ""),
                size=mb(data.get("image_size_bytes")),
                disk=mb(data.get("image_size_uncompressed_bytes")),
                layers=data.get("layer_count", "-"),
                cold=data.get("cold_build_s", "-"),
                warm=data.get("code_change_rebuild_s", "-"),
                changed=mb(data.get("code_change_bytes")),
            )
        )

    lines += [
        "",
        "## Startup, memory and throughput",
        "",
        "| Variant | Startup (median) | Startup (best) | Classes loaded | RSS idle | RSS under load | Throughput | p50 | p99 |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    for name, data in rows:
        lines.append(
            "| `{name}` | {median} ms | {best} ms | {classes} | {idle} MB | {loaded} MB | {rps} rps | {p50} ms | {p99} ms |".format(
                name=name,
                median=data.get("startup_ms_median", "-"),
                best=data.get("startup_ms_min", "-"),
                classes=data.get("loaded_classes", "-"),
                idle=data.get("rss_idle_mb", "-"),
                loaded=data.get("rss_loaded_mb", "-"),
                rps=data.get("load_throughput_rps", "-"),
                p50=data.get("load_p50_ms", "-"),
                p99=data.get("load_p99_ms", "-"),
            )
        )

    lines.append("")
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    (RESULTS_DIR / "results.md").write_text("\n".join(lines))
    print("\n".join(lines))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only", help="comma-separated list of variant names to limit the run to")
    sub = parser.add_subparsers(dest="command", required=True)

    build = sub.add_parser("build")
    build.add_argument("--offline", action="store_true", help="build against the pre-seeded jarbench:builder image (see seed-builder-image.sh)")
    build.set_defaults(func=cmd_build)

    measure = sub.add_parser("measure")
    measure.add_argument("--runs", type=int, default=5)
    measure.add_argument("--timeout", type=float, default=180.0)
    measure.add_argument("--load-seconds", type=int, default=20)
    measure.add_argument("--threads", type=int, default=16)
    measure.set_defaults(func=cmd_measure)

    report = sub.add_parser("report")
    report.set_defaults(func=cmd_report)

    everything = sub.add_parser("all")
    everything.add_argument("--offline", action="store_true")
    everything.add_argument("--runs", type=int, default=5)
    everything.add_argument("--timeout", type=float, default=180.0)
    everything.add_argument("--load-seconds", type=int, default=20)
    everything.add_argument("--threads", type=int, default=16)
    everything.set_defaults(func=None)

    args = parser.parse_args()
    args.only = args.only.split(",") if args.only else None
    if args.command == "all":
        cmd_build(args)
        cmd_measure(args)
        cmd_report(args)
    else:
        args.func(args)
    return 0


if __name__ == "__main__":
    sys.exit(main())
