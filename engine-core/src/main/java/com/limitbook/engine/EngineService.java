package com.limitbook.engine;

import com.limitbook.engine.model.BookSnapshot;
import com.limitbook.engine.model.EngineEvent;
import com.limitbook.engine.model.StpPolicy;
import com.limitbook.engine.model.Trade;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Single-writer (LMAX-style) wrapper around {@link MatchingEngine}. One thread
 * owns the matcher; producers hand {@link EngineCommand}s over a bounded queue.
 * After each command the latest L2 snapshot is published to an
 * {@link AtomicReference} and recent trades to a bounded ring, so reader threads
 * never touch the matcher.
 */
public final class EngineService {

    private static final int DEFAULT_QUEUE_CAPACITY = 1 << 16;
    private static final int DEFAULT_SNAPSHOT_DEPTH = 50;
    private static final int DEFAULT_TAPE_CAPACITY = 1000;
    private static final long POLL_TIMEOUT_MS = 50;

    private final MatchingEngine engine;
    private final BlockingQueue<EngineCommand> queue;
    private final EngineListener listener;
    private final int snapshotDepth;
    private final int tapeCapacity;

    private final Deque<Trade> tape = new ArrayDeque<>();
    private final AtomicReference<BookSnapshot> latestSnapshot =
            new AtomicReference<>(new BookSnapshot(List.of(), List.of(), 0, 0));
    private final LongAdder ordersProcessed = new LongAdder();
    private final LongAdder tradesEmitted = new LongAdder();

    private volatile boolean running;
    private volatile boolean processing;
    private Thread worker;

    public EngineService(EngineListener listener) {
        this(StpPolicy.CANCEL_NEWEST, listener, DEFAULT_QUEUE_CAPACITY, DEFAULT_SNAPSHOT_DEPTH, DEFAULT_TAPE_CAPACITY);
    }

    public EngineService(StpPolicy stpPolicy, EngineListener listener,
                         int queueCapacity, int snapshotDepth, int tapeCapacity) {
        this.engine = new MatchingEngine(stpPolicy);
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.listener = listener;
        this.snapshotDepth = snapshotDepth;
        this.tapeCapacity = tapeCapacity;
    }

    // ---- lifecycle --------------------------------------------------------

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::runLoop, "engine-writer");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
    }

    // ---- producer API -----------------------------------------------------

    /** Enqueue a command. Returns false if the queue is full (backpressure). */
    public boolean offer(EngineCommand command) {
        return queue.offer(command);
    }

    // ---- reader API -------------------------------------------------------

    public BookSnapshot latestSnapshot() {
        return latestSnapshot.get();
    }

    public List<Trade> recentTrades(int max) {
        synchronized (tape) {
            int n = Math.min(max, tape.size());
            List<Trade> out = new ArrayList<>(n);
            var it = tape.descendingIterator();
            while (it.hasNext() && out.size() < n) {
                out.add(it.next());
            }
            return out;
        }
    }

    public long ordersProcessed() {
        return ordersProcessed.sum();
    }

    public long tradesEmitted() {
        return tradesEmitted.sum();
    }

    /** Block until the queue is drained and no command is in flight, or timeout elapses. */
    public boolean awaitIdle(long timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (queue.isEmpty() && !processing) {
                return true;
            }
            Thread.onSpinWait();
        }
        return queue.isEmpty() && !processing;
    }

    // ---- single-writer loop ----------------------------------------------

    private void runLoop() {
        while (running) {
            EngineCommand command;
            try {
                command = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (command == null) {
                continue;
            }
            processing = true;
            try {
                handle(command);
            } finally {
                processing = false;
            }
        }
    }

    private void handle(EngineCommand command) {
        long start = System.nanoTime();
        List<EngineEvent> events = apply(command);
        long latencyNanos = System.nanoTime() - start;

        ordersProcessed.increment();
        recordTrades(events);

        BookSnapshot snapshot = engine.snapshot(snapshotDepth);
        latestSnapshot.set(snapshot);

        List<EngineEvent> published = new ArrayList<>(events);
        if (bookChanged(events)) {
            published.add(new EngineEvent.BookUpdated(snapshot));
        }
        if (listener != null) {
            listener.onResult(new EngineResult(command, published, latencyNanos, snapshot));
        }
    }

    private List<EngineEvent> apply(EngineCommand command) {
        return switch (command) {
            case EngineCommand.Submit s -> engine.submit(s.order());
            case EngineCommand.Cancel c -> engine.cancel(c.orderId());
            case EngineCommand.Amend a -> engine.amend(a.orderId(), a.newPrice(), a.newQuantity());
        };
    }

    private void recordTrades(List<EngineEvent> events) {
        synchronized (tape) {
            for (EngineEvent e : events) {
                if (e instanceof Trade t) {
                    tradesEmitted.increment();
                    tape.addLast(t);
                    if (tape.size() > tapeCapacity) {
                        tape.removeFirst();
                    }
                }
            }
        }
    }

    private static boolean bookChanged(List<EngineEvent> events) {
        for (EngineEvent e : events) {
            if (!(e instanceof EngineEvent.OrderRejected)) {
                return true;
            }
        }
        return false;
    }
}
