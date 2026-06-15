package com.limitbook.engine;

import static com.limitbook.engine.Matchers.trades;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limitbook.engine.model.Order;
import com.limitbook.engine.model.Side;
import com.limitbook.engine.model.Trade;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StopOrderTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    @Test
    void buyStopParksThenFiresAsMarketWhenLastPriceCrosses() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 20));   // resting ask
        engine.submit(Order.stop(2, 2, Side.BUY, 100, 5));      // parked until last >= 100
        assertEquals(-1, engine.restingQuantity(2), "stop is parked, not in book");

        // a trade at 100 sets last price and triggers the stop
        var events = engine.submit(Order.limit(3, 3, Side.BUY, 100, 5));
        List<Trade> ts = trades(events);
        assertEquals(2, ts.size(), "the triggering trade plus the stop's own fill");
        assertEquals(10, engine.restingQuantity(1), "20 - 5 - 5 left on the ask");
    }

    @Test
    void sellStopFiresWhenLastPriceFallsToTrigger() {
        engine.submit(Order.limit(1, 1, Side.BUY, 100, 20));    // resting bid
        engine.submit(Order.stop(2, 2, Side.SELL, 100, 5));     // fires when last <= 100
        var events = engine.submit(Order.limit(3, 3, Side.SELL, 100, 5));
        assertEquals(2, trades(events).size());
        assertEquals(10, engine.restingQuantity(1));
    }

    @Test
    void stopLimitActivatesAsLimitAndRestsRemainder() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 10));         // ask 10 @100
        engine.submit(Order.stopLimit(2, 2, Side.BUY, 100, 100, 10)); // stop 100, limit 100, qty 10
        engine.submit(Order.limit(3, 3, Side.BUY, 100, 5));           // trade @100 (eats 5) triggers stop

        // stop-limit buys the remaining 5 of the ask, then rests its other 5 as a bid @100
        assertEquals(5, engine.restingQuantity(2));
        assertEquals(100, engine.bestBidPrice());
        assertEquals(-1, engine.restingQuantity(1));
    }

    @Test
    void parkedStopCanBeCanceledBeforeTriggering() {
        engine.submit(Order.limit(1, 1, Side.SELL, 100, 20));
        engine.submit(Order.stop(2, 2, Side.BUY, 100, 5));
        var cancelEvents = engine.cancel(2);
        assertTrue(cancelEvents.get(0) instanceof com.limitbook.engine.model.EngineEvent.OrderCanceled);

        var events = engine.submit(Order.limit(3, 3, Side.BUY, 100, 5));
        assertEquals(1, trades(events).size(), "canceled stop must not fire");
    }
}
