# Clef-flash on AWS: results

## Speed

Latency at concurrency 1. Throughput is the best step: llama.cpp evaluates one Clef prompt per batch, so extra concurrency queues rather than adding throughput.

| machine | quant | workload | input tok | p50 ms | p90 ms | max req/s | max tok/s | cold start s |
|---|---|---|---|---|---|---|---|---|
| container-4vcpu | Q4_K_M | small | 508 | 23,747 | 24,102 | 0.043 | 21.9 | 482.3 |
| container-4vcpu | Q4_K_M | medium | 1,605 | 75,288 | 79,914 | 0.013 | 21.6 | 482.3 |
| container-4vcpu | Q4_K_M | large | 4,449 | 214,082 | 224,650 | 0.005 | 20.4 | 482.3 |

## Cost

Self-hosted cost assumes the box is 100% busy. Workers AI: $0.09/1M input tokens. *Beats Workers AI* = an always-on box gets cheaper than Workers AI before it runs out of capacity.

| machine | quant | workload | $/hr | $/1M req | $/1M tok | capacity req/mo | break-even req/mo | beats Workers AI |
|---|---|---|---|---|---|---|---|---|

Prices: container unknown (no cost rows)

## Resources

Averages over the concurrency-1 step. CPU % is of the whole machine.

| machine | quant | workload | llama-server CPU % | RSS MB | GPU % | VRAM MB | GPU W |
|---|---|---|---|---|---|---|---|
| container-4vcpu | Q4_K_M | small | 92.5 | 9,830 | - | - | - |
| container-4vcpu | Q4_K_M | medium | 93.1 | 11,233 | - | - | - |
| container-4vcpu | Q4_K_M | large | 91.5 | 13,595 | - | - | - |

## Answer quality

Accuracy on the labeled cases in `bench/data/eval_cases.json`, and drift against a reference run (Workers AI if present, else BF16 on the same machine).

| machine | quant | correct | reference | mean abs dP | max abs dP | same answer |
|---|---|---|---|---|---|---|
| container-4vcpu | Q4_K_M | 37/38 | - | - | - | - |

## Setup and run time

Seconds per bootstrap phase, and what the whole run cost on-demand.

| machine | packages | llama_cpp_build | python_env | model_download | benchmark | total min | run $ |
|---|---|---|---|---|---|---|---|
| container-4vcpu | 13 | 210 | 8 | 0 | 3,309 | 59.0 | - |
