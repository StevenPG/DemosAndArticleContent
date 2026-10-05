# Walkthrough: Clef-flash on AWS, from zero to a cost table

This walks through the whole thing once: try Clef on your own machine, launch the AWS matrix, talk
to a remote instance, add the Workers AI baseline, read the results, and tear it all down. Budget
about 30 minutes of your time and 3-5 hours of machine time. The full five-machine run costs a few
dollars on-demand (see [Cost of a run](README.md#cost-of-a-run)).

- [0. Prerequisites](#0-prerequisites)
- [1. Try Clef locally (optional, 10 minutes)](#1-try-clef-locally-optional-10-minutes)
- [2. Configure the run](#2-configure-the-run)
- [3. Launch](#3-launch)
- [4. Watch it work](#4-watch-it-work)
- [5. Make your own calls to a remote Clef](#5-make-your-own-calls-to-a-remote-clef)
- [6. Workers AI baseline](#6-workers-ai-baseline)
- [7. Collect and analyze](#7-collect-and-analyze)
- [8. Tear down](#8-tear-down)
- [Troubleshooting](#troubleshooting)

---

## 0. Prerequisites

| Tool | Why | Check |
|---|---|---|
| AWS account + credentials | everything | `aws sts get-caller-identity` |
| Terraform >= 1.6 (or OpenTofu) | infrastructure | `terraform version` |
| AWS CLI v2 | results, SSM | `aws --version` |
| [Session Manager plugin](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html) | shell + port forwarding, no SSH | `session-manager-plugin --version` |
| Python 3.11+ | client, benchmark, analysis | `python3 --version` |
| Cloudflare account (optional) | Workers AI baseline | - |

The IAM identity running Terraform needs to create a VPC, EC2 instances, an S3 bucket and an IAM
role + instance profile. `AdministratorAccess` in a sandbox account is the easy path.

### Check your EC2 quotas first

New accounts often have **zero** quota for GPU instances, and the request can take a day. Quotas
are counted in vCPUs per instance family group:

```bash
# Standard (C, M, R, T...) on-demand: the three CPU boxes need 48 vCPUs
aws service-quotas get-service-quota --service-code ec2 --quota-code L-1216C47A \
  --query 'Quota.Value' --region us-east-1

# G and VT on-demand: g6.xlarge + g6e.xlarge need 8 vCPUs
aws service-quotas get-service-quota --service-code ec2 --quota-code L-DB2E81BA \
  --query 'Quota.Value' --region us-east-1
```

If the G quota is below 8, request an increase:

```bash
aws service-quotas request-service-quota-increase --service-code ec2 \
  --quota-code L-DB2E81BA --desired-value 8 --region us-east-1
```

Using Spot (`use_spot = true`) draws from separate quotas: `L-34B43A08` (standard) and
`L-3819A6DF` (G and VT).

### Python environment

```bash
cd blog/clef-aws-benchmark
python3 -m venv .venv && source .venv/bin/activate
pip install -r bench/requirements.txt
```

---

## 1. Try Clef locally (optional, 10 minutes)

Seeing a decision model answer on your own machine first makes everything after it easier to
follow. You need llama.cpp **b11390 or newer**: Clef support was merged on 2026-10-03
([ggml-org/llama.cpp#29831](https://github.com/ggml-org/llama.cpp/pull/29831)).

```bash
# macOS
brew install llama.cpp          # or brew upgrade llama.cpp; check: llama-server --version

# Linux: build it (CPU), about 10 minutes on 4 cores
git clone --depth 1 --branch b11401 https://github.com/ggml-org/llama.cpp
cmake -S llama.cpp -B llama.cpp/build -DCMAKE_BUILD_TYPE=Release
cmake --build llama.cpp/build --target llama-server -j
export PATH="$PWD/llama.cpp/build/bin:$PATH"
```

Start a server. `-hf` downloads the 6.5 GB Q4_K_M file into the llama.cpp cache:

```bash
llama-server -hf ggml-org/Clef-Flash-GGUF:Q4_K_M -c 8192 -b 8192 -ub 8192
```

Wait for `decision model type: clef` and `listening on http://127.0.0.1:8080`. Use the
**ggml-org** GGUF. Other uploads named `clef-flash-GGUF` contain only the Qwen3.5 backbone,
without the schema head, and cannot answer decision requests (see the README's gotchas).

Ask it something:

```bash
curl -s http://127.0.0.1:8080/v1/systemone -H 'Content-Type: application/json' -d '{
  "state": "Customer: I was charged twice for my order last week and nobody has replied.",
  "questions": {
    "route":   {"type": "choice", "instructions": "Which team should handle this?",
                "criteria": {"billing": null, "shipping": null, "technical": null}},
    "angry":   {"type": "noul",   "instructions": "Is the customer angry?"},
    "urgency": {"type": "score",  "instructions": "How urgent is this?",
                "criteria": ["can wait", "this week", "today", "right now"]}
  }
}' | python3 -m json.tool
```

Or use the bundled client, which prints every probability as a bar:

```bash
python3 bench/quickstart.py
python3 bench/quickstart.py --state "Engine 2 oil pressure low light on climb-out, crew returned to field."
```

What came back:

- **`choice`**: the most likely option, a probability for *every* option (they sum to 1), and a
  `confidence` (0 = all options equally likely).
- **`score`**: the probability of each ordered level, and `score`, the probability-weighted
  expected level. A `score` of 2.9 out of 3 means "almost certainly the top level". A `score` of
  1.5 with a flat distribution means the model can't tell.
- **`noul`**: one number, P(true).

There is no generated text and no parsing. One forward pass over the prompt produces every answer,
so latency depends on **input** length, and there are no output tokens to pay for. The rest of
this project builds on that.

Stop the server (Ctrl-C) before moving on.

---

## 2. Configure the run

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars
```

The defaults are the full matrix:

| Name | Type | What it is | vCPU / RAM | Accelerator |
|---|---|---|---|---|
| `c7i-4xlarge` | c7i.4xlarge | Intel Sapphire Rapids | 16 / 32 GB | AVX-512, AMX |
| `c7a-4xlarge` | c7a.4xlarge | AMD EPYC Genoa | 16 / 32 GB | AVX-512 |
| `c8g-4xlarge` | c8g.4xlarge | AWS Graviton4 (arm64) | 16 / 32 GB | SVE2 |
| `g6-xlarge` | g6.xlarge | NVIDIA L4 | 4 / 16 GB | 24 GB VRAM |
| `g6e-xlarge` | g6e.xlarge | NVIDIA L40S | 4 / 32 GB | 48 GB VRAM |

Each machine benchmarks `Q4_K_M` (6.5 GB), `Q8_0` (9.7 GB) and `BF16` (18.2 GB). Everything fits
on every machine.

For a first run, cut it down to one CPU box, one GPU box and one quant. That finishes in under an
hour for well under a dollar:

```hcl
only       = ["c8g-4xlarge", "g6-xlarge"]
quants     = ["Q4_K_M"]
bench_args = "--concurrency 1,4 --duration 30 --min-requests 4 --max-step-seconds 300"
```

Other knobs:

- `auto_run = false`: install everything but don't benchmark. Drive it yourself over SSM.
- `shutdown_minutes` (default 300): each box powers itself off this long after boot, whatever
  happens. A stopped instance only bills for its disk.
- `instances`: any EC2 type works. Architecture and GPU are detected, and the AZ is picked from
  where the type is actually offered.

---

## 3. Launch

```bash
terraform init
terraform apply
```

Terraform creates:

- A VPC with a public subnet per AZ and an egress-only security group. Nothing listens on the
  internet.
- An S3 bucket. Your local `bench/` and `scripts/` are uploaded into it, so local edits run
  without pushing to git.
- An IAM role allowing SSM Session Manager plus read/write on that bucket.
- One instance per machine:
  - CPU boxes use Ubuntu 24.04.
  - GPU boxes use the Deep Learning Base AMI, which includes the NVIDIA driver and CUDA.

Each instance's user data writes `/etc/clef-bench.env`, pulls the code from S3 and starts
`scripts/remote/bootstrap.sh`. Bootstrap then runs these steps:

1. Arms the safety-net shutdown.
2. Installs packages and builds llama.cpp `b11401`. The build is native for the CPU, or CUDA for
   exactly the installed GPU.
3. Creates a Python venv and downloads the GGUF files.
4. Runs `scripts/remote/run-benchmark.sh`. For each quant it starts `llama-server`, times the
   launch until `/health` returns 200, runs `bench/clef_bench.py`, then stops the server.
5. Uploads results to `s3://<bucket>/results/<machine>/`.
6. Leaves `clef-server.service` running the `serve_quant` model on `127.0.0.1:8080`.

---

## 4. Watch it work

```bash
../scripts/status.sh        # RUNNING / DONE / FAILED per machine
../scripts/status.sh -v     # plus the last lines of each bootstrap log
```

Or open a shell on one and follow along:

```bash
terraform output ssm_shell                  # copy the command for a machine
aws ssm start-session --region us-east-1 --target i-0123456789abcdef0
sudo tail -f /var/log/clef-bench.log
```

The progress lines look like this. They are from the 4-vCPU test container, so expect your EC2
numbers to be much faster:

```
[c8g-4xlarge/Q4_K_M] small c=1 ...
    6 ok / 0 err  p50=...ms p90=...ms  ... req/s  ... tok/s  cpu=...%  gpu=None%
```

Rough timings per machine for the default three quants:

- **GPU boxes**: most of the time goes to the CUDA build (4 vCPUs) and the 34 GB of downloads.
  The benchmark itself takes minutes.
- **CPU boxes**: the benchmark dominates. A 4.5k-token prompt is a large amount of matrix math
  for 16 vCPUs, and BF16 is the slowest of the three quants.

---

## 5. Make your own calls to a remote Clef

When a machine reports `DONE`, its `clef-server` is up on the instance's loopback. Forward it to
your laptop through SSM. There is no open port and no SSH key:

```bash
../scripts/port-forward.sh g6-xlarge          # localhost:8080 -> instance 127.0.0.1:8080
```

In another terminal:

```bash
python3 bench/quickstart.py --state "Two-factor codes are not arriving by SMS, I'm locked out."
python3 bench/quickstart.py --raw             # the full response body
```

Every curl from step 1 works too. Ideas to try:

- Ask the same question of a CPU box and a GPU box and compare the round trip.
- Feed it something from your own domain. A JSON `state` works as well as plain text, and
  `instructions` can be a whole paragraph.
- Add a `choice` with 20 options. Clef scores all of them jointly in the same single pass, so the
  added cost is only the extra prompt tokens.
- Watch resources while you call it, from an SSM shell:
  - GPU: `nvidia-smi dmon -s um`
  - CPU: `top -p $(pgrep llama-server)`

To serve a different quant: `sudo systemctl edit clef-server`, or run
`sudo /opt/clef-bench/scripts/remote/serve.sh Q8_0` by hand after
`sudo systemctl stop clef-server`.

---

## 6. Workers AI baseline

Cloudflare hosts the same model as `@cf/cloudflare/clef-flash` at $0.09 per million input tokens.
To include it in the comparison:

1. In the Cloudflare dashboard, copy your Account ID.
2. Create an API token with **Workers AI: Read**.

```bash
export CLOUDFLARE_ACCOUNT_ID=...
export CLOUDFLARE_API_TOKEN=...
python3 bench/quickstart.py --target workers-ai     # sanity check
scripts/bench-workers-ai.sh                          # from the project root
```

That runs the same three workloads and the same eval set, and writes
`results/workers-ai/workers-ai.json`.

Latency from your laptop includes your internet round trip to Cloudflare, while the EC2 numbers
are measured on the box. For a fairer number, run the same script from an EC2 instance in the same
region. It is already there, and it uploads to the run's bucket:

```bash
aws ssm start-session --region us-east-1 --target <id>
sudo -i
export CLOUDFLARE_ACCOUNT_ID=... CLOUDFLARE_API_TOKEN=...
/opt/clef-bench/scripts/bench-workers-ai.sh workers-ai-from-us-east-1
```

---

## 7. Collect and analyze

```bash
scripts/fetch-results.sh          # s3://<bucket>/results/ -> ./results/
python3 bench/analyze.py          # -> results/summary.md and results/summary.csv
```

`analyze.py` takes live on-demand prices from the AWS Pricing API, falling back to a built-in
table. It writes five tables:

1. **Speed**: latency at concurrency 1 (p50/p90), best sustained requests/s and input tokens/s,
   and cold start (launch until `/health` returns 200).
2. **Cost**:
   - $ per million requests and $ per million input tokens for a fully busy box.
   - The monthly volume at which an always-on box breaks even with Workers AI.
   - Whether one box can even serve that volume.
3. **Resources**: llama-server CPU %, RSS, GPU utilization, VRAM and power draw.
4. **Answer quality**: accuracy on the 24 labeled cases, and how far each quant's probabilities
   drift from a reference. The reference is Workers AI if you ran it, else BF16 on the same box.
5. **Setup and run time**: build, download and benchmark minutes per machine, and the dollar cost
   of the run.

How to read it:

- **Latency vs throughput.** llama.cpp evaluates one Clef prompt per batch, so with `-np 1` a box
  serves exactly 1/latency requests per second. Concurrency 4 should show about 4x the latency at
  the same throughput. If you need more throughput, add boxes or pick a faster one. Extra slots do
  not help.
- **$/1M tokens is the headline.** Clef has no output tokens, so input tokens are the entire bill
  on Workers AI. That makes it directly comparable with a self-hosted box's
  `$/hr ÷ tokens per hour`.
- **"never" in *beats Workers AI*** means a single always-on box of that type costs more than
  Workers AI even at 100% utilization. Self-hosting can still win on data residency, a private
  network, or burst latency, but not on price.
- **Quant drift.** Q4_K_M usually tracks BF16 to within a few hundredths of probability on
  confident answers. Look at the *max* drift, and at whether any argmax answer flipped
  (*same answer* column).

---

## 8. Tear down

Fetch results first: the bucket is created with `force_destroy`.

```bash
scripts/fetch-results.sh
cd terraform && terraform destroy
```

Verify nothing is left running:

```bash
aws ec2 describe-instances --region us-east-1 \
  --filters Name=tag:Project,Values=clef-bench Name=instance-state-name,Values=pending,running,stopped \
  --query 'Reservations[].Instances[].[InstanceId,InstanceType,State.Name]' --output table
```

---

## Troubleshooting

**`VcpuLimitExceeded` / `You have requested more vCPU capacity than your current vCPU limit`**
is a quota problem; see [Check your EC2 quotas](#check-your-ec2-quotas-first). Use
`only = [...]` to launch what fits now.

**`InsufficientInstanceCapacity` on g6e/g6** means the chosen AZ is out of that type right now.
Re-run `terraform apply` later, or try another region (`region = "us-west-2"`).

**A machine shows `FAILED`.** Run `scripts/status.sh -v`, or read
`s3://<bucket>/results/<machine>/bootstrap.log`. Bootstrap is idempotent, so fix the cause and
re-run it over SSM: `sudo /opt/clef-bench/scripts/remote/bootstrap.sh`.

**`server did not become healthy`**: see `results/<machine>/<quant>.server.log`. The usual
causes:

- Out of memory: BF16 plus an 8192-token ubatch on a small box. Lower `CLEF_CTX`, or drop BF16
  from `quants`.
- An old llama.cpp tag.

**`400 ... must fit in --ubatch-size`** (or similar). Clef evaluates the whole prompt in one
micro-batch. Raise `CLEF_CTX` (it sets `-c`, `-b` and `-ub` together) for long states.

**`501` from `/v1/systemone`** means the loaded GGUF is not a decision model. You are using a
backbone-only upload; switch to `ggml-org/Clef-Flash-GGUF`. Image requests also return 501, because
llama.cpp does not support Clef's vision input yet.

**SSM `TargetNotConnected`.** The agent registers 1-2 minutes after boot. Also check that the
Session Manager plugin is installed locally.

**The box powered off mid-run.** That was the safety-net shutdown. Raise `shutdown_minutes`, then
`aws ec2 start-instances` and re-run bootstrap.
