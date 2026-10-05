#!/usr/bin/env python3
"""Benchmark Clef-flash: latency, throughput, resource usage and answer quality.

For every (workload, concurrency) step it runs a closed loop of ``concurrency`` workers against
``/v1/systemone`` and records per-request latency and input tokens. A background sampler records
CPU, RSS, and (if ``nvidia-smi`` exists) GPU utilization, VRAM and power while each step runs.
With ``--eval`` it also answers the labeled cases in ``data/eval_cases.json`` and stores every
probability, so quantizations and Workers AI can be compared answer by answer.

    # against a local llama-server
    python bench/clef_bench.py --label laptop-q4 --out results/laptop/Q4_K_M.json

    # against Workers AI (needs CLOUDFLARE_ACCOUNT_ID / CLOUDFLARE_API_TOKEN)
    python bench/clef_bench.py --target workers-ai --label workers-ai --concurrency 1,4 \\
        --duration 30 --out results/workers-ai/workers-ai.json
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import platform
import shutil
import statistics
import subprocess
import sys
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import httpx
import psutil

sys.path.insert(0, str(Path(__file__).resolve().parent))
import workloads  # noqa: E402
from clef_client import LlamaCppClient, make_client  # noqa: E402

HERE = Path(__file__).resolve().parent


# ---------------------------------------------------------------------------------------------
# resource sampling
# ---------------------------------------------------------------------------------------------


class ResourceSampler:
    """Samples host/process CPU + memory with psutil and GPU stats with nvidia-smi."""

    def __init__(self, pid: int | None, interval_s: float = 0.5) -> None:
        self.interval_s = interval_s
        self.proc = psutil.Process(pid) if pid else None
        self.samples: list[dict[str, float]] = []
        self.gpu_samples: list[dict[str, float]] = []
        self._stop = threading.Event()
        self._threads: list[threading.Thread] = []
        self._smi: subprocess.Popen[str] | None = None

    def __enter__(self) -> "ResourceSampler":
        psutil.cpu_percent(None)
        if self.proc:
            self.proc.cpu_percent(None)
        self._threads.append(threading.Thread(target=self._cpu_loop, daemon=True))
        if shutil.which("nvidia-smi"):
            self._smi = subprocess.Popen(
                [
                    "nvidia-smi",
                    "--query-gpu=utilization.gpu,memory.used,power.draw",
                    "--format=csv,noheader,nounits",
                    f"-lms={int(self.interval_s * 1000)}",
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
            )
            self._threads.append(threading.Thread(target=self._gpu_loop, daemon=True))
        for thread in self._threads:
            thread.start()
        return self

    def __exit__(self, *exc: object) -> None:
        self._stop.set()
        if self._smi:
            self._smi.terminate()
        for thread in self._threads:
            thread.join(timeout=2)

    def _cpu_loop(self) -> None:
        cores = psutil.cpu_count() or 1
        while not self._stop.wait(self.interval_s):
            sample = {
                "host_cpu_pct": psutil.cpu_percent(None),
                "host_mem_used_mb": psutil.virtual_memory().used / 2**20,
            }
            if self.proc:
                try:
                    # process cpu_percent is per-core (1600% on 16 vCPUs); normalise to 0-100 of the box
                    sample["proc_cpu_pct"] = self.proc.cpu_percent(None) / cores
                    sample["proc_rss_mb"] = self.proc.memory_info().rss / 2**20
                except psutil.NoSuchProcess:
                    pass
            self.samples.append(sample)

    def _gpu_loop(self) -> None:
        assert self._smi and self._smi.stdout
        for line in self._smi.stdout:
            if self._stop.is_set():
                break
            # one line per GPU; the instances here have a single GPU
            parts = [p.strip() for p in line.split(",")]
            try:
                self.gpu_samples.append(
                    {
                        "gpu_util_pct": float(parts[0]),
                        "gpu_mem_used_mb": float(parts[1]),
                        "gpu_power_w": float(parts[2]),
                    }
                )
            except (ValueError, IndexError):
                continue

    def summary(self) -> dict[str, float | None]:
        def agg(rows: list[dict[str, float]], key: str, fn: Any) -> float | None:
            values = [row[key] for row in rows if key in row]
            return round(fn(values), 2) if values else None

        return {
            "host_cpu_pct_avg": agg(self.samples, "host_cpu_pct", statistics.fmean),
            "host_cpu_pct_max": agg(self.samples, "host_cpu_pct", max),
            "proc_cpu_pct_avg": agg(self.samples, "proc_cpu_pct", statistics.fmean),
            "proc_rss_mb_max": agg(self.samples, "proc_rss_mb", max),
            "host_mem_used_mb_max": agg(self.samples, "host_mem_used_mb", max),
            "gpu_util_pct_avg": agg(self.gpu_samples, "gpu_util_pct", statistics.fmean),
            "gpu_util_pct_max": agg(self.gpu_samples, "gpu_util_pct", max),
            "gpu_mem_used_mb_max": agg(self.gpu_samples, "gpu_mem_used_mb", max),
            "gpu_power_w_avg": agg(self.gpu_samples, "gpu_power_w", statistics.fmean),
        }


# ---------------------------------------------------------------------------------------------
# load steps
# ---------------------------------------------------------------------------------------------


def percentile(values: list[float], pct: float) -> float:
    ordered = sorted(values)
    k = (len(ordered) - 1) * pct / 100
    lo, hi = int(k), min(int(k) + 1, len(ordered) - 1)
    return ordered[lo] + (ordered[hi] - ordered[lo]) * (k - lo)


async def run_step(
    client: LlamaCppClient,
    requests: list[dict[str, Any]],
    concurrency: int,
    duration_s: float,
    min_requests: int,
    max_step_s: float,
) -> dict[str, Any]:
    """Closed loop: ``concurrency`` workers, each sends its next request as soon as the last returns.

    Stops once ``duration_s`` has passed AND at least ``min_requests`` completed, or at ``max_step_s``.
    Slow boxes (a 4.5k-token prompt on a CPU) still get enough samples; fast ones don't run forever.
    """
    latencies: list[float] = []
    tokens: list[int] = []
    errors: list[str] = []
    counter = iter(range(10**9))
    start = time.perf_counter()

    def done() -> bool:
        elapsed = time.perf_counter() - start
        finished = len(latencies) + len(errors)
        return elapsed >= max_step_s or (elapsed >= duration_s and finished >= min_requests)

    async def worker(http: httpx.AsyncClient) -> None:
        while not done():
            request = requests[next(counter) % len(requests)]
            try:
                decision = await client.adecide(request, http)
                latencies.append(decision.latency_s)
                tokens.append(decision.input_tokens)
            except httpx.HTTPStatusError as exc:
                errors.append(f"{exc.response.status_code}: {exc.response.text[:200]}")
                await asyncio.sleep(0.5)  # don't spin on a 429 from Workers AI
            except httpx.HTTPError as exc:
                errors.append(f"{type(exc).__name__}: {exc}")
                await asyncio.sleep(0.5)

    limits = httpx.Limits(max_connections=concurrency, max_keepalive_connections=concurrency)
    async with httpx.AsyncClient(timeout=client.timeout_s, limits=limits) as http:
        await asyncio.gather(*(worker(http) for _ in range(concurrency)))
    wall = time.perf_counter() - start

    result: dict[str, Any] = {
        "concurrency": concurrency,
        "wall_s": round(wall, 3),
        "requests": len(latencies),
        "errors": len(errors),
        "error_samples": errors[:3],
    }
    if latencies:
        result.update(
            {
                "latency_ms": {
                    "mean": round(statistics.fmean(latencies) * 1000, 1),
                    "p50": round(percentile(latencies, 50) * 1000, 1),
                    "p90": round(percentile(latencies, 90) * 1000, 1),
                    "p99": round(percentile(latencies, 99) * 1000, 1),
                    "min": round(min(latencies) * 1000, 1),
                    "max": round(max(latencies) * 1000, 1),
                },
                "input_tokens_mean": round(statistics.fmean(tokens), 1),
                "requests_per_s": round(len(latencies) / wall, 3),
                "input_tokens_per_s": round(sum(tokens) / wall, 1),
            }
        )
    return result


# ---------------------------------------------------------------------------------------------
# labeled eval
# ---------------------------------------------------------------------------------------------


def predicted(answer: dict[str, Any]) -> Any:
    if answer["type"] == "noul":
        return answer["noul"] >= 0.5
    if answer["type"] == "choice":
        return answer["choice"]
    probabilities = answer["probabilities"]
    return int(max(probabilities, key=probabilities.get))


def run_eval(client: LlamaCppClient, cases_path: Path) -> dict[str, Any]:
    cases = json.loads(cases_path.read_text())
    rows, correct, total = [], 0, 0
    with httpx.Client(timeout=client.timeout_s) as http:
        for case in cases:
            request = {"state": case["state"], "questions": case["questions"]}
            try:
                decision = client.decide(request, http)
            except httpx.HTTPError as exc:
                rows.append({"id": case["id"], "error": str(exc)})
                continue
            graded = {}
            for qid, expected in case.get("expected", {}).items():
                answer = decision.answers.get(qid)
                got = predicted(answer) if answer else None
                graded[qid] = {"expected": expected, "got": got, "ok": got == expected}
                total += 1
                correct += got == expected
            rows.append(
                {
                    "id": case["id"],
                    "latency_ms": round(decision.latency_s * 1000, 1),
                    "input_tokens": decision.input_tokens,
                    "answers": decision.answers,
                    "graded": graded,
                }
            )
    return {
        "cases": len(cases),
        "graded_questions": total,
        "correct": correct,
        "accuracy": round(correct / total, 4) if total else None,
        "rows": rows,
    }


# ---------------------------------------------------------------------------------------------
# metadata
# ---------------------------------------------------------------------------------------------


def host_info() -> dict[str, Any]:
    info: dict[str, Any] = {
        "hostname": platform.node(),
        "machine": platform.machine(),
        "python": platform.python_version(),
        "logical_cpus": psutil.cpu_count(),
        "physical_cpus": psutil.cpu_count(logical=False),
        "mem_total_gb": round(psutil.virtual_memory().total / 2**30, 1),
    }
    try:
        for line in Path("/proc/cpuinfo").read_text().splitlines():
            if line.startswith(("model name", "CPU part")):
                info["cpu_model"] = line.split(":", 1)[1].strip()
                break
    except OSError:
        info["cpu_model"] = platform.processor()
    if shutil.which("nvidia-smi"):
        out = subprocess.run(
            ["nvidia-smi", "--query-gpu=name,memory.total,driver_version", "--format=csv,noheader"],
            capture_output=True,
            text=True,
        ).stdout.strip()
        info["gpu"] = out
    return info


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--target", choices=["llama.cpp", "workers-ai"], default="llama.cpp")
    parser.add_argument("--url", default="http://127.0.0.1:8080", help="llama-server base URL")
    parser.add_argument("--label", required=True, help="free-form run label, e.g. c8g.4xlarge/Q4_K_M")
    parser.add_argument("--instance-type", default=os.environ.get("INSTANCE_TYPE", "local"))
    parser.add_argument("--quant", default=os.environ.get("CLEF_QUANT", "n/a"))
    parser.add_argument("--workloads", default="small,medium,large")
    parser.add_argument("--concurrency", default="1,4", help="comma-separated concurrency levels")
    parser.add_argument("--duration", type=float, default=45.0, help="target seconds per step")
    parser.add_argument("--min-requests", type=int, default=6, help="minimum completed requests per step")
    parser.add_argument("--max-step-seconds", type=float, default=600.0, help="hard cap per step")
    parser.add_argument("--warmup", type=int, default=2, help="untimed requests per workload")
    parser.add_argument("--eval", action="store_true", help="also answer data/eval_cases.json")
    parser.add_argument("--eval-cases", type=Path, default=HERE / "data" / "eval_cases.json")
    parser.add_argument("--server-pid", type=int, default=None, help="llama-server PID for process CPU/RSS")
    parser.add_argument("--startup-seconds", type=float, default=None, help="time from launch to /health 200")
    parser.add_argument("--extra", default="{}", help="JSON object merged into the result metadata")
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    client = make_client(args.target, args.url)
    client.wait_until_ready()

    result: dict[str, Any] = {
        "label": args.label,
        "target": args.target,
        "instance_type": args.instance_type,
        "quant": args.quant,
        "started_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "startup_seconds": args.startup_seconds,
        "host": host_info(),
        "steps": [],
        **json.loads(args.extra),
    }
    if args.target == "llama.cpp":
        try:
            result["server_props"] = {
                k: v
                for k, v in httpx.get(f"{args.url}/props", timeout=5).json().items()
                if k in ("build_info", "model_path", "n_ctx", "total_slots")
            }
        except (httpx.HTTPError, ValueError):
            pass

    levels = [int(c) for c in args.concurrency.split(",") if c]
    for workload in [w for w in args.workloads.split(",") if w]:
        requests = workloads.build(workload)
        with httpx.Client(timeout=client.timeout_s) as http:
            for request in requests[: args.warmup]:
                client.decide(request, http)
        for concurrency in levels:
            print(f"[{args.label}] {workload} c={concurrency} ...", flush=True)
            with ResourceSampler(args.server_pid) as sampler:
                step = asyncio.run(
                    run_step(client, requests, concurrency, args.duration, args.min_requests, args.max_step_seconds)
                )
            step.update({"workload": workload, "resources": sampler.summary()})
            result["steps"].append(step)
            lat = step.get("latency_ms", {})
            print(
                f"    {step['requests']} ok / {step['errors']} err  p50={lat.get('p50')}ms p90={lat.get('p90')}ms"
                f"  {step.get('requests_per_s')} req/s  {step.get('input_tokens_per_s')} tok/s"
                f"  cpu={step['resources']['proc_cpu_pct_avg'] or step['resources']['host_cpu_pct_avg']}%"
                f"  gpu={step['resources']['gpu_util_pct_avg']}%",
                flush=True,
            )

    if args.eval:
        print(f"[{args.label}] eval ...", flush=True)
        result["eval"] = run_eval(client, args.eval_cases)
        print(f"    accuracy {result['eval']['correct']}/{result['eval']['graded_questions']}", flush=True)

    result["finished_at"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, indent=2))
    print(f"wrote {args.out}")


if __name__ == "__main__":
    main()
