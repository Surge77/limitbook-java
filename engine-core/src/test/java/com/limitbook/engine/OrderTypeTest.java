package com.limitbook.engine;

import static com.limitbook.engine.Matchers.canceled;
import static com.limitbook.engine.Matchers.hasReject;
import static com.limitbook.engine.Matchers.trades;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limitbook.engine.model.Order;
import com.limitbook.engine.model.RejectReason;
import com.limitbook.engine.model.Side;
import com.limitbook.engine.model.Trade;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderTypeTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    // ---- MARKET ----

    @Test
    void marketOrderSweepsMultipleLevels() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 50));
        engine.submit(Order.limit(2, 1, Side.SELL, 101, 50));
        var events = engine.submit(Order.market(3, 2, Side.BUY, 80));

        List<Trade> ts = trades(events);
        assertEquals(2, ts.size());
        assertEquals(80, ts.stream().mapToLong(Trade::quantity).sum());
        assertEquals(20, engine.restingQuantity(2));
        assertEquals(-1, engine.bestBidPrice());
    }

    @Test
    void marketRemainderIsCanceledWhenLiquidityRunsOut() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 30));
        var events = engine.submit(Order.market(2, 2, Side.BUY, 50));

        assertEquals(30, trades(events).get(0).quantity());
        assertEquals(20, canceled(events).orElseThrow().remainingQuantity());
        assertEquals(-1, engine.bestBidPrice());
    }

    // ---- IOC ----

    @Test
    void iocFillsWhatItCanAndCancelsTheRest() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 30));
        var events = engine.submit(Order.ioc(2, 2, Side.BUY, 100, 50));

        assertEquals(30, trades(events).get(0).quantity());
        assertEquals(20, canceled(events).orElseThrow().remainingQuantity());
        assertEquals(-1, engine.restingQuantity(2));
    }

    @Test
    void iocNeverRestsEvenWithNoFill() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 30));
        var events = engine.submit(Order.ioc(2, 2, Side.BUY, 99, 50));

        assertTrue(trades(events).isEmpty());
        assertEquals(50, canceled(events).orElseThrow().remainingQuantity());
        assertEquals(-1, engine.bestBidPrice());
    }

    // ---- FOK ----

    @Test
    void fokRejectedWhenNotFullyFillable() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 30));
        var events = engine.submit(Order.fok(2, 2, Side.BUY, 100, 50));

        assertTrue(trades(events).isEmpty());
        assertTrue(hasReject(events, RejectReason.FOK_NOT_FULLY_FILLABLE));
        assertEquals(30, engine.restingQuantity(1), "book untouched on FOK reject");
    }

    @Test
    void fokFillsAtomicallyWhenLiquiditySuffices() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 50));
        engine.submit(Order.limit(2, 1, Side.SELL, 101, 50));
        var events = engine.submit(Order.fok(3, 2, Side.BUY, 101, 80));

        assertEquals(80, trades(events).stream().mapToLong(Trade::quantity).sum());
        assertEquals(20, engine.restingQuantity(2));
    }

    // ---- POST-ONLY ----

    @Test
    void postOnlyRejectedWhenItWouldCross() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 10));
        var events = engine.submit(Order.postOnly(2, 2, Side.BUY, 100, 10));

        assertTrue(trades(events).isEmpty());
        assertTrue(hasReject(events, RejectReason.POST_ONLY_WOULD_CROSS));
    }

    @Test
    void postOnlyRestsWhenItDoesNotCross() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 10));
        var events = engine.submit(Order.postOnly(2, 2, Side.BUY, 99, 10));

        assertTrue(trades(events).isEmpty());
        assertEquals(99, engine.bestBidPrice());
        assertEquals(10, engine.restingQuantity(2));
    }
}
