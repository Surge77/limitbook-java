# Engine-core benchmark results

Microbenchmarks via [JMH](https://github.com/openjdk/jmh). Source:
`src/test/java/com/limitbook/engine/bench/MatchingBenchmark.java`.

## Environment

- CPU: developer workstation, single matching thread (no parallelism in the core)
- JDK: Temurin 21.0.11 (LTS), 64-bit Server VM
- OS: Windows 11
- JMH: 1.37 — 1 fork, 3×1s warmup, 5×1s measurement

## Headline numbers

| Benchmark | Metric | Result |
|-----------|--------|--------|
| `throughput` | orders/sec (single thread, mixed limit flow) | **≈ 2.44 M ops/s** (± 0.49 M) |
| `matchLatency` | mean per order | **0.15 µs** |
| `matchLatency` | p50 / p90 / p99 | **0.10 / 0.20 / 0.40 µs** |
| `matchLatency` | p99.9 / p99.99 | **6.2 / 67 µs** |

Targets from the spec (≥ 1 M orders/sec single-thread, p99 < 100 µs warm JVM) are
both comfortably met: ~2.4× the throughput target and p99 ~250× under the latency
ceiling. The p99.99 tail (~67 µs) reflects JVM GC / safepoint jitter — the
expected cost of a managed runtime; the Rust variant of this project targets a
tighter tail for exactly this reason.

## How to reproduce

```bash
mvn -pl engine-core test-compile \
    dependency:build-classpath -Dmdep.outputFile=cp.txt -Dmdep.includeScope=test
java -cp "engine-core/target/classes;engine-core/target/test-classes;$(cat engine-core/cp.txt)" \
    com.limitbook.engine.bench.MatchingBenchmark
```

(On Windows PowerShell, join the classpath with `;`.)
