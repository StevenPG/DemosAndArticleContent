# Probe output

Temurin builds from `scripts/fetch-jdks.sh`: 25.0.4.1+1-LTS, 26.0.2.1+1, 27+35. Object layout numbers are
deterministic for a given JDK and flag set - they do not depend on the host.

## DefaultsProbe - JDK 25, no container limits (4-core host)

```
java.version            25.0.4.1
availableProcessors     4
maxHeap                 3422 MiB
collector               G1
collector MXBeans       G1 Young Generation, G1 Concurrent GC, G1 Old Generation
UseCompactObjectHeaders false
UseCompressedOops       true (ERGONOMIC)
TLS named groups        x25519, secp256r1, secp384r1, secp521r1, x448, ffdhe2048, ffdhe3072, ffdhe4096, ffdhe6144, ffdhe8192
```

## DefaultsProbe - JDK 26, no container limits (4-core host)

```
java.version            26.0.2.1
availableProcessors     4
maxHeap                 3422 MiB
collector               G1
collector MXBeans       G1 Young Generation, G1 Concurrent GC, G1 Old Generation
UseCompactObjectHeaders false
UseCompressedOops       true (ERGONOMIC)
TLS named groups        x25519, secp256r1, secp384r1, secp521r1, x448, ffdhe2048, ffdhe3072, ffdhe4096, ffdhe6144, ffdhe8192
```

## DefaultsProbe - JDK 27, no container limits (4-core host)

```
java.version            27
availableProcessors     4
maxHeap                 3422 MiB
collector               G1
collector MXBeans       G1 Young Generation, G1 Concurrent GC, G1 Old Generation
UseCompactObjectHeaders true
UseCompressedOops       true (ERGONOMIC)
TLS named groups        X25519MLKEM768, x25519, secp256r1, secp384r1, secp521r1, x448, ffdhe2048, ffdhe3072, ffdhe4096
```

## DefaultsProbe - JDK 25, `--cpus 1 --memory 1g`

```
java.version            25.0.4.1
availableProcessors     1
maxHeap                 247 MiB
collector               Serial
collector MXBeans       Copy, MarkSweepCompact
UseCompactObjectHeaders false
UseCompressedOops       true (ERGONOMIC)
TLS named groups        x25519, secp256r1, secp384r1, secp521r1, x448, ffdhe2048, ffdhe3072, ffdhe4096, ffdhe6144, ffdhe8192
```

## DefaultsProbe - JDK 26, `--cpus 1 --memory 1g`

```
java.version            26.0.2.1
availableProcessors     1
maxHeap                 255 MiB
collector               Serial
collector MXBeans       Copy, MarkSweepCompact
UseCompactObjectHeaders false
UseCompressedOops       true (ERGONOMIC)
TLS named groups        x25519, secp256r1, secp384r1, secp521r1, x448, ffdhe2048, ffdhe3072, ffdhe4096, ffdhe6144, ffdhe8192
```

## DefaultsProbe - JDK 27, `--cpus 1 --memory 1g`

```
java.version            27
availableProcessors     1
maxHeap                 256 MiB
collector               G1
collector MXBeans       G1 Young Generation, G1 Concurrent GC, G1 Old Generation
UseCompactObjectHeaders true
UseCompressedOops       true (ERGONOMIC)
TLS named groups        X25519MLKEM768, x25519, secp256r1, secp384r1, secp521r1, x448, ffdhe2048, ffdhe3072, ffdhe4096
```

## HeaderFootprint - JDK 27, `-XX:-UseCompactObjectHeaders` (left) vs default (right)

```
java.version                               27|java.version                               27
UseCompactObjectHeaders                    false|UseCompactObjectHeaders                    true
instances per shape                        2,000,000|instances per shape                        2,000,000
|
shape                                       bytes/obj|shape                                       bytes/obj
new Object()                                     16.0|new Object()                                      8.0
Integer (outside cache)                          16.0|Integer (outside cache)                          16.0
Long                                             24.0|Long                                             16.0
record Point(int, int)                           24.0|record Point(int, int)                           16.0
Node { ref, int }                                24.0|Node { ref, int }                                16.0
record Sample(long, int, 3x float, short)        40.0|record Sample(long, int, 3x float, short)        40.0
byte[16]                                         32.0|byte[16]                                         32.0
String, 8 latin1 chars                           48.0|String, 8 latin1 chars                           48.0
ArrayList, 4 Integers                           120.0|ArrayList, 4 Integers                           120.0
HashMap entry (Long -> Point)                    89.4|HashMap entry (Long -> Point)                    65.4
```

## HeaderFootprint - JDK 25 default (matches the legacy column above)

```
java.version                               25.0.4.1
UseCompactObjectHeaders                    false
instances per shape                        2,000,000

shape                                       bytes/obj
new Object()                                     16.1
Integer (outside cache)                          16.1
Long                                             24.3
record Point(int, int)                           24.1
Node { ref, int }                                24.1
record Sample(long, int, 3x float, short)        40.0
byte[16]                                         32.0
String, 8 latin1 chars                           48.0
ArrayList, 4 Integers                           120.2
HashMap entry (Long -> Point)                    89.6
```
