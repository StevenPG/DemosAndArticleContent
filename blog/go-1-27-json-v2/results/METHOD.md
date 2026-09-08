# Benchmark methodology

## What was measured

`encoding/json` (v1) against `encoding/json/v2`, marshal and unmarshal, across
five payload shapes chosen to stress different parts of a JSON codec:

| Shape | Size | What it stresses |
|---|---:|---|
| `Small` | 115 B | Per-call overhead. The most common real payload: an API response of a dozen scalars. |
| `Nested` | 1,262 B | Recursion and the syntax state machine — 32 levels deep. |
| `Batch` | 201,288 B | Throughput per element — 2,000 homogeneous telemetry samples. |
| `Document` | 23,718 B | String escaping and UTF-8 handling: quotes, backslashes, tabs, newlines, non-ASCII. |
| `Dynamic` | 4,768 B | The reflection-heavy worst case — `map[string]any` with mixed nested values. |

The corpus is generated deterministically from a fixed seed
(`rand.NewPCG(0x5eed, 0x1227)`) in `bench/corpus.go`, so every run on every
machine measures identical bytes. Nothing is checked in as a fixture file.

Unmarshal inputs are pre-encoded **with v1**, so both implementations decode
byte-identical input. `TestBothProduceEquivalentOutput` asserts that v1 and v2
marshal the fixtures to identical JSON — without that guard the benchmark
would be timing two different jobs.

## How to reproduce

```bash
make bench        # -count=10, ~6 minutes
make benchstat    # requires golang.org/x/perf/cmd/benchstat
```

Benchmarks use `b.Loop()` (Go 1.24+), which keeps the measured value alive
without the `runtime.KeepAlive` and sink-variable dance older harnesses need.

Sub-benchmark names end in `/impl=v1` or `/impl=v2` so that
`benchstat -col /impl` renders the comparison as a two-column table with
confidence intervals rather than a flat list.

## Hardware and toolchain

```
goos:   linux
goarch: amd64
cpu:    Intel(R) Xeon(R) Processor @ 2.10GHz
cores:  4
memory: 15 GiB
go:     go1.27.1 linux/amd64
```

## Read these numbers carefully

This was run in a **shared cloud container**, not on dedicated hardware. That
has a specific consequence: the *ratios* between v1 and v2 are trustworthy,
because both implementations ran interleaved on the same machine under the same
conditions, ten times each, with benchstat reporting variance. The *absolute*
ns/op figures are not a statement about what your production hardware will do.

If a number matters to a decision you are making, run `make bench` on the
machine that will actually serve the traffic.
