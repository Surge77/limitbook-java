package com.limitbook.engine;

import static com.limitbook.engine.Matchers.accepted;
import static com.limitbook.engine.Matchers.trades;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limitbook.engine.model.Order;
import com.limitbook.engine.model.Side;
import com.limitbook.engine.model.Trade;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LimitMatchingTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    @Test
    void unmatchedLimitRestsInBook() {
        var events = engine.submit(Order.limit(1, 1, Side.BUY, 100, 10));
        assertEquals(10, engine.restingQuantity(1));
        assertEquals(100, engine.bestBidPrice());
        assertEquals(-1, engine.bestAskPrice());
        assertEquals(10, accepted(events).orElseThrow().restingQuantity());
    }

    @Test
    void exactMatchProducesSingleTradeAndEmptiesBook() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 10));
        var events = engine.submit(Order.limit(2, 2, Side.BUY, 100, 10));

        List<Trade> ts = trades(events);
        assertEquals(1, ts.size());
        Trade t = ts.get(0);
        assertEquals(100, t.price());
        assertEquals(10, t.quantity());
        assertEquals(2, t.takerOrderId());
        assertEquals(1, t.makerOrderId());
        assertEquals(Side.BUY, t.aggressorSide());
        assertEquals(-1, engine.bestBidPrice());
        assertEquals(-1, engine.bestAskPrice());
    }

    @Test
    void aggressiveBuyExecutesAtRestingMakerPrice() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 10));
        var events = engine.submit(Order.limit(2, 2, Side.BUY, 105, 10)); // willing to pay up to 105

        Trade t = trades(events).get(0);
        assertEquals(100, t.price(), "trade must execute at maker price, not aggressor price");
    }

    @Test
    void noMatchWhenBuyBelowBestAsk() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 10));
        var events = engine.submit(Order.limit(2, 2, Side.BUY, 99, 10));
        assertTrue(trades(events).isEmpty());
        assertEquals(99, engine.bestBidPrice());
        assertEquals(100, engine.bestAskPrice());
    }

    @Test
    void partialFillLeavesRestingMakerRemainder() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 100));
        var events = engine.submit(Order.limit(2, 2, Side.BUY, 100, 60));

        assertEquals(60, trades(events).get(0).quantity());
        assertEquals(40, engine.restingQuantity(1));
        assertEquals(-1, engine.restingQuantity(2));
    }

    @Test
    void aggressorRemainderRestsAsNewLevel() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 60));
        var events = engine.submit(Order.limit(2, 2, Side.BUY, 100, 100));

        assertEquals(60, trades(events).get(0).quantity());
        assertEquals(40, engine.restingQuantity(2));
        assertEquals(100, engine.bestBidPrice());
        assertEquals(-1, engine.bestAskPrice());
    }

    @Test
    void sweepsMultiplePriceLevelsBestFirst() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 50));
        engine.submit(Order.limit(2, 1, Side.SELL, 101, 50));
        var events = engine.submit(Order.limit(3, 2, Side.BUY, 101, 80));

        List<Trade> ts = trades(events);
        assertEquals(2, ts.size());
        assertEquals(100, ts.get(0).price());
        assertEquals(50, ts.get(0).quantity());
        assertEquals(101, ts.get(1).price());
        assertEquals(30, ts.get(1).quantity());
        assertEquals(20, engine.restingQuantity(2));
    }

    @Test
    void honorsFifoTimePriorityWithinLevel() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 50)); // first in line
        engine.submit(Order.limit(2, 1, Side.SELL, 100, 50)); // second
        var events = engine.submit(Order.limit(3, 2, Side.BUY, 100, 70));

        List<Trade> ts = trades(events);
        assertEquals(50, ts.get(0).quantity());
        assertEquals(1, ts.get(0).makerOrderId(), "oldest order at the level fills first");
        assertEquals(20, ts.get(1).quantity());
        assertEquals(2, ts.get(1).makerOrderId());
        assertEquals(30, engine.restingQuantity(2));
    }

    @Test
    void bookNeverCrossesAfterMatching() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 10));
        engine.submit(Order.limit(2, 2, Side.BUY, 100, 10));
        long bid = engine.bestBidPrice();
        long ask = engine.bestAskPrice();
        assertTrue(bid == -1 || ask == -1 || bid < ask, "best bid must be below best ask");
    }
}
