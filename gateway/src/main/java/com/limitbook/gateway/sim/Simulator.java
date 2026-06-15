package com.limitbook.gateway.sim;

import com.limitbook.engine.EngineCommand;
import com.limitbook.engine.EngineService;
import com.limitbook.engine.model.BookSnapshot;
import com.limitbook.engine.model.Order;
import com.limitbook.engine.model.Side;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Market-making style synthetic flow. Each step reads the live best bid/ask and
 * either (a) posts a passive maker order just inside the spread, (b) crosses by a
 * tick as a taker to generate a trade, or (c) cancels one of its own resting
 * orders. This keeps a tight two-sided book with bounded depth — unlike a uniform
 * random spray, which lets takers hollow out the inside while passive orders pile
 * up at the edges (spread blows out, sizes grow without bound).
 */
@Component
public class Simulator {

    private static final int MAX_RATE = 200_000;
    private static final long TICK_SLEEP_MS = 5;
    private static final int TICKS_PER_SEC = 200;
    private static final long DEFAULT_MID = 10_000;
    private static final int TAKER_PERCENT = 40;
    private static final int CANCEL_PERCENT = 18;
    private static final int DEPTH_LEVELS = 12;
    private static final int TAKER_DEPTH = 2;
    private static final int MAX_QTY = 20;
    private static final int MAX_TRACKED_IDS = 4_000;
    private static final int ACCOUNTS = 5;

    private final EngineService engine;
    private final AtomicLong idGenerator;
    private final Random random = new Random();
    private final Deque<Long> restingIds = new ArrayDeque<>();

    private volatile boolean running;
    private volatile int ratePerSec;
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
                engine.offer(nextCommand());
            }
            try {
                Thread.sleep(TICK_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private EngineCommand nextCommand() {
        if (random.nextInt(100) < CANCEL_PERCENT) {
            Long id = pollTrackedId();
            if (id != null) {
                return new EngineCommand.Cancel(id);
            }
        }

        BookSnapshot snap = engine.latestSnapshot();
        long bestBid = snap.bids().isEmpty() ? -1 : snap.bids().get(0).price();
        long bestAsk = snap.asks().isEmpty() ? -1 : snap.asks().get(0).price();
        long id = idGenerator.incrementAndGet();
        long account = 1 + random.nextInt(ACCOUNTS);
        long qty = 1 + random.nextInt(MAX_QTY);

        if (bestBid < 0 || bestAsk < 0) {
            return bootstrap(id, account, qty, bestBid, bestAsk);
        }

        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
        if (random.nextInt(100) < TAKER_PERCENT) {
            // cross the spread: trades against resting liquidity, never tracked
            long price = side == Side.BUY
                    ? bestAsk + random.nextInt(TAKER_DEPTH)
                    : Math.max(1, bestBid - random.nextInt(TAKER_DEPTH));
            return new EngineCommand.Submit(Order.limit(id, account, side, price, qty));
        }

        // passive maker: rest at or behind the best, skewed toward the top of book
        // (offset mostly 0 → refills the inside; occasionally deeper → builds the ladder)
        double r = random.nextDouble();
        int offset = (int) (r * r * DEPTH_LEVELS);
        long price = side == Side.BUY ? Math.max(1, bestBid - offset) : bestAsk + offset;
        track(id);
        return new EngineCommand.Submit(Order.limit(id, account, side, price, qty));
    }

    private EngineCommand bootstrap(long id, long account, long qty, long bestBid, long bestAsk) {
        Side side;
        long price;
        if (bestBid < 0 && bestAsk < 0) {
            side = random.nextBoolean() ? Side.BUY : Side.SELL;
            price = side == Side.BUY ? DEFAULT_MID - 1 : DEFAULT_MID + 1;
        } else if (bestBid < 0) {
            side = Side.BUY;
            price = Math.max(1, bestAsk - 1);
        } else {
            side = Side.SELL;
            price = bestBid + 1;
        }
        track(id);
        return new EngineCommand.Submit(Order.limit(id, account, side, price, qty));
    }

    private synchronized void track(long id) {
        restingIds.addLast(id);
        if (restingIds.size() > MAX_TRACKED_IDS) {
            restingIds.pollFirst();
        }
    }

    private synchronized Long pollTrackedId() {
        return restingIds.pollFirst();
    }
}
