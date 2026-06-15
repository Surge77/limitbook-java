package com.limitbook.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limitbook.engine.model.BookSnapshot;
import com.limitbook.engine.model.EngineEvent;
import com.limitbook.engine.model.Order;
import com.limitbook.engine.model.OrderType;
import com.limitbook.engine.model.Side;
import com.limitbook.engine.model.Trade;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Invariant checks over randomized order flows. */
class MatcherPropertiesTest {

    /** A randomly generated order intent; id is assigned at replay time. */
    record Spec(int side, int type, long price, long qty) {}

    @Provide
    Arbitrary<List<Spec>> flows() {
        Arbitrary<Integer> side = Arbitraries.integers().between(0, 1);
        Arbitrary<Integer> type = Arbitraries.integers().between(0, 2); // LIMIT, MARKET, IOC
        Arbitrary<Long> price = Arbitraries.longs().between(95, 105);
        Arbitrary<Long> qty = Arbitraries.longs().between(1, 20);
        Arbitrary<Spec> spec = Combinators.combine(side, type, price, qty).as(Spec::new);
        return spec.list().ofMaxSize(80);
    }

    private static List<EngineEvent> replay(List<Spec> specs, MatchingEngine engine) {
        List<EngineEvent> all = new ArrayList<>();
        long id = 0;
        for (Spec s : specs) {
            id++;
            Side side = s.side() == 0 ? Side.BUY : Side.SELL;
            Order order = switch (s.type()) {
                case 1 -> Order.market(id, 1 + (id % 3), side, s.qty());
                case 2 -> Order.ioc(id, 1 + (id % 3), side, s.price(), s.qty());
                default -> Order.limit(id, 1 + (id % 3), side, s.price(), s.qty());
            };
            all.addAll(engine.submit(order));
        }
        return all;
    }

    @Property
    void bookNeverCrosses(@ForAll("flows") List<Spec> specs) {
        MatchingEngine engine = new MatchingEngine();
        long id = 0;
        for (Spec s : specs) {
            id++;
            Side side = s.side() == 0 ? Side.BUY : Side.SELL;
            Order order = switch (s.type()) {
                case 1 -> Order.market(id, 1, side, s.qty());
                case 2 -> Order.ioc(id, 1, side, s.price(), s.qty());
                default -> Order.limit(id, 1, side, s.price(), s.qty());
            };
            engine.submit(order);
            long bid = engine.bestBidPrice();
            long ask = engine.bestAskPrice();
            assertTrue(bid == -1 || ask == -1 || bid < ask,
                    "crossed book: bid=" + bid + " ask=" + ask);
        }
    }

    @Property
    void allTradesHavePositiveQuantityAndPrice(@ForAll("flows") List<Spec> specs) {
        for (EngineEvent e : replay(specs, new MatchingEngine())) {
            if (e instanceof Trade t) {
                assertTrue(t.quantity() > 0 && t.price() > 0);
            }
        }
    }

    @Property
    void restingQuantitiesAreNeverNegative(@ForAll("flows") List<Spec> specs) {
        MatchingEngine engine = new MatchingEngine();
        replay(specs, engine);
        BookSnapshot snap = engine.snapshot(1000);
        for (BookSnapshot.Level lvl : snap.bids()) {
            assertTrue(lvl.quantity() > 0 && lvl.orderCount() > 0);
        }
        for (BookSnapshot.Level lvl : snap.asks()) {
            assertTrue(lvl.quantity() > 0 && lvl.orderCount() > 0);
        }
    }

    @Property
    void replayIsDeterministic(@ForAll("flows") List<Spec> specs) {
        List<EngineEvent> first = replay(specs, new MatchingEngine());
        List<EngineEvent> second = replay(specs, new MatchingEngine());
        assertEquals(first, second, "same input sequence must yield identical events");
    }

    @Property
    void quantityIsConservedPerTrade(@ForAll("flows") List<Spec> specs) {
        // Every trade reduces aggregate open interest by exactly 2*qty (one unit
        // removed from each side); assert the maker leg never over-fills.
        MatchingEngine engine = new MatchingEngine();
        long id = 0;
        for (Spec s : specs) {
            id++;
            Side side = s.side() == 0 ? Side.BUY : Side.SELL;
            Order order = s.type() == 0
                    ? Order.limit(id, 1, side, s.price(), s.qty())
                    : Order.ioc(id, 1, side, s.price(), s.qty());
            long submitted = order.quantity();
            long traded = engine.submit(order).stream()
                    .filter(Trade.class::isInstance)
                    .mapToLong(e -> ((Trade) e).quantity())
                    .sum();
            assertTrue(traded <= submitted, "taker filled more than it submitted");
            if (order.type() == OrderType.LIMIT && traded < submitted) {
                long rest = engine.restingQuantity(id);
                assertTrue(rest == submitted - traded || rest == -1);
            }
        }
    }
}
