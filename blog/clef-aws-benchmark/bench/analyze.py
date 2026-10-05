#!/usr/bin/env python3
"""Turn results/**/*.json into speed-vs-cost tables.

    python bench/analyze.py                    # reads ./results, writes results/summary.md + summary.csv
    python bench/analyze.py --no-live-prices   # skip the AWS Pricing API, use the built-in table

Prices: EC2 on-demand Linux $/hr from the AWS Pricing API (``aws pricing get-products``, needs AWS
credentials) with a fallback table. Workers AI at its list price per input token.

Cost model. Self-hosting bills by the hour whether or not requests arrive, so:

    $ per 1M requests  = $/hr / (sustained req/s * 3600) * 1e6     (box 100% busy)
    $ per 1M tokens    = $/hr / (sustained input tok/s * 3600) * 1e6
    break-even         = requests/month at which one always-on box costs the same as Workers AI
    capacity           = requests/month one box can actually serve at that rate

If break-even > capacity, that box never beats Workers AI on that workload, at any volume.
"""

from __future__ import annotations

import argparse
import csv
import json
import shutil
import statistics
import subprocess
import sys
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))
from clef_client import WORKERS_AI_USD_PER_M_INPUT_TOKENS  # noqa: E402

HOURS_PER_MONTH = 730

# us-east-1 on-demand Linux, USD/hr. Used only when the Pricing API is unavailable; check them
# against https://aws.amazon.com/ec2/pricing/on-demand/ before publishing numbers.
FALLBACK_PRICES = {
    "c7i.4xlarge": 0.714,
    "c7a.4xlarge": 0.82112,
    "c8g.4xlarge": 0.63808,
    "g6.xlarge": 0.8048,
    "g6e.xlarge": 1.861,
    "g5.xlarge": 1.006,
}


def live_price(instance_type: str, region: str) -> float | None:
    if not shutil.which("aws"):
        return None
    filters = {
        "instanceType": instance_type,
        "regionCode": region,
        "operatingSystem": "Linux",
        "tenancy": "Shared",
        "preInstalledSw": "NA",
        "capacitystatus": "Used",
        "licenseModel": "No License required",
    }
    cmd = [
        "aws",
        "pricing",
        "get-products",
        "--region",
        "us-east-1",
        "--service-code",
        "AmazonEC2",
        "--output",
        "json",
        "--filters",
        *[f"Type=TERM_MATCH,Field={k},Value={v}" for k, v in filters.items()],
    ]
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=30, check=True).stdout
        for item in json.loads(out).get("PriceList", []):
            product = json.loads(item)
            for term in product["terms"]["OnDemand"].values():
                for dim in term["priceDimensions"].values():
                    usd = float(dim["pricePerUnit"]["USD"])
                    if usd > 0:
                        return usd
    except (subprocess.SubprocessError, ValueError, KeyError):
        return None
    return None


def load(results_dir: Path) -> tuple[list[dict[str, Any]], dict[str, dict[str, Any]]]:
    runs, phases = [], {}
    for path in sorted(results_dir.rglob("*.json")):
        data = json.loads(path.read_text())
        if path.name == "phases.json":
            phases[data.get("instance_name", path.parent.name)] = data
        elif "steps" in data:
            data["_machine"] = data.get("instance_name") or (
                "workers-ai" if data.get("target") == "workers-ai" else path.parent.name
            )
            runs.append(data)
    return runs, phases


def fmt(value: Any, digits: int = 1) -> str:
    if value is None:
        return "-"
    if isinstance(value, float):
        if abs(value) >= 1e6:
            return f"{value / 1e6:,.1f}M"
        if abs(value) >= 1000:
            return f"{value:,.0f}"
        if 0 < abs(value) < 1:
            return f"{value:.3g}"  # 0.0412 req/s, not 0.0
        return f"{value:,.{digits}f}"
    if isinstance(value, int) and not isinstance(value, bool):
        return f"{value:,}"
    return str(value)


def table(headers: list[str], rows: list[list[Any]]) -> str:
    lines = ["| " + " | ".join(headers) + " |", "|" + "|".join("---" for _ in headers) + "|"]
    lines += ["| " + " | ".join(fmt(c) if not isinstance(c, str) else c for c in row) + " |" for row in rows]
    return "\n".join(lines)


def compare(answers: dict[str, Any], reference: dict[str, Any]) -> tuple[list[float], int, int]:
    """Absolute probability differences over every option, and how many argmax picks agree."""
    deltas, same, total = [], 0, 0
    for qid, ans in answers.items():
        ref = reference.get(qid)
        if not ref:
            continue
        total += 1
        if ans["type"] == "noul":
            deltas.append(abs(ans["noul"] - ref["noul"]))
            same += (ans["noul"] >= 0.5) == (ref["noul"] >= 0.5)
            continue
        for option, p in ans["probabilities"].items():
            deltas.append(abs(p - ref["probabilities"].get(option, 0.0)))
        same += max(ans["probabilities"], key=ans["probabilities"].get) == max(
            ref["probabilities"], key=ref["probabilities"].get
        )
    return deltas, same, total


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("results", nargs="?", type=Path, default=Path("results"))
    parser.add_argument("--region", default="us-east-1")
    parser.add_argument("--no-live-prices", action="store_true")
    parser.add_argument(
        "--workers-ai-price", type=float, default=WORKERS_AI_USD_PER_M_INPUT_TOKENS, help="USD per 1M input tokens"
    )
    args = parser.parse_args()

    runs, phases = load(args.results)
    if not runs:
        raise SystemExit(f"no results under {args.results}/")

    prices: dict[str, tuple[float | None, str]] = {}
    for itype in sorted({r["instance_type"] for r in runs if r.get("target") != "workers-ai"}):
        price = None if args.no_live_prices else live_price(itype, args.region)
        prices[itype] = (price, "pricing-api") if price else (FALLBACK_PRICES.get(itype), "fallback")

    out: list[str] = ["# Clef-flash on AWS: results", ""]
    csv_rows: list[dict[str, Any]] = []

    # ---- latency + throughput + cost ------------------------------------------------------
    perf_rows, cost_rows = [], []
    for run in sorted(runs, key=lambda r: (r["_machine"], r["quant"])):
        is_cf = run.get("target") == "workers-ai"
        price = None if is_cf else prices.get(run["instance_type"], (None, ""))[0]
        workloads = sorted({s["workload"] for s in run["steps"]}, key=["small", "medium", "large"].index)
        for workload in workloads:
            steps = [s for s in run["steps"] if s["workload"] == workload and s.get("requests")]
            if not steps:
                continue
            c1 = min(steps, key=lambda s: s["concurrency"])
            best = max(steps, key=lambda s: s["requests_per_s"])
            tokens = c1["input_tokens_mean"]
            res = c1["resources"]
            row = {
                "machine": run["_machine"],
                "instance_type": run["instance_type"],
                "quant": run["quant"],
                "workload": workload,
                "input_tokens": tokens,
                "p50_ms": c1["latency_ms"]["p50"],
                "p90_ms": c1["latency_ms"]["p90"],
                "p99_ms": c1["latency_ms"]["p99"],
                "max_req_s": best["requests_per_s"],
                "max_tok_s": best["input_tokens_per_s"],
                "best_concurrency": best["concurrency"],
                "startup_s": run.get("startup_seconds"),
                "cpu_pct": res.get("proc_cpu_pct_avg") or res.get("host_cpu_pct_avg"),
                "rss_mb": res.get("proc_rss_mb_max"),
                "gpu_pct": res.get("gpu_util_pct_avg"),
                "vram_mb": res.get("gpu_mem_used_mb_max"),
                "power_w": res.get("gpu_power_w_avg"),
                "usd_per_hr": price,
            }
            cf_per_req = tokens * args.workers_ai_price / 1e6
            if is_cf:
                row["usd_per_m_req"] = cf_per_req * 1e6
                row["usd_per_m_tok"] = args.workers_ai_price
            elif price:
                row["usd_per_m_req"] = price / (best["requests_per_s"] * 3600) * 1e6
                row["usd_per_m_tok"] = price / (best["input_tokens_per_s"] * 3600) * 1e6
                row["capacity_req_month"] = best["requests_per_s"] * 3600 * HOURS_PER_MONTH
                row["breakeven_req_month"] = price * HOURS_PER_MONTH / cf_per_req
            csv_rows.append(row)
            perf_rows.append(
                [
                    row["machine"],
                    row["quant"],
                    workload,
                    round(tokens),
                    row["p50_ms"],
                    row["p90_ms"],
                    row["max_req_s"],
                    row["max_tok_s"],
                    row["startup_s"],
                ]
            )
            if is_cf:
                cost_rows.append(
                    [
                        row["machine"],
                        row["quant"],
                        workload,
                        "per token",
                        row["usd_per_m_req"],
                        row["usd_per_m_tok"],
                        "-",
                        "-",
                        "-",
                    ]
                )
            elif price:
                wins = row["breakeven_req_month"] <= row["capacity_req_month"]
                cost_rows.append(
                    [
                        row["machine"],
                        row["quant"],
                        workload,
                        price,
                        row["usd_per_m_req"],
                        row["usd_per_m_tok"],
                        row["capacity_req_month"],
                        row["breakeven_req_month"],
                        "yes" if wins else "never",
                    ]
                )

    out += [
        "## Speed",
        "",
        "Latency at concurrency 1. Throughput is the best step: llama.cpp evaluates one Clef prompt per batch, "
        "so extra concurrency queues rather than adding throughput.",
        "",
        table(
            ["machine", "quant", "workload", "input tok", "p50 ms", "p90 ms", "max req/s", "max tok/s", "cold start s"],
            perf_rows,
        ),
        "",
    ]
    out += [
        "## Cost",
        "",
        f"Self-hosted cost assumes the box is 100% busy. Workers AI: ${args.workers_ai_price}/1M input tokens. "
        "*Beats Workers AI* = an always-on box gets cheaper than Workers AI before it runs out of capacity.",
        "",
        table(
            [
                "machine",
                "quant",
                "workload",
                "$/hr",
                "$/1M req",
                "$/1M tok",
                "capacity req/mo",
                "break-even req/mo",
                "beats Workers AI",
            ],
            cost_rows,
        ),
        "",
    ]
    out += [
        "Prices: "
        + ", ".join(f"{k} ${v[0]}/hr ({v[1]})" if v[0] else f"{k} unknown (no cost rows)" for k, v in prices.items()),
        "",
    ]

    # ---- resources -------------------------------------------------------------------------
    res_rows = [
        [r["machine"], r["quant"], r["workload"], r["cpu_pct"], r["rss_mb"], r["gpu_pct"], r["vram_mb"], r["power_w"]]
        for r in csv_rows
        if r["machine"] != "workers-ai"
    ]
    out += [
        "## Resources",
        "",
        "Averages over the concurrency-1 step. CPU % is of the whole machine.",
        "",
        table(["machine", "quant", "workload", "llama-server CPU %", "RSS MB", "GPU %", "VRAM MB", "GPU W"], res_rows),
        "",
    ]

    # ---- quality ----------------------------------------------------------------------------
    evals = {(r["_machine"], r["quant"]): r["eval"] for r in runs if r.get("eval")}
    if evals:
        reference_key = next((k for k in evals if k[0] == "workers-ai"), None)
        q_rows = []
        for (machine, quant), ev in sorted(evals.items()):
            ref_key = reference_key or ((machine, "BF16") if (machine, "BF16") in evals else None)
            deltas, same, total = [], 0, 0
            if ref_key and ref_key != (machine, quant):
                ref_rows = {row["id"]: row for row in evals[ref_key]["rows"] if "answers" in row}
                for row in ev["rows"]:
                    if "answers" in row and row["id"] in ref_rows:
                        d, s, t = compare(row["answers"], ref_rows[row["id"]]["answers"])
                        deltas += d
                        same += s
                        total += t
            q_rows.append(
                [
                    machine,
                    quant,
                    f"{ev['correct']}/{ev['graded_questions']}",
                    f"{ref_key[0]}/{ref_key[1]}" if ref_key and ref_key != (machine, quant) else "-",
                    round(statistics.fmean(deltas), 4) if deltas else None,
                    round(max(deltas), 4) if deltas else None,
                    f"{same}/{total}" if total else "-",
                ]
            )
        out += [
            "## Answer quality",
            "",
            "Accuracy on the labeled cases in `bench/data/eval_cases.json`, and drift against a reference run "
            "(Workers AI if present, else BF16 on the same machine).",
            "",
            table(["machine", "quant", "correct", "reference", "mean abs dP", "max abs dP", "same answer"], q_rows),
            "",
        ]

    # ---- setup -------------------------------------------------------------------------------
    if phases:
        keys = ["packages", "llama_cpp_build", "python_env", "model_download", "benchmark"]
        rows = []
        for machine, ph in sorted(phases.items()):
            total_s = sum(ph.get(k, 0) for k in keys)
            price = prices.get(ph.get("instance_type", ""), (None, ""))[0]
            rows.append(
                [
                    machine,
                    *[ph.get(k) for k in keys],
                    round(total_s / 60, 1),
                    round(price * total_s / 3600, 2) if price else None,
                ]
            )
        out += [
            "## Setup and run time",
            "",
            "Seconds per bootstrap phase, and what the whole run cost on-demand.",
            "",
            table(["machine", *keys, "total min", "run $"], rows),
            "",
        ]

    summary = "\n".join(out)
    (args.results / "summary.md").write_text(summary)
    with (args.results / "summary.csv").open("w", newline="") as handle:
        fields = sorted({k for row in csv_rows for k in row})
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(csv_rows)
    print(summary)
    print(f"\nwrote {args.results / 'summary.md'} and {args.results / 'summary.csv'}")


if __name__ == "__main__":
    main()
