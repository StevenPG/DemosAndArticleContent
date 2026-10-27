# What happens when production doesn't match training (JDK 28-ea+21, x64)

Cache trained on this host (Intel Xeon @ 2.10GHz with AVX-512 incl. FP16) for `warmup-cpu adsb`.
Production runs simulate an older CPU by turning features off.

| Production flags | AOT code used? | Default output | With `-Xlog:aot` | With `-XX:AOTMode=required` |
|---|---|---|---|---|
| (same as training) | yes, 741 methods installed | nothing | `Loaded N AOT code entries` | runs |
| `-XX:UseAVX=2` (an AVX2-only CPU) | **no**, 0 methods | **nothing — silent** | `[info][aot] AOT Code Cache disabled: cpu features are incompatible` + list of required features | **exit 1** |
| `-XX:UseAVX=1` | no | nothing | same, lists avx2 + avx512* | exit 1 |
| `-XX:-UseFMA` | no | nothing | `cpu features that are required: "fma"` | exit 1 |
| `-XX:+UseSerialGC` (trained with G1) | no | nothing | `AOT Code Cache disabled: it was created with UseCondCardMark = true vs current false` | exit 1 |

In every mismatch case the rest of the cache (classes, heap objects, profiles) is still used;
only the compiled code is dropped. The JVM falls back to JIT compilation.

Raw log excerpt (UseAVX=2):

```
[0.028s][info][aot] AOT Code Cache disabled: cpu features are incompatible
[0.028s][info][aot] cpu features that are required: "avx512f, avx512dq, avx512cd, avx512bw, avx512vl, avx512_vpopcntdq, avx512_vpclmulqdq, avx512_vaes, avx512_vnni, avx512_vbmi2, avx512_vbmi, avx512_bitalg, avx512_ifma, avx512_fp16"
```

## Training "down" for portability: the match is exact, not "at least"

Training with `-XX:UseAVX=2` (to target AVX2-only nodes) and running on the AVX-512 host *without*
the flag also drops the AOT code — a superset is rejected too:

```
[0.025s][info][aot] AOT Code Cache disabled: cpu features are incompatible
[0.025s][info][aot] cpu features that are additional: "avx512f, avx512dq, avx512cd, avx512bw, avx512vl, ..."
```

| Training | Production | AOT methods installed |
|---|---|---:|
| `-XX:UseAVX=2` | `-XX:UseAVX=2` | 875 |
| `-XX:UseAVX=2` | default (AVX-512) | 0 |

So a fleet with mixed CPU generations needs either one cache per CPU feature level, or the same
feature-limiting flags on BOTH the training run and every production run (giving up AVX-512 codegen
on the newer nodes).
