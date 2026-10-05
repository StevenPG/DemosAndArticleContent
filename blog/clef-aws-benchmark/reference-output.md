# Reference output: full bootstrap run on a 4-vCPU container

This is `scripts/remote/bootstrap.sh` run end to end, exactly as an EC2 instance runs it, on a 4-vCPU
x86_64 cloud container: Intel Xeon @ 2.80 GHz with AVX-512 VNNI, 15.7 GB RAM, no GPU. The
container has no systemd and no AWS CLI, so three commands were replaced by recording stubs placed
first on `PATH`:

- **`aws`**: logged its arguments, and copied each `s3 sync` into a local "bucket" directory.
- **`systemctl`**: logged its arguments.
- **`shutdown`**: logged its arguments.

Everything else ran for real: apt, the llama.cpp build, the venv, the benchmark, the eval and
`analyze.py`.

Raw results: [`results/container-4vcpu/`](results/container-4vcpu/). Generated tables:
[`results/summary.md`](results/summary.md).

**These are not EC2 numbers.** They show the pipeline working and what a small CPU does with
Clef-flash.

## Prompts

`results/container-4vcpu/prompts/` holds the literal prompt for the first request of each
workload, plus one eval case, rendered with `bench/render_prompt.py`. Each was verified
token-for-token against `llama-server` (small 508, large 4,322, medium 1,862).

This run predates `clef_client.render_state`. During it, llama.cpp rounded the decimal amounts in
the medium invoice state to 6 significant digits, which removed 114 tokens from that prompt. The
small and large states contain no decimals, and the eval cases' amounts (`1250.0`, `84.5`,
`48200.0`) keep their values either way. Re-running the medium request with the fix changed
`duplicate` from 0.525 to 0.761; the other medium answers moved by less than 0.02.

## Configuration

```
QUANTS="Q4_K_M"   LLAMA_CPP_TAG=b11401   AUTO_RUN=true   SHUTDOWN_MINUTES=300
BENCH_ARGS="--concurrency 1,2 --duration 1 --min-requests 3 --warmup 1 --max-step-seconds 900"
```

Only Q4_K_M was run. Q8_0 (9.7 GB) would leave too little headroom in 15.7 GB of RAM, given the
prompt-dependent growth below, and BF16 (18.2 GB) does not fit at all.

## What bootstrap did

| Phase | Seconds | Notes |
|---|---|---|
| packages | 13 | apt with `DPkg::Lock::Timeout`; nothing held the lock here |
| llama_cpp_build | 210 | static CPU build of `llama-server` at `b11401`, 18 MB binary, `/opt/llama.cpp/TAG` written |
| python_env | 8 | venv + httpx, psutil |
| model_download | 0 | the GGUF was pre-placed; on EC2 this is a 6.5 GB download |
| benchmark | 3,309 | one quant, three workloads × two concurrency levels, plus 24 eval cases |
| **total** | **59 min** | |

Calls the stubs recorded, in order:

```
shutdown -h +300 clef-bench safety-net shutdown
aws s3 sync … /opt/clef-bench/results/container-4vcpu s3://fake-bucket/results/container-4vcpu   # after setup
aws s3 sync … (same)                                                                             # after setup
systemctl stop clef-server.service                                                               # before benchmark
aws s3 sync … (same)                                                                             # after the Q4_K_M quant
systemctl daemon-reload
systemctl enable --now clef-server.service
aws s3 sync … (same)                                                                             # final, STATUS=DONE
```

The "bucket" ended up holding `STATUS` (`DONE`), `phases.json`, `bootstrap.log`, `Q4_K_M.json` and
`Q4_K_M.server.log`.

## Speed

| Workload | Prompt tokens | Concurrency | Requests | p50 | p90 | req/s | Prompt tok/s |
|---|---|---|---|---|---|---|---|
| small | 508 | 1 | 3 | 23.7 s | 24.1 s | 0.042 | 21.6 |
| small | 508 | 2 | 4 | 46.3 s | 46.4 s | 0.043 | 21.9 |
| medium | 1,605 | 1 | 3 | 75.3 s | 79.9 s | 0.013 | 21.6 |
| medium | 1,605 | 2 | 4 | 145.6 s | 154.0 s | 0.013 | 21.5 |
| large | 4,449 | 1 | 3 | 214.1 s | 224.6 s | 0.005 | 20.4 |
| large | 4,449 | 2 | 4 | 427.4 s | 433.2 s | 0.005 | 20.5 |

- **Zero errors** across all 21 timed requests.
- **Prefill speed is flat:** 20–22 prompt tokens/s at every size. Latency is linear in prompt
  length, so the ~300-token Clef wrapper and the joint head add nothing visible.
- **Concurrency 2 doubles latency at identical throughput**, at every size. llama.cpp evaluates one
  Clef prompt per batch.
- **Cold start was 482 s.** This time the page-cache drop worked, so the 6.5 GB model was read from
  this container's disk at about 13.5 MB/s. An earlier warm-cache start took 13 s. On EC2 gp3 at
  the configured 500 MB/s, expect roughly 15–20 s.

## Resources

| Workload | llama-server CPU (of 4 vCPUs) | Peak RSS |
|---|---|---|
| small | 92.5% | 9.8 GB |
| medium | 93.1% | 11.2 GB |
| large | 91.5% | 13.6 GB |

The CPU is saturated by prefill. RSS is the repacked weights plus a buffer that grows with prompt
length, about +3.8 GB from 508 to 4,449 tokens.

## Answer quality

**37/38** graded answers correct on the 24 labeled cases, with a median latency of 11.5 s at a mean
of 245 prompt tokens. The single miss is a genuine model error. For `fact-arithmetic-no`, the order
does not fit on one truck (48 × 20 = 960 < 1,000), but the model gave `P(fits) = 0.81`.

## What a box like this costs per token

At ~21 prompt tokens/s, a 100%-busy box processes about 75,600 tokens an hour, so:

```
$ per 1M tokens ≈ $/hr × 13.2
```

Even at a hypothetical $0.10/hr, that is about $1.32 per million tokens, roughly 15× Workers AI's
$0.09. A 4-vCPU CPU box is far too slow to compete on cost. The EC2 matrix exists to find where the
16-vCPU CPUs and the GPUs land on that curve.
