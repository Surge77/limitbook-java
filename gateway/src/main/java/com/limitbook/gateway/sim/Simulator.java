package com.limitbook.gateway.sim;

import com.limitbook.engine.EngineCommand;
import com.limitbook.engine.EngineService;
import com.limitbook.engine.model.Order;
import com.limitbook.engine.model.Side;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Synthetic order flow: a mid price does a bounded random walk while limit orders
 * are sprayed around it (some crossing, some resting) plus occasional market
 * orders, so the book and trade tape stay lively. Drives load for stress-testing
 * and makes the UI look alive on demand.
 */
@Component
public class Simulator {

    private static final int MAX_RATE = 200_000;
    private static final long TICK_SLEEP_MS = 5;
    private static final int TICKS_PER_SEC = 200;
    private static final long MID_FLOOR = 9_000;
    private static final long MID_CEIL = 11_000;
    private static final int PRICE_BAND = 5;
    private static final int MARKET_ORDER_PERCENT = 5;

    private final EngineService engine;
    private final AtomicLong idGenerator;
    private final Random random = new Random();

    private volatile boolean running;
    private volatile int ratePerSec;
    private long mid = 10_000;
    private Thread thread;

    public Simulator(EngineService engine, AtomicLong orderIdGenerator) {
        this.engine = engine;
        this.idGenerator = orderIdGenerator;
    }

    public synchronized int start(int rate) {
        this.ratePerSec = Math.max(1, Math.min(rate, MAX_RATE));
        if (!running) {
            running = true;
            thread = new Thread(this::run, "order-simulator");
            thread.setDaemon(true);
            thread.start();
        }
        return ratePerSec;
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int rate() {
        return ratePerSec;
    }

    private void run() {
        while (running) {
            int batch = Math.max(1, ratePerSec / TICKS_PER_SEC);
            for (int i = 0; i < batch; i++) {
                engine.offer(new EngineCommand.Submit(nextOrder()));
            }
            try {
                Thread.sleep(TICK_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private Order nextOrder() {
        mid += random.nextInt(3) - 1; // -1, 0, +1
        mid = Math.max(MID_FLOOR, Math.min(MID_CEIL, mid));
        long id = idGenerator.incrementAndGet();
        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
        long qty = 1 + random.nextInt(20);
        if (random.nextInt(100) < MARKET_ORDER_PERCENT) {
            return Order.market(id, randomAccount(), side, qty);
        }
        long price = mid + (random.nextInt(2 * PRICE_BAND + 1) - PRICE_BAND);
        return Order.limit(id, randomAccount(), side, Math.max(1, price), qty);
    }

    private long randomAccount() {
        return 1 + random.nextInt(5);
    }
}
