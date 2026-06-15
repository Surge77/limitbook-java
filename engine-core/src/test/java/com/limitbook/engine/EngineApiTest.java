package com.limitbook.engine;

import static com.limitbook.engine.Matchers.hasReject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limitbook.engine.model.Order;
import com.limitbook.engine.model.RejectReason;
import com.limitbook.engine.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EngineApiTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    @Test
    void lastTradePriceTracksMostRecentExecution() {
        assertEquals(0, engine.lastTradePrice());
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 10));
        engine.submit(Order.limit(2, 2, Side.BUY, 100, 10));
        assertEquals(100, engine.lastTradePrice());
    }

    @Test
    void defaultSnapshotReflectsBothSides() {
        engine.submit(Order.limit(1, 1, Side.BUY, 99, 10));
        engine.submit(Order.limit(2, 2, Side.SELL, 101, 7));
        var snap = engine.snapshot();
        assertEquals(99, snap.bids().get(0).price());
        assertEquals(101, snap.asks().get(0).price());
        assertEquals(7, snap.asks().get(0).quantity());
    }

    @Test
    void duplicateRestingOrderIdIsRejected() {
        engine.submit(Order.limit(1, 1, Side.BUY, 100, 10));
        var events = engine.submit(Order.limit(1, 1, Side.BUY, 101, 5));
        assertTrue(hasReject(events, RejectReason.DUPLICATE_ORDER_ID));
        assertEquals(100, engine.bestBidPrice(), "original order untouched");
    }

    @Test
    void duplicateIdAgainstParkedStopIsRejected() {
        engine.submit(Order.stop(5, 1, Side.BUY, 100, 10));
        var events = engine.submit(Order.limit(5, 1, Side.BUY, 99, 5));
        assertTrue(hasReject(events, RejectReason.DUPLICATE_ORDER_ID));
        assertFalse(engine.restingQuantity(5) > 0, "rejected order did not rest");
    }
}
