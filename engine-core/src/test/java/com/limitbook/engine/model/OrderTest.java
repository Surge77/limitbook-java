package com.limitbook.engine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void limitFactoryBuildsAPricedRestingOrder() {
        Order o = Order.limit(1, 100, Side.BUY, 10_000, 5);
        assertEquals(OrderType.LIMIT, o.type());
        assertEquals(10_000, o.price());
        assertEquals(5, o.quantity());
        assertEquals(Order.NO_PRICE, o.stopPrice());
    }

    @Test
    void marketOrderCarriesNoPrice() {
        Order o = Order.market(2, 100, Side.SELL, 3);
        assertEquals(Order.NO_PRICE, o.price());
        assertEquals(OrderType.MARKET, o.type());
    }

    @Test
    void stopLimitCarriesBothStopAndLimit() {
        Order o = Order.stopLimit(3, 100, Side.BUY, 10_500, 10_600, 4);
        assertEquals(10_500, o.stopPrice());
        assertEquals(10_600, o.price());
    }

    @Test
    void rejectsNonPositiveId() {
        assertThrows(IllegalArgumentException.class, () -> Order.limit(0, 1, Side.BUY, 100, 1));
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> Order.limit(1, 1, Side.BUY, 100, 0));
    }

    @Test
    void rejectsNonPositiveLimitPrice() {
        assertThrows(IllegalArgumentException.class, () -> Order.limit(1, 1, Side.BUY, 0, 1));
    }

    @Test
    void rejectsPriceOnMarketOrder() {
        assertThrows(IllegalArgumentException.class,
                () -> new Order(1, 1, Side.BUY, OrderType.MARKET, 100, 1, Order.NO_PRICE));
    }

    @Test
    void rejectsMissingStopPriceOnStop() {
        assertThrows(IllegalArgumentException.class,
                () -> new Order(1, 1, Side.BUY, OrderType.STOP, Order.NO_PRICE, 1, 0));
    }

    @Test
    void rejectsStopPriceOnPlainLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new Order(1, 1, Side.BUY, OrderType.LIMIT, 100, 1, 50));
    }

    @Test
    void sideOppositeFlips() {
        assertEquals(Side.SELL, Side.BUY.opposite());
        assertEquals(Side.BUY, Side.SELL.opposite());
    }
}
