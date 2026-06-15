package com.limitbook.gateway.metrics;

import com.limitbook.engine.EngineListener;
import com.limitbook.engine.EngineResult;
import com.limitbook.engine.model.EngineEvent;
import com.limitbook.engine.model.Trade;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Records engine activity into Micrometer (exposed at {@code /actuator/prometheus})
 * and exposes the live percentiles the UI metrics panel reads. Runs on the engine
 * thread via {@link EngineListener} — every operation here is O(1) and allocation-free.
 */
@Component
public class EngineMetrics implements EngineListener {

    private final Timer matchLatency;
    private final Counter orders;
    private final Counter trades;
    private final Counter rejects;

    public EngineMetrics(MeterRegistry registry) {
        this.matchLatency = Timer.builder("limitbook.match.latency")
                .description("Time to process one command in the matching engine")
                .publishPercentiles(0.5, 0.99, 0.999)
                .register(registry);
        this.orders = Counter.builder("limitbook.orders.processed").register(registry);
        this.trades = Counter.builder("limitbook.trades.executed").register(registry);
        this.rejects = Counter.builder("limitbook.orders.rejected").register(registry);
    }

    @Override
    public void onResult(EngineResult result) {
        matchLatency.record(result.latencyNanos(), TimeUnit.NANOSECONDS);
        orders.increment();
        for (EngineEvent e : result.events()) {
            if (e instanceof Trade) {
                trades.increment();
            } else if (e instanceof EngineEvent.OrderRejected) {
                rejects.increment();
            }
        }
    }

    public long ordersTotal() {
        return (long) orders.count();
    }

    public long tradesTotal() {
        return (long) trades.count();
    }

    public double latencyP50Micros() {
        return percentileMicros(0.5);
    }

    public double latencyP99Micros() {
        return percentileMicros(0.99);
    }

    private double percentileMicros(double percentile) {
        for (ValueAtPercentile v : matchLatency.takeSnapshot().percentileValues()) {
            if (Math.abs(v.percentile() - percentile) < 1e-9) {
                return v.value(TimeUnit.MICROSECONDS);
            }
        }
        return 0.0;
    }
}
