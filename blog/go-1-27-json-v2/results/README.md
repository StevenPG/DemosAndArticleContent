# Results

Go 1.27.1, `-count=10`, compared with `benchstat -col /impl`. See
[METHOD.md](METHOD.md) for the corpus, the hardware, and how to reproduce.
Raw output is in [raw.txt](raw.txt), the full benchstat table in
[benchstat.txt](benchstat.txt).

Negative percentages mean **v2 is faster**. `~` means benchstat found no
statistically significant difference.

## Time per operation

| Benchmark | v1 | v2 | Change |
|---|---:|---:|---:|
| Marshal/Small | 649.0 ns ± 3% | 679.2 ns ± 6% | ~ (p=0.353) |
| Marshal/Nested | 6.676 µs ± 5% | 7.024 µs ± 2% | **+5.21%** (p=0.004) |
| Marshal/Batch | 917.9 µs ± 4% | 921.6 µs ± 3% | ~ (p=0.393) |
| Marshal/Document | 48.83 µs ± 1% | 48.94 µs ± 3% | ~ (p=0.393) |
| Marshal/Dynamic | 86.61 µs ± 2% | 60.01 µs ± 5% | **−30.71%** (p=0.000) |
| Unmarshal/Small | 1061.5 ns ± 2% | 855.7 ns ± 3% | **−19.39%** (p=0.000) |
| Unmarshal/Nested | 14.47 µs ± 4% | 13.19 µs ± 9% | **−8.80%** (p=0.000) |
| Unmarshal/Batch | 2.071 ms ± 5% | 1.743 ms ± 8% | **−15.86%** (p=0.000) |
| Unmarshal/Document | 119.50 µs ± 2% | 86.97 µs ± 5% | **−27.22%** (p=0.000) |
| Unmarshal/Dynamic | 147.77 µs ± 4% | 95.46 µs ± 4% | **−35.40%** (p=0.000) |
| **geomean** | **39.58 µs** | **34.07 µs** | **−13.92%** |

## Allocations

Only the `map[string]any` shape moved; every other shape allocates identically.

| Benchmark | v1 | v2 | Change |
|---|---:|---:|---:|
| Marshal/Dynamic | 404 allocs, 10.425 KiB | 204 allocs, 7.119 KiB | **−49.50% allocs, −31.71% bytes** |
| Unmarshal/Dynamic | 1273 allocs, 46.79 KiB | 793 allocs, 41.76 KiB | **−37.71% allocs, −10.75% bytes** |
| everything else | — | — | ~ (identical) |

## What these numbers say

**Unmarshal is faster on every shape measured**, from −8.8% on deeply nested
structs to −35.4% on `map[string]any`. This matches the release notes' claim
that unmarshal is "significantly faster", and the effect is consistent rather
than concentrated in one case.

**Marshal is at parity on three shapes out of five**, which also matches the
release notes. The two exceptions are worth knowing about and are not mentioned
upstream:

- **`map[string]any` marshal is 30.7% faster** and allocates half as many
  times. If your service does passthrough or logging of dynamic JSON, this is
  the largest single improvement in the whole table.
- **Deeply nested marshal is 5.2% *slower*** (p=0.004, so it is a real effect
  rather than noise, though small). Thirty-two levels of nesting is not a
  typical payload; if yours looks like that and marshal is on your hot path,
  measure before assuming parity.

**The reflection-heavy path is where v2 wins most.** Both `Dynamic` rows are
the largest improvements in their columns, on time and on allocations. The
structured shapes with static Go types improve on decode and stay flat on
encode.

**Caveat that matters:** these ran in a shared cloud container. The v1-to-v2
*ratios* are trustworthy — both implementations ran interleaved on the same
machine, ten times each, with variance reported. The absolute ns/op figures are
not a prediction for your hardware. Run `make bench` on the box that serves
your traffic before making a decision on the numbers.
