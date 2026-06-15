package com.limitbook.engine;

import static com.limitbook.engine.Matchers.hasReject;
import static com.limitbook.engine.Matchers.trades;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limitbook.engine.model.EngineEvent;
import com.limitbook.engine.model.Order;
import com.limitbook.engine.model.RejectReason;
import com.limitbook.engine.model.Side;
import com.limitbook.engine.model.Trade;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CancelAmendTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    private static EngineEvent.OrderAmended amend(List<EngineEvent> events) {
        return events.stream()
                .filter(EngineEvent.OrderAmended.class::isInstance)
                .map(EngineEvent.OrderAmended.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void cancelRemovesRestingOrder() {
        engine.submit(Order.limit(1, 1, Side.BUY, 100, 10));
        var events = engine.cancel(1);
        assertTrue(events.get(0) instanceof EngineEvent.OrderCanceled);
        assertEquals(-1, engine.bestBidPrice());
    }

    @Test
    void cancelUnknownOrderIsRejected() {
        var events = engine.cancel(999);
        assertTrue(hasReject(events, RejectReason.UNKNOWN_ORDER));
    }

    @Test
    void amendQuantityDecreaseKeepsTimePriority() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 50)); // first
        engine.submit(Order.limit(2, 1, Side.SELL, 100, 50)); // second
        var amendEvents = engine.amend(1, 100, 30);
        assertFalse(amend(amendEvents).lostPriority());

        var events = engine.submit(Order.limit(3, 2, Side.BUY, 100, 40));
        List<Trade> ts = trades(events);
        assertEquals(1, ts.get(0).makerOrderId(), "amended order keeps its place at the front");
        assertEquals(30, ts.get(0).quantity());
        assertEquals(2, ts.get(1).makerOrderId());
    }

    @Test
    void amendQuantityIncreaseLosesTimePriority() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 50));
        engine.submit(Order.limit(2, 1, Side.SELL, 100, 50));
        var amendEvents = engine.amend(1, 100, 80);
        assertTrue(amend(amendEvents).lostPriority());

        var events = engine.submit(Order.limit(3, 2, Side.BUY, 100, 60));
        List<Trade> ts = trades(events);
        assertEquals(2, ts.get(0).makerOrderId(), "untouched order now ahead of the re-queued one");
    }

    @Test
    void amendPriceChangeMovesLevelAndLosesPriority() {
        engine.submit(Order.limit(1, 1, Side.BUY, 100, 10));
        var amendEvents = engine.amend(1, 101, 10);
        assertTrue(amend(amendEvents).lostPriority());
        assertEquals(101, engine.bestBidPrice());
    }

    @Test
    void amendUnknownOrderIsRejected() {
        assertTrue(hasReject(engine.amend(42, 100, 5), RejectReason.UNKNOWN_ORDER));
    }

    @Test
    void amendToInvalidPriceIsRejected() {
        engine.submit(Order.limit(1, 1, Side.BUY, 100, 10));
        assertTrue(hasReject(engine.amend(1, 0, 5), RejectReason.INVALID_PRICE));
    }
}
