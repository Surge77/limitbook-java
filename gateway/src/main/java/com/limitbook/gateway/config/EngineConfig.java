package com.limitbook.gateway.config;

import com.limitbook.engine.EngineService;
import com.limitbook.engine.model.StpPolicy;
import com.limitbook.gateway.metrics.EngineMetrics;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the single-writer engine into the Spring context and manages its lifecycle. */
@Configuration
public class EngineConfig {

    private static final int QUEUE_CAPACITY = 1 << 16;
    private static final int SNAPSHOT_DEPTH = 25;
    private static final int TAPE_CAPACITY = 500;

    @Bean(initMethod = "start", destroyMethod = "stop")
    public EngineService engineService(EngineMetrics metrics) {
        return new EngineService(StpPolicy.CANCEL_NEWEST, metrics, QUEUE_CAPACITY, SNAPSHOT_DEPTH, TAPE_CAPACITY);
    }

    /** Shared monotonic id source so REST orders and the simulator never collide. */
    @Bean
    public AtomicLong orderIdGenerator() {
        return new AtomicLong();
    }
}
