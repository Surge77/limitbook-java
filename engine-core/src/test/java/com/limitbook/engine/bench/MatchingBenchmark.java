package com.limitbook.engine.bench;

import com.limitbook.engine.MatchingEngine;
import com.limitbook.engine.model.EngineEvent;
import com.limitbook.engine.model.Order;
import com.limitbook.engine.model.Side;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * Microbenchmarks for the matching core. {@code throughput} measures single-thread
 * orders/sec on a realistic mixed flow; {@code matchLatency} samples per-order
 * latency (p50/p99/p999) in microseconds in steady state.
 */
public class MatchingBenchmark {

    private static final int BATCH = 1_000_000;
    private static final int SEED_DEPTH = 50_000;
    private static final long MID = 10_000;
    private static final long SPREAD = 50;

    @State(Scope.Thread)
    public static class BatchState {
        Order[] orders;

        @Setup(Level.Iteration)
        public void setup() {
            Random rnd = new Random(42);
            orders = new Order[BATCH];
            for (int i = 0; i < BATCH; i++) {
                Side side = rnd.nextBoolean() ? Side.BUY : Side.SELL;
                long price = MID + rnd.nextInt((int) (2 * SPREAD + 1)) - SPREAD;
                long qty = 1 + rnd.nextInt(20);
                orders[i] = Order.limit(i + 1L, 1 + (i % 4), side, price, qty);
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(BATCH)
    public void throughput(BatchState s, Blackhole bh) {
        MatchingEngine engine = new MatchingEngine();
        for (Order o : s.orders) {
            bh.consume(engine.submit(o));
        }
    }

    @State(Scope.Thread)
    public static class LatencyState {
        MatchingEngine engine;
        long nextId;
        boolean buy;

        @Setup(Level.Iteration)
        public void setup() {
            engine = new MatchingEngine();
            long id = 0;
            for (int i = 0; i < SEED_DEPTH; i++) {
                id++;
                engine.submit(Order.limit(id, 1, Side.BUY, MID - 1 - (i % SPREAD), 100));
                id++;
                engine.submit(Order.limit(id, 2, Side.SELL, MID + 1 + (i % SPREAD), 100));
            }
            nextId = id + 1;
            buy = true;
        }

        Order nextProbe() {
            Side side = buy ? Side.BUY : Side.SELL;
            buy = !buy;
            long price = side == Side.BUY ? MID + SPREAD : MID - SPREAD; // crosses the spread
            return Order.limit(nextId++, 3, side, price, 1);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public List<EngineEvent> matchLatency(LatencyState s) {
        return s.engine.submit(s.nextProbe());
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder()
                .include(MatchingBenchmark.class.getSimpleName())
                .warmupIterations(3)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(5)
                .measurementTime(TimeValue.seconds(1))
                .forks(1)
                .build()).run();
    }
}
