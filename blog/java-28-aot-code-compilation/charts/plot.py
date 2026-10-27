#!/usr/bin/env python3
"""Turn results/raw into charts (SVG) and results/summary.md (the tables quoted in the post).

Usage: python3 charts/plot.py            (needs matplotlib)
"""
import csv
import glob
import json
import os
import re
import statistics as st
from collections import defaultdict

import matplotlib

matplotlib.use("svg")
import matplotlib.pyplot as plt  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "results", "raw")
OUT = os.path.join(ROOT, "charts")
SUMMARY = os.path.join(ROOT, "results", "summary.md")

# Fixed identity per configuration across EVERY chart (colour follows the entity, never rank).
SURFACE, INK, INK2, GRID = "#fcfcfb", "#0b0b0b", "#52514e", "#e4e3df"
STYLE = {
    "28:g1:code": ("JDK 28 + AOT code (JEP 544)", "#2a78d6", "-"),
    "28:g1:cache": ("JDK 28 cache, no AOT code", "#eb6834", "--"),
    "native": ("GraalVM native image", "#1baf7a", "-."),
    "25:g1:cache": ("JDK 25 AOT cache", "#eda100", ":"),
    "26:g1:cache": ("JDK 26 AOT cache", "#e87ba4", ":"),
    "27:g1:cache": ("JDK 27 AOT cache", "#6250d6", ":"),
    "28:g1:none": ("JDK 28, no cache (JIT only)", "#8a8984", "-"),
}
LABEL = {k: v[0] for k, v in STYLE.items()}

plt.rcParams.update({
    "font.family": "DejaVu Sans", "font.size": 10.5,
    "axes.edgecolor": GRID, "axes.labelcolor": INK2, "axes.titlecolor": INK,
    "xtick.color": INK2, "ytick.color": INK2, "axes.grid": True, "grid.color": GRID,
    "grid.linewidth": 0.8, "axes.spines.top": False, "axes.spines.right": False,
    "figure.facecolor": SURFACE, "axes.facecolor": SURFACE, "legend.frameon": False,
    "lines.linewidth": 2, "svg.fonttype": "none",
})

md = []  # summary.md accumulator


def style(cfg):
    return STYLE.get(cfg, (cfg, "#8a8984", "-"))


def save(fig, name):
    fig.savefig(os.path.join(OUT, name), bbox_inches="tight", facecolor=SURFACE)
    plt.close(fig)
    print("wrote", name)


def read_csv(path):
    with open(path) as f:
        return list(csv.DictReader(f))


# ---------------------------------------------------------------- startup (hyperfine)
def startup_chart(tag, app, title, fname):
    path = os.path.join(RAW, f"startup-{tag}-{app}.json")
    if not os.path.exists(path):
        return None
    res = json.load(open(path))["results"]
    # hyperfine 2.x: per-metric summaries (wall, cpu, peak RSS) under "summary"
    rows = []
    for r in res:
        w = r["summary"]["time_wall_clock"]
        rows.append((r["name"], w["median"] * 1000, w["mean"] * 1000, w["stddev"] * 1000, w["min"] * 1000,
                     w["max"] * 1000, r["summary"]["time_cpu"]["median"],
                     r["summary"]["memory_peak_resident"]["median"] / 1024 / 1024))
    base = next((r for r in rows if r[0].endswith(":none")), rows[0])

    fig, ax = plt.subplots(figsize=(8.2, 0.52 * len(rows) + 1.1))
    for i, (cfg, med, mean, sd, lo, hi, _, _) in enumerate(rows):
        y = len(rows) - 1 - i
        ax.barh(y, med, height=0.62, color=style(cfg)[1])
        ax.errorbar(med, y, xerr=[[med - lo], [hi - med]], color=INK2, capsize=3, lw=1)
        pct = "" if cfg == base[0] else f"  ({(1 - med / base[1]) * 100:+.0f}%)".replace("+", "−", 1) \
            if med < base[1] else f"  (+{(med / base[1] - 1) * 100:.0f}%)"
        ax.text(hi + base[1] * 0.012, y, f"{med:,.0f} ms{pct}", va="center", color=INK, fontsize=9.5)
    ax.set_yticks(range(len(rows)), [LABEL.get(r[0], r[0]) for r in reversed(rows)])
    ax.set_xlabel("median wall time, ms (whisker = min…max)")
    ax.set_xlim(0, max(r[5] for r in rows) * 1.3)
    ax.grid(axis="y", visible=False)
    ax.set_title(title, loc="left", fontsize=12, pad=10)
    save(fig, fname)

    md.append(f"\n### {title}\n\n| Config | Median | Mean ± σ | Min | Max | vs JIT-only | CPU time | Peak RSS |\n"
              "|---|---:|---:|---:|---:|---:|---:|---:|")
    for cfg, med, mean, sd, lo, hi, cpu, rss in rows:
        md.append(f"| {LABEL.get(cfg, cfg)} | {med:,.0f} ms | {mean:,.0f} ± {sd:,.0f} | {lo:,.0f} | {hi:,.0f} | "
                  f"{(med / base[1] - 1) * 100:+.1f}% | {cpu:,.2f} s | {rss:,.0f} MB |")
    return rows


# ---------------------------------------------------------------- batch (iterations)
def batch_series(tag, mode):
    series = defaultdict(list)
    for p in glob.glob(os.path.join(RAW, f"batch-{tag}-{mode}-*-r*.csv")):
        cfg = re.match(rf"batch-{re.escape(tag)}-{mode}-(.+)-r\d+\.csv", os.path.basename(p)).group(1)
        series[cfg.replace("_", ":")].append([float(r["millis"]) for r in read_csv(p)])
    # median per iteration across repetitions
    return {cfg: [st.median(col) for col in zip(*runs)] for cfg, runs in series.items()}


def wall_times(tag, mode):
    out = defaultdict(list)
    p = os.path.join(RAW, "batch-summary.csv")
    if os.path.exists(p):
        for row in csv.reader(open(p)):
            if row[0] == tag and row[1] == mode:
                out[row[2]].append(int(row[4]))
    return {k: st.median(v) for k, v in out.items()}


def batch_chart(tag, mode, title, fname, order, labels=None, log=False):
    s = batch_series(tag, mode)
    if not s:
        return None
    labels = labels or {}
    walls = wall_times(tag, mode)
    fig, ax = plt.subplots(figsize=(8.2, 4.4))
    for cfg in [c for c in order if c in s]:
        name, color, ls = style(cfg)
        ys = s[cfg]
        ax.plot(range(1, len(ys) + 1), ys, color=color, ls=ls, marker="o", ms=4, label=labels.get(cfg, name))
    if log:
        ax.set_yscale("log")
    ax.set_xlabel("iteration (same work every iteration)")
    ax.set_ylabel("iteration time, ms (median of runs)")
    ax.set_ylim(bottom=0 if not log else None)
    ax.legend(loc="upper right", fontsize=9)
    ax.set_title(title, loc="left", fontsize=12, pad=10)
    save(fig, fname)

    steady = {cfg: st.median(ys[-5:]) for cfg, ys in s.items()}
    md.append(f"\n### {title}\n\n| Config | Iter 1 | Iter 2 | Iter 3 | Iter 5 | Steady (last 5) | Iters to ≤1.1× steady | Wall (median) |\n"
              "|---|---:|---:|---:|---:|---:|---:|---:|")
    for cfg in [c for c in order if c in s]:
        ys = s[cfg]
        ref = steady[cfg]
        n = next((i + 1 for i in range(len(ys)) if all(y <= ref * 1.1 for y in ys[i:i + 3])), len(ys))
        md.append(f"| {labels.get(cfg, LABEL.get(cfg, cfg))} | {ys[0]:,.0f} | {ys[1]:,.0f} | {ys[2]:,.0f} | {ys[min(4, len(ys) - 1)]:,.0f} | "
                  f"{ref:,.0f} | {n} | {walls.get(cfg, 0):,.0f} ms |")
    return s


# ---------------------------------------------------------------- curves (loadgen)
def curve_series(tag):
    series = defaultdict(list)
    for p in glob.glob(os.path.join(RAW, f"curve-{tag}-*-r*.csv")):
        cfg = re.match(rf"curve-{re.escape(tag)}-(.+)-r\d+\.csv", os.path.basename(p)).group(1).replace("_", ":")
        series[cfg].append(read_csv(p))
    out = {}
    for cfg, runs in series.items():
        n = min(len(r) for r in runs)
        rps = [st.median(float(r[i]["requests"]) for r in runs) for i in range(n)]
        p99 = [st.median(float(r[i]["p99_ms"]) for r in runs if float(r[i]["requests"]) > 0) if
               any(float(r[i]["requests"]) > 0 for r in runs) else None for i in range(n)]
        out[cfg] = (rps, p99)
    return out


def first_ok(tag):
    out = defaultdict(list)
    p = os.path.join(RAW, "curve-summary.csv")
    if os.path.exists(p):
        for row in csv.reader(open(p)):
            if row[0] == tag:
                m = re.search(r"first_ok_ms=([\d.]+)", ",".join(row))
                rss = re.search(r"rss_kb=(\d+)", ",".join(row))
                out[row[1]].append((float(m.group(1)) / 1000 if m else None, int(rss.group(1)) if rss else 0))
    return {k: (st.median(x[0] for x in v), st.median(x[1] for x in v)) for k, v in out.items()}


def time_to(rps, target):
    """First second from which throughput stays >= target for 5 consecutive seconds."""
    for i in range(len(rps)):
        if all(r >= target for r in rps[i:i + 5]) and i + 5 <= len(rps):
            return i + 1
    return None


def curve_chart(tag, title, fname, order, labels=None, smooth=3, steady_window=30):
    s = curve_series(tag)
    if not s:
        return None
    labels = labels or {}
    jit = next((c for c in s if c.endswith(":none")), None)
    peak = st.mean(s[jit][0][-steady_window:]) if jit else max(st.mean(v[0][-steady_window:]) for v in s.values())

    fig, (ax, ax2) = plt.subplots(2, 1, figsize=(8.6, 6.8), sharex=True, gridspec_kw={"height_ratios": [3, 2]})
    for cfg in [c for c in order if c in s]:
        name, color, ls = style(cfg)
        rps, p99 = s[cfg]
        sm = [st.mean(rps[max(0, i - smooth + 1):i + 1]) for i in range(len(rps))]
        x = range(1, len(rps) + 1)
        ax.plot(x, sm, color=color, ls=ls, label=labels.get(cfg, name))
        ax2.plot(x, [p if p is not None else float("nan") for p in p99], color=color, ls=ls, lw=1.6)
    ax.axhline(peak, color=INK2, lw=1, ls=(0, (2, 3)))
    ax.text(len(next(iter(s.values()))[0]), peak, " JIT-only steady state", color=INK2, fontsize=8.5, va="bottom",
            ha="right")
    ax.set_ylabel(f"requests / s ({smooth}s rolling mean)")
    ax.set_ylim(bottom=0)
    ax.legend(loc="lower right", fontsize=9)
    ax.set_title(title, loc="left", fontsize=12, pad=10)
    ax2.set_yscale("log")
    ax2.set_ylabel("p99 latency, ms (log)")
    ax2.set_xlabel("seconds since `java` was launched")
    save(fig, fname)

    fo = first_ok(tag)
    md.append(f"\n### {title}\n\n| Config | First response | 50% of JIT peak | 90% of JIT peak | Requests in first 30 s | "
              f"Requests in first 60 s | Steady req/s (last {steady_window}s) | p99 @ 10–20 s | RSS at end |\n"
              "|---|---:|---:|---:|---:|---:|---:|---:|---:|")
    for cfg in [c for c in order if c in s]:
        rps, p99 = s[cfg]
        f, rss = fo.get(cfg, (None, 0))
        t50, t90 = time_to(rps, peak * 0.5), time_to(rps, peak * 0.9)
        p99w = [p for p in p99[9:20] if p is not None]
        md.append(f"| {labels.get(cfg, LABEL.get(cfg, cfg))} | {f:.2f} s | {t50 or '–'} s | {t90 or '–'} s | "
                  f"{sum(rps[:30]):,.0f} | {sum(rps[:60]):,.0f} | {st.mean(rps[-steady_window:]):,.0f} | "
                  f"{st.median(p99w) if p99w else float('nan'):,.1f} ms | {rss / 1024:,.0f} MB |")
    return s


# ---------------------------------------------------------------- GC x CPU matrix
def gc_matrix():
    gcs = [("serial", "Serial"), ("g1", "G1"), ("z", "ZGC"), ("shen", "Shenandoah")]
    rows = []
    for n in (1, 2):
        p = os.path.join(RAW, f"startup-gc-{n}cpu-spring-rest.json")
        if not os.path.exists(p):
            continue
        res = {r["name"]: r["summary"]["time_wall_clock"]["median"] * 1000 for r in json.load(open(p))["results"]}
        curves = curve_series(f"gc-{n}cpu")
        for gc, gname in gcs:
            none, cache, code = (res.get(f"28:{gc}:{v}") for v in ("none", "cache", "code"))
            if none is None:
                continue
            b = {v: batch_series(f"gc-{n}cpu-{gc}", "adsb").get(f"28:{gc}:{v}") for v in ("none", "cache", "code")}
            req = {v: sum(curves[f"28:{gc}:{v}"][0][:60]) if f"28:{gc}:{v}" in curves else None
                   for v in ("none", "cache", "code")}
            rows.append((n, gname, none, cache, code, b, req))
    if not rows:
        return
    md.append("\n### GC × CPU matrix (spring-rest startup, warmup-cpu iteration 1, requests served in first 60 s)\n\n"
              "| CPUs | GC | Startup: JIT | cache | +AOT code | Δ code vs JIT | adsb iter 1: JIT | cache | +AOT code | "
              "60 s requests: JIT | cache | +AOT code |\n|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for n, g, none, cache, code, b, req in rows:
        it1 = [f"{b[v][0]:,.0f} ms" if b[v] else "–" for v in ("none", "cache", "code")]
        rq = [f"{req[v]:,.0f}" if req[v] else "–" for v in ("none", "cache", "code")]
        md.append(f"| {n} | {g} | {none:,.0f} ms | {cache:,.0f} ms | {code:,.0f} ms | {(code / none - 1) * 100:+.0f}% | "
                  f"{' | '.join(it1)} | {' | '.join(rq)} |")

    # grouped bars: startup reduction vs JIT-only, per GC, per CPU count
    fig, axes = plt.subplots(1, 2, figsize=(9.2, 3.9), sharey=True)
    for ax, n in zip(axes, (1, 2)):
        sub = [r for r in rows if r[0] == n]
        x = range(len(sub))
        w = 0.36
        cache_red = [(1 - r[3] / r[2]) * 100 for r in sub]
        code_red = [(1 - r[4] / r[2]) * 100 for r in sub]
        ax.bar([i - w / 2 - 0.01 for i in x], cache_red, w, color=STYLE["28:g1:cache"][1], label="cache, no AOT code")
        ax.bar([i + w / 2 + 0.01 for i in x], code_red, w, color=STYLE["28:g1:code"][1], label="+ AOT code (JEP 544)")
        for i, (a, c) in enumerate(zip(cache_red, code_red)):
            ax.text(i - w / 2, a + 1, f"{a:.0f}%", ha="center", fontsize=8.5, color=INK)
            ax.text(i + w / 2, c + 1, f"{c:.0f}%", ha="center", fontsize=8.5, color=INK)
        ax.set_xticks(list(x), [r[1] for r in sub])
        ax.set_title(f"{n} CPU" + ("s" if n > 1 else ""), loc="left", fontsize=11)
        ax.grid(axis="x", visible=False)
        ax.set_ylim(0, 100)
    axes[0].set_ylabel("startup time saved vs JIT-only, %")
    axes[1].legend(loc="upper right", fontsize=9)
    fig.suptitle("spring-rest startup reduction by collector and CPU count (JDK 28)", x=0.07, ha="left", fontsize=12)
    save(fig, "gc-matrix-startup.svg")


# ---------------------------------------------------------------- training cost
def training_table():
    p = os.path.join(RAW, "training.csv")
    if not os.path.exists(p):
        return
    rows = {}
    for app, cfg, training, ms, size in csv.reader(open(p)):
        rows[(app, cfg, training)] = (int(ms), int(size))  # last write wins (re-trains)
    md.append("\n### Training runs (one-step -XX:AOTCacheOutput, includes workload time)\n\n"
              "| App | Config | Training workload | Wall time | Cache size |\n|---|---|---|---:|---:|")
    for (app, cfg, training), (ms, size) in sorted(rows.items()):
        md.append(f"| {app} | {cfg} | {training} | {ms / 1000:,.1f} s | {size / 1024 / 1024:,.0f} MB |")


if __name__ == "__main__":
    ladder = ["28:g1:none", "25:g1:cache", "26:g1:cache", "27:g1:cache", "28:g1:cache", "28:g1:code", "native"]
    md.append("# Results summary (generated by charts/plot.py)\n")
    env = os.path.join(ROOT, "results", "environment.txt")
    if os.path.exists(env):
        md.append("```\n" + open(env).read().strip() + "\n```")
    startup_chart("ladder", "spring-rest", "Spring Boot 4.1: time to ApplicationReady (2 CPUs)", "startup-spring.svg")
    startup_chart("ladder", "cli-tool", "picocli CLI: total runtime of one invocation (2 CPUs)", "startup-cli.svg")
    batch_chart("ladder", "adsb", "ADS-B batch: iteration time, first 30 iterations", "batch-adsb.svg", ladder)
    right = lambda: STYLE["28:g1:code"]
    wrong = ("", "#6250d6", "--")
    STYLE.update({"28:g1:cache:javac": STYLE["28:g1:cache"], "28:g1:code:javac": right()})
    batch_chart("jep", "javac", "javac × 50 files, 20 iterations (the JEP 544 benchmark)", "batch-javac.svg",
                ["28:g1:none", "28:g1:cache:javac", "28:g1:code:javac"],
                {"28:g1:cache:javac": LABEL["28:g1:cache"], "28:g1:code:javac": LABEL["28:g1:code"]})
    curve_chart("main", "Spring Boot under load: the first 120 seconds (2 CPUs)", "curve-main.svg",
                ["28:g1:none", "25:g1:cache", "28:g1:cache", "28:g1:code", "native"])

    # Training mismatch: the correctly trained cache keeps the JEP 544 blue, the wrong one is violet.
    STYLE["28:g1:code:fleet"] = wrong
    curve_chart("mismatch", "Training mismatch: cache trained on fleet endpoints only, load also hits /tracks",
                "curve-mismatch.svg", ["28:g1:none", "28:g1:code:fleet", "28:g1:code"],
                {"28:g1:code:fleet": "AOT code, trained on the WRONG endpoints",
                 "28:g1:code": "AOT code, trained on the real mix"})
    STYLE.update({"28:g1:code:adsb": wrong, "28:g1:code:javac": right()})
    batch_chart("mismatch", "javac", "Mismatch: javac with a cache trained on the ADS-B workload",
                "batch-mismatch-javac.svg", ["28:g1:none", "28:g1:code:adsb", "28:g1:code:javac"],
                {"28:g1:code:adsb": "AOT code, trained on adsb (wrong)",
                 "28:g1:code:javac": "AOT code, trained on javac"})
    STYLE.update({"28:g1:code:javac": wrong, "28:g1:code:adsb": right()})
    batch_chart("mismatch", "adsb", "Mismatch: ADS-B with a cache trained on javac",
                "batch-mismatch-adsb.svg", ["28:g1:none", "28:g1:code:javac", "28:g1:code:adsb"],
                {"28:g1:code:javac": "AOT code, trained on javac (wrong)",
                 "28:g1:code:adsb": "AOT code, trained on adsb"})
    gc_matrix()
    training_table()
    open(SUMMARY, "w").write("\n".join(md) + "\n")
    print("wrote", SUMMARY)
