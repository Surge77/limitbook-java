package com.limitbook.engine;

import static com.limitbook.engine.Matchers.canceled;
import static com.limitbook.engine.Matchers.trades;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limitbook.engine.model.Order;
import com.limitbook.engine.model.Side;
import com.limitbook.engine.model.StpPolicy;
import org.junit.jupiter.api.Test;

class SelfTradePreventionTest {

    private static final long ACCT = 7;

    @Test
    void cancelNewestKillsTheAggressorAndKeepsResting() {
        var engine = new MatchingEngine(StpPolicy.CANCEL_NEWEST);
        engine.submit(Order.limit(1, ACCT, Side.SELL, 100, 50));
        var events = engine.submit(Order.limit(2, ACCT, Side.BUY, 100, 50));

        assertTrue(trades(events).isEmpty());
        assertEquals(50, canceled(events).orElseThrow().remainingQuantity());
        assertEquals(50, engine.restingQuantity(1), "resting order untouched");
    }

    @Test
    void cancelOldestRemovesRestingAndLetsAggressorContinue() {
        var engine = new MatchingEngine(StpPolicy.CANCEL_OLDEST);
        engine.submit(Order.limit(1, ACCT, Side.SELL, 100, 50));
        var events = engine.submit(Order.limit(2, ACCT, Side.BUY, 100, 60));

        assertTrue(trades(events).isEmpty());
        assertEquals(-1, engine.restingQuantity(1), "oldest canceled");
        assertEquals(60, engine.restingQuantity(2), "aggressor rests with no liquidity left");
    }

    @Test
    void decrementShrinksBothSidesWithoutTrading() {
        var engine = new MatchingEngine(StpPolicy.DECREMENT);
        engine.submit(Order.limit(1, ACCT, Side.SELL, 100, 50));
        var events = engine.submit(Order.limit(2, ACCT, Side.BUY, 100, 30));

        assertTrue(trades(events).isEmpty());
        assertEquals(20, engine.restingQuantity(1), "maker decremented by 30");
        assertEquals(-1, engine.restingQuantity(2), "aggressor fully consumed by decrement");
    }

    @Test
    void nonePolicyAllowsAnAccountToTradeWithItself() {
        var engine = new MatchingEngine(StpPolicy.NONE);
        engine.submit(Order.limit(1, ACCT, Side.SELL, 100, 50));
        var events = engine.submit(Order.limit(2, ACCT, Side.BUY, 100, 50));
        assertEquals(1, trades(events).size(), "NONE permits self-trades");
    }

    @Test
    void differentAccountsTradeNormallyUnderStp() {
        var engine = new MatchingEngine(StpPolicy.CANCEL_NEWEST);
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 50));
        var events = engine.submit(Order.limit(2, 2, Side.BUY, 100, 50));
        assertEquals(1, trades(events).size());
    }
}
