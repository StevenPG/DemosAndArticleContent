"""
CPU-bound strong-scaling test: a fixed amount of pure-Python work split across
1..N threads. On a GIL build the wall time stays flat (or gets worse); on a
free-threaded build it should fall with the thread count.

    python threads/scale.py --threads 1 2 4 --work 4000000

Prints one JSON object. Also reports whether the GIL is actually disabled at
runtime (an extension that is not free-threading-safe can re-enable it) and
whether the experimental JIT is available/enabled.
"""
import argparse
import json
import sys
import sysconfig
import threading
import time
from concurrent.futures import ProcessPoolExecutor


def collatz_steps(start: int, stop: int) -> int:
    """Total Collatz steps for every n in [start, stop). Pure Python, no allocation-heavy objects."""
    total = 0
    for n in range(start, stop):
        steps = 0
        while n != 1:
            n = n // 2 if n % 2 == 0 else 3 * n + 1
            steps += 1
        total += steps
    return total


def chunks(work: int, parts: int) -> list[tuple[int, int]]:
    size = work // parts
    return [(1 + i * size, 1 + (i + 1) * size) for i in range(parts)]


def run_threads(work: int, threads: int) -> float:
    results = [0] * threads
    def worker(i: int, lo: int, hi: int) -> None:
        results[i] = collatz_steps(lo, hi)
    pool = [threading.Thread(target=worker, args=(i, lo, hi)) for i, (lo, hi) in enumerate(chunks(work, threads))]
    t0 = time.perf_counter()
    for t in pool:
        t.start()
    for t in pool:
        t.join()
    return time.perf_counter() - t0


def run_processes(work: int, processes: int) -> float:
    with ProcessPoolExecutor(processes) as pool:
        pool.submit(int).result()  # start the workers before timing
        t0 = time.perf_counter()
        list(pool.map(collatz_steps, *zip(*chunks(work, processes))))
        return time.perf_counter() - t0


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--threads", type=int, nargs="+", default=[1, 2, 4])
    parser.add_argument("--work", type=int, default=2_000_000)
    parser.add_argument("--repeat", type=int, default=3)
    parser.add_argument("--processes", action="store_true", help="also time a ProcessPoolExecutor at max threads")
    args = parser.parse_args()

    jit = getattr(sys, "_jit", None)
    out = {
        "python": sys.version.split()[0],
        "free_threaded_build": bool(sysconfig.get_config_var("Py_GIL_DISABLED")),
        "gil_enabled": sys._is_gil_enabled() if hasattr(sys, "_is_gil_enabled") else True,
        "jit_available": bool(jit and jit.is_available()),
        "jit_enabled": bool(jit and jit.is_enabled()),
        "work": args.work,
        "threads": {},
    }
    for n in args.threads:
        out["threads"][n] = min(run_threads(args.work, n) for _ in range(args.repeat))
    if args.processes:
        out["processes"] = {max(args.threads): min(run_processes(args.work, max(args.threads)) for _ in range(args.repeat))}
    print(json.dumps(out))


if __name__ == "__main__":
    main()
