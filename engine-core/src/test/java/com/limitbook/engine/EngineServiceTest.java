package com.limitbook.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limitbook.engine.model.EngineEvent;
import com.limitbook.engine.model.Order;
import com.limitbook.engine.model.Side;
import com.limitbook.engine.model.Trade;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EngineServiceTest {

    private final List<EngineResult> results = new CopyOnWriteArrayList<>();
    private EngineService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.stop();
        }
    }

    private EngineService startService() {
        EngineService s = new EngineService(results::add);
        s.start();
        return s;
    }

    @Test
    void processesCommandsAsynchronouslyAndPublishesResults() {
        service = startService();
        service.offer(new EngineCommand.Submit(Order.limit(1, 1, Side.SELL, 100, 10)));
        service.offer(new EngineCommand.Submit(Order.limit(2, 2, Side.BUY, 100, 10)));

        assertTrue(service.awaitIdle(2000), "engine should drain the queue");
        assertEquals(2, service.ordersProcessed());
        assertEquals(1, service.tradesEmitted());
        assertEquals(1, service.recentTrades(10).size());
        assertEquals(100, service.recentTrades(10).get(0).price());
    }

    @Test
    void appendsBookUpdatedWhenBookChanges() {
        service = startService();
        service.offer(new EngineCommand.Submit(Order.limit(1, 1, Side.BUY, 100, 10)));
        assertTrue(service.awaitIdle(2000));

        boolean hasBookUpdate = results.stream()
                .flatMap(r -> r.events().stream())
                .anyMatch(EngineEvent.BookUpdated.class::isInstance);
        assertTrue(hasBookUpdate, "a resting order must publish a BookUpdated");
        assertEquals(100, service.latestSnapshot().bids().get(0).price());
    }

    @Test
    void rejectionDoesNotPublishBookUpdated() {
        service = startService();
        service.offer(new EngineCommand.Cancel(999)); // unknown -> rejected, no book change
        assertTrue(service.awaitIdle(2000));

        boolean hasBookUpdate = results.stream()
                .flatMap(r -> r.events().stream())
                .anyMatch(EngineEvent.BookUpdated.class::isInstance);
        assertFalse(hasBookUpdate);
    }

    @Test
    void sameCommandSequenceIsDeterministicAcrossServices() {
        List<EngineCommand> script = List.of(
                new EngineCommand.Submit(Order.limit(1, 1, Side.SELL, 101, 30)),
                new EngineCommand.Submit(Order.limit(2, 1, Side.SELL, 100, 20)),
                new EngineCommand.Submit(Order.limit(3, 2, Side.BUY, 101, 40)),
                new EngineCommand.Cancel(1),
                new EngineCommand.Submit(Order.limit(4, 2, Side.BUY, 100, 10)));

        List<Trade> a = runScript(script);
        List<Trade> b = runScript(script);
        assertEquals(a, b, "identical input must yield identical trades");
    }

    private List<Trade> runScript(List<EngineCommand> script) {
        List<Trade> trades = new CopyOnWriteArrayList<>();
        EngineService s = new EngineService(r -> r.events().stream()
                .filter(Trade.class::isInstance).map(Trade.class::cast).forEach(trades::add));
        s.start();
        script.forEach(s::offer);
        assertTrue(s.awaitIdle(2000));
        s.stop();
        return List.copyOf(trades);
    }

    @Test
    void offerReturnsFalseWhenQueueIsFull() {
        EngineService tiny = new EngineService(
                com.limitbook.engine.model.StpPolicy.NONE, results::add, 1, 10, 10);
        // not started: nothing drains the queue
        assertTrue(tiny.offer(new EngineCommand.Cancel(1)));
        assertFalse(tiny.offer(new EngineCommand.Cancel(2)), "second offer hits the bounded capacity");
    }
}
