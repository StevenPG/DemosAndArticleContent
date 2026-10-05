# Clef-flash on AWS: Speed, Resources and Cost, CPU vs GPU

[Clef and Clef-flash](https://blog.cloudflare.com/clef-decision-models) (Cloudflare, 2026-10-01) are
open-weight **decision models**. They are the "System One" kind: you give them a `state` and typed
questions, and they return a **probability for every allowed answer**. They don't generate text.
There is no decode loop and no output parsing. One forward pass over the prompt answers every
question at once.

That shape makes them unusually easy to cost out: the bill is input tokens and nothing else. This
project runs **Clef-flash (9B)** with llama.cpp on five EC2 machines (three CPU, two GPU) at three
quantizations, and measures:

- **Speed**: latency at three prompt sizes, sustained throughput, and cold start.
- **Resources**: CPU, RSS, GPU utilization, VRAM and power while serving.
- **Quality**: accuracy on labeled cases, and probability drift per quantization.
- **Cost**: $ per million requests and per million tokens. These are compared with Cloudflare's
  hosted Clef-flash on Workers AI ($0.09/1M input tokens), including the volume at which an
  always-on box breaks even.

Start here: **[WALKTHROUGH.md](WALKTHROUGH.md)**, step by step from a laptop to the final cost table.

```
 laptop                                 AWS (one VPC, no inbound ports)
┌───────────────────┐  terraform apply  ┌──────────────────────────────────────────────────────┐
│ terraform/        │ ───────────────▶  │ S3 bucket: project/ (your bench/ + scripts/)          │
│ bench/  scripts/  │                   │            results/<machine>/*.json  ◀──────┐         │
└───────────────────┘                   │                                             │ upload  │
        ▲    │ SSM port-forward :8080   │ c7i.4xl  c7a.4xl  c8g.4xl  g6.xl  g6e.xl ───┘         │
        │    └────────────────────────▶ │  bootstrap: build llama.cpp b11401 → download GGUFs → │
        │                               │  per quant: llama-server + clef_bench.py → leave a    │
        │  fetch-results.sh             │  server running on 127.0.0.1:8080                     │
        └────────────────────────────── └──────────────────────────────────────────────────────┘
 analyze.py → results/summary.md            Workers AI (@cf/cloudflare/clef-flash) ◀── same workloads
```

## The machines

| Name | Type | CPU / accelerator | vCPU | Memory | On-demand $/hr* |
|---|---|---|---|---|---|
| `c7i-4xlarge` | c7i.4xlarge | Intel Sapphire Rapids, AVX-512 + AMX | 16 | 32 GB | ~0.71 |
| `c7a-4xlarge` | c7a.4xlarge | AMD EPYC Genoa, AVX-512 | 16 | 32 GB | ~0.82 |
| `c8g-4xlarge` | c8g.4xlarge | AWS Graviton4 (arm64), SVE2 | 16 | 32 GB | ~0.64 |
| `g6-xlarge` | g6.xlarge | NVIDIA L4, 24 GB | 4 | 16 GB | ~0.80 |
| `g6e-xlarge` | g6e.xlarge | NVIDIA L40S, 48 GB | 4 | 32 GB | ~1.86 |

\* us-east-1 Linux. `analyze.py` pulls current prices from the AWS Pricing API at analysis time.

The three CPU boxes cost about the same per hour, so they compare three CPU vendors on equal
footing. The GPUs are the two cheapest current NVIDIA options on EC2. Every quantization fits
everywhere: Q4_K_M 6.5 GB, Q8_0 9.7 GB, BF16 18.2 GB.

## Quick start

```bash
cd blog/clef-aws-benchmark
python3 -m venv .venv && source .venv/bin/activate && pip install -r bench/requirements.txt

cd terraform && cp terraform.tfvars.example terraform.tfvars   # optional: trim the matrix
terraform init && terraform apply
../scripts/status.sh                                           # wait for DONE everywhere

../scripts/port-forward.sh g6-xlarge &                         # talk to a remote Clef
python3 ../bench/quickstart.py

cd .. && scripts/bench-workers-ai.sh                           # optional, needs Cloudflare creds
scripts/fetch-results.sh && python3 bench/analyze.py           # → results/summary.md
cd terraform && terraform destroy
```

Check your **GPU quota** before the first apply: new accounts often have 0 vCPUs of G-family
quota. See [WALKTHROUGH §0](WALKTHROUGH.md#check-your-ec2-quotas-first).

## Layout

```
terraform/                 VPC, egress-only SG, S3 bucket, SSM-only IAM role, one instance per machine
  instances.tf             arch + GPU detected per type, AZ picked from where the type is offered
  user_data.sh.tftpl       writes /etc/clef-bench.env, pulls code from S3, starts bootstrap
scripts/remote/            runs on the instances
  bootstrap.sh             timed phases: packages → llama.cpp build → venv → models → benchmark → serve
  install-llama-cpp.sh     pinned tag, native CPU build or CUDA for the installed GPU only
  download-model.sh        ggml-org/Clef-Flash-GGUF (the only GGUF with the schema head)
  serve.sh                 llama-server with Clef-appropriate flags
  run-benchmark.sh         per quant: cold start → clef_bench.py → stop
scripts/                   runs on your laptop
  status.sh  port-forward.sh  fetch-results.sh  bench-workers-ai.sh
bench/
  clef_client.py           /v1/systemone client for llama.cpp and Workers AI
  quickstart.py            one request, every probability printed
  workloads.py             deterministic small / medium / large workloads
  clef_bench.py            closed-loop load steps + psutil / nvidia-smi sampling + labeled eval
  analyze.py               speed, cost, resources, quality and setup tables
  data/eval_cases.json     24 labeled cases (support, invoices, aircraft maintenance, NOTAMs, phishing, ...)
results/                   fetched results land here
```

## What gets measured

**Workloads.** They differ mainly in input size, because input size is what drives a prefill-only
model. Each one is generated from a fixed seed, so every machine evaluates identical prompts. Each
request also has a different state, so no prompt prefix is served from cache.

| Workload | Prompt tokens | State | Questions |
|---|---|---|---|
| `small` | ~450 | support ticket | choice (4), score (4 levels), 2× noul |
| `medium` | ~1,600 | invoice + PO + vendor history as JSON | choice (4), score (5), 2× noul |
| `large` | ~4,400 | 80 lines of auth / nginx logs | choice (5), score (5), 2× noul |

**Load steps.** For each workload and concurrency level (default 1 and 4), N workers each send
their next request as soon as the previous one returns. A step runs at least `--duration` seconds
*and* at least `--min-requests` requests, capped at `--max-step-seconds`. Each step records:

- p50/p90/p99 latency
- requests/s and input tokens/s
- llama-server CPU % and RSS (psutil)
- GPU utilization, VRAM and power (`nvidia-smi`, sampled every 500 ms)

**Cold start** is the time from launching `llama-server` until `/health` returns 200, with the page
cache dropped first, so the model is read from EBS (gp3 at 500 MB/s) and uploaded to the GPU.

**Quality.** The 24 labeled cases are answered once per machine × quant, and every probability is
stored. `analyze.py` reports accuracy, plus mean and max probability drift against Workers AI (or
against BF16 on the same box).

**Cost model.** A self-hosted box bills by the hour whether requests arrive or not:

```
$ per 1M requests  = $/hr ÷ (req/s × 3600) × 10⁶          (box 100% busy, the best case)
$ per 1M tokens    = $/hr ÷ (input tok/s × 3600) × 10⁶
break-even volume  = $/hr × 730 ÷ (Workers AI $ per request)   requests/month
capacity           = req/s × 3600 × 730                        requests/month
```

If the break-even volume is above the capacity, that box can never undercut Workers AI on that
workload, at any volume.

## Gotchas found while building this

- **Only one GGUF actually is Clef.** Reading the GGUF headers:
  - `ggml-org/Clef-Flash-GGUF` has `general.architecture = clef` and 587 tensors, including the
    joint schema head (`dec.blk.*`).
  - The other popular uploads, e.g. `bartowski/Cloudflare_clef-flash-GGUF` and
    `prithivMLmods/clef-flash-GGUF`, are `qwen35` with 427 tensors. They are just the Qwen3.5-9B
    backbone with Clef's fine-tuned weights. They load fine and chat, but `/v1/systemone` returns
    501 on them.
- **llama.cpp b11390 or newer.** Clef support was merged on 2026-10-03
  ([ggml-org/llama.cpp#29831](https://github.com/ggml-org/llama.cpp/pull/29831)). It covers text
  only; image input returns 501. Once the server loads a decision model, it serves `/v1/systemone`
  and nothing else, and it turns on embedding mode by itself.
- **The whole prompt must fit in one micro-batch.** Clef's joint head reads every question and
  option span in one pass, so `--ubatch-size` has to cover the full prompt. `serve.sh` sets `-c`,
  `-b` and `-ub` together through `CLEF_CTX` (default 8192).
- **Concurrency doesn't add throughput.** The server never batches two Clef prompts together. On
  the 4-vCPU test container, concurrency 2 doubled p50 latency (24.8 s → 47.7 s) at the same
  ~0.04 req/s. The benchmark keeps the concurrency-4 step to show the queueing. Scaling out means
  more boxes, not more slots.
- **Memory is the model plus a buffer that grows with the prompt.** On CPU the Q4_K_M GGUF is
  6.5 GB on disk. The server reached 9.8 GB RSS at a ~500-token prompt, 11.2 GB at ~1.7k and
  13.6 GB at ~4.4k. The CPU backend repacks weights for its SIMD kernels while the mmapped
  originals stay resident, and Clef evaluates the whole prompt in one micro-batch, so activation
  memory scales with prompt length. Plan headroom for BF16 on the 24 GB L4: 18.2 GB of weights
  leaves little room for a long prompt.
- **A shallow clone reports `build 1`.** `llama-server --version` on a `--depth 1` checkout says
  `0.5.0-dev (build 1, commit …)`, not the tag. The installer records the tag in
  `/opt/llama.cpp/TAG` instead.
- **Workers AI's response envelope is documented both ways.** The model page says the body is
  unwrapped, but early write-ups show `{"result": …}`. `clef_client.py` accepts both.
- **The probabilities are temperature-scaled, not calibrated for your data.** In llama.cpp's
  words: "scaled with the temperatures stored in the model file". The quantization drift table
  measures agreement, not calibration.

## Measured so far

The pipeline was verified end to end on a 4-vCPU x86_64 cloud container (Intel Xeon with AVX-512,
15 GB RAM, no GPU) with llama.cpp `b11401` and Clef-Flash Q4_K_M. These numbers show what to expect
from a small CPU. **They are not EC2 results**; those come from running this project.

Concurrency 1, measured with the instance scripts themselves (`install-llama-cpp.sh`, then
`run-benchmark.sh`):

| Workload | Prompt tokens | p50 latency | Throughput | llama-server CPU | Peak RSS |
|---|---|---|---|---|---|
| small | 509 | 25.3 s | 0.039 req/s, 20.1 tok/s | 88% | 9.8 GB |
| medium | 1,690 | 83.6 s | 0.012 req/s, 20.2 tok/s | 89% | 11.2 GB |
| large | 4,439 | 233.9 s | 0.004 req/s, 19.0 tok/s | 89% | 13.6 GB |

- **Concurrency 2** on the small workload doubled p50 latency (24.8 s → 47.7 s) at the same
  ~0.04 req/s.
- **Build:** the static CPU build of `llama-server` took 212 s on 4 cores.
- **Cold start:** 13 s from launch to `/health` 200. The container may not have honored the
  page-cache drop, so treat this as a warm number.
- **Labeled eval:** 36/38 correct with Q4_K_M. One miss was a real arithmetic error: 48 × 20 = 960
  < 1,000, yet the model gave P(fits) = 0.81. The other was an ambiguous label, since fixed.

About 20 prompt tokens/s on 4 vCPUs means a 4.4k-token prompt takes minutes on a small CPU. That
is why the benchmark steps have a minimum request count and a hard time cap, not a fixed request
count.

Sample output from `bench/quickstart.py`:

```
department  choice  -> technical  (confidence 0.930)
    technical              0.9474  ############################..
    billing                0.0389  #.............................
    shipping               0.0083  ..............................
    flight_ops             0.0055  ..............................
urgency     score   -> 2.908159172055389  (confidence 0.908)
    3 Right now            0.9392  ############################..
    2 Today                0.0411  #.............................
    0 Can wait             0.0113  ..............................
    1 This week            0.0084  ..............................
outage      noul    P(true) = 0.9168  ############################..
safety      noul    P(true) = 0.0041  ..............................
```

## Cost of a run

Approximate on-demand cost of the default matrix: five machines, three quants each, defaults for
everything else.

- **GPU boxes:** about 45-75 minutes each, mostly the CUDA build on 4 vCPUs and 34 GB of
  downloads. Roughly $1.00 for g6.xlarge and $2.25 for g6e.xlarge.
- **CPU boxes:** about 1.5-3 hours each, mostly the benchmark itself (large prompts on CPU are
  slow). Roughly $1.00-2.50 each.
- **Workers AI baseline:** about $0.10-0.20.
- **EBS** (150 GB gp3 × 5) and S3 add cents.

Expect **roughly $6-10** for everything. The trimmed first run in the walkthrough (one CPU box, one
GPU box, Q4_K_M only) is well under $1. The `shutdown_minutes` safety net (default 5 h) powers
every box off even if you forget to destroy.

## Requirements

- AWS account; Terraform >= 1.6 (AWS provider 6.x); AWS CLI v2 with the Session Manager plugin
- Python 3.11+ (`httpx`, `psutil`)
- Optional: Cloudflare account + API token with Workers AI: Read
