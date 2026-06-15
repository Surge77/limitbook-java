package com.limitbook.engine;

import com.limitbook.engine.book.OrderBook;
import com.limitbook.engine.book.RestingOrder;
import com.limitbook.engine.model.BookSnapshot;
import com.limitbook.engine.model.EngineEvent;
import com.limitbook.engine.model.Order;
import com.limitbook.engine.model.OrderType;
import com.limitbook.engine.model.RejectReason;
import com.limitbook.engine.model.Side;
import com.limitbook.engine.model.StpPolicy;
import com.limitbook.engine.model.Trade;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, single-threaded price-time-priority matching engine. Holds an
 * {@link OrderBook} and turns commands ({@code submit} / {@code cancel} /
 * {@code amend}) into a list of {@link EngineEvent}s. No threads, no I/O — the
 * single-writer loop (step 4) wraps an instance of this class.
 *
 * <p>Time priority is the monotonic {@code orderSeq}; trades carry a monotonic
 * {@code tradeSeq}. Both make replay reproducible.
 */
public final class MatchingEngine {

    private static final int DEFAULT_SNAPSHOT_DEPTH = 50;

    private final OrderBook book = new OrderBook();
    private final StpPolicy stpPolicy;
    private final List<ParkedStop> stops = new ArrayList<>();

    private long orderSeq;
    private long tradeSeq;
    private long lastTradePrice;

    private record ParkedStop(Order order, long seq) {}

    public MatchingEngine() {
        this(StpPolicy.NONE);
    }

    public MatchingEngine(StpPolicy stpPolicy) {
        this.stpPolicy = stpPolicy;
    }

    // ---- queries ----------------------------------------------------------

    public long bestBidPrice() {
        return book.bestBidPrice();
    }

    public long bestAskPrice() {
        return book.bestAskPrice();
    }

    public long lastTradePrice() {
        return lastTradePrice;
    }

    /** Remaining quantity of a resting order, or -1 if not resting in the book. */
    public long restingQuantity(long orderId) {
        RestingOrder ro = book.get(orderId);
        return ro == null ? -1 : ro.remaining;
    }

    public BookSnapshot snapshot() {
        return snapshot(DEFAULT_SNAPSHOT_DEPTH);
    }

    public BookSnapshot snapshot(int depth) {
        return book.snapshot(depth, lastTradePrice, orderSeq);
    }

    // ---- commands ---------------------------------------------------------

    public List<EngineEvent> submit(Order order) {
        List<EngineEvent> events = new ArrayList<>();
        process(order, events);
        return events;
    }

    public List<EngineEvent> cancel(long orderId) {
        List<EngineEvent> events = new ArrayList<>();
        RestingOrder removed = book.remove(orderId);
        if (removed != null) {
            events.add(new EngineEvent.OrderCanceled(orderId, removed.remaining));
        } else if (removeParked(orderId)) {
            events.add(new EngineEvent.OrderCanceled(orderId, 0));
        } else {
            events.add(new EngineEvent.OrderRejected(orderId, RejectReason.UNKNOWN_ORDER));
        }
        return events;
    }

    /**
     * Amend a resting order. A pure quantity decrease keeps time priority; a price
     * change or quantity increase loses it (remove + re-rest with a fresh seq).
     */
    public List<EngineEvent> amend(long orderId, long newPrice, long newQuantity) {
        List<EngineEvent> events = new ArrayList<>();
        RestingOrder existing = book.get(orderId);
        if (existing == null) {
            events.add(new EngineEvent.OrderRejected(orderId, RejectReason.UNKNOWN_ORDER));
            return events;
        }
        if (newPrice <= 0 || newQuantity <= 0) {
            events.add(new EngineEvent.OrderRejected(orderId, RejectReason.INVALID_PRICE));
            return events;
        }
        boolean keepsPriority = newPrice == existing.price && newQuantity < existing.remaining;
        if (keepsPriority) {
            book.reduce(existing, existing.remaining - newQuantity);
            events.add(new EngineEvent.OrderAmended(orderId, newPrice, newQuantity, existing.seq, false));
            return events;
        }
        long accountId = existing.accountId;
        Side side = existing.side;
        book.remove(orderId);
        long seq = ++orderSeq;
        book.rest(new RestingOrder(orderId, accountId, side, newPrice, newQuantity, seq));
        events.add(new EngineEvent.OrderAmended(orderId, newPrice, newQuantity, seq, true));
        return events;
    }

    // ---- core processing --------------------------------------------------

    private void process(Order order, List<EngineEvent> events) {
        if (book.contains(order.id()) || isParked(order.id())) {
            events.add(new EngineEvent.OrderRejected(order.id(), RejectReason.DUPLICATE_ORDER_ID));
            return;
        }
        if (order.type().isStop()) {
            long seq = ++orderSeq;
            stops.add(new ParkedStop(order, seq));
            events.add(new EngineEvent.OrderAccepted(order.id(), seq, order.quantity()));
            checkStops(events);
            return;
        }
        if (order.type() == OrderType.POST_ONLY && book.wouldCross(order.side(), order.price(), true)) {
            events.add(new EngineEvent.OrderRejected(order.id(), RejectReason.POST_ONLY_WOULD_CROSS));
            return;
        }
        if (order.type() == OrderType.FOK
                && book.fillableQuantity(order.side(), order.price(), true, order.quantity()) < order.quantity()) {
            events.add(new EngineEvent.OrderRejected(order.id(), RejectReason.FOK_NOT_FULLY_FILLABLE));
            return;
        }

        long seq = ++orderSeq;
        MatchResult result = match(order, events);
        long remaining = result.remaining();

        boolean restsType = order.type() == OrderType.LIMIT || order.type() == OrderType.POST_ONLY;
        if (restsType && !result.aborted() && remaining > 0) {
            book.rest(new RestingOrder(order.id(), order.accountId(), order.side(), order.price(), remaining, seq));
            events.add(new EngineEvent.OrderAccepted(order.id(), seq, remaining));
        } else if (remaining > 0) {
            RejectReason cause = result.aborted() ? RejectReason.SELF_TRADE_PREVENTED : null;
            events.add(new EngineEvent.OrderCanceled(order.id(), remaining, cause));
        } else {
            events.add(new EngineEvent.OrderAccepted(order.id(), seq, 0));
        }
        checkStops(events);
    }

    private record MatchResult(long remaining, boolean aborted) {}

    private MatchResult match(Order order, List<EngineEvent> events) {
        long remaining = order.quantity();
        boolean priced = order.type().isPriced();
        Side opposite = order.side().opposite();

        while (remaining > 0) {
            RestingOrder maker = book.bestResting(opposite);
            if (maker == null || (priced && !crosses(order.side(), maker.price, order.price()))) {
                break;
            }
            if (maker.accountId == order.accountId()) {
                StpOutcome outcome = applyStp(maker, remaining, events);
                if (outcome.abort()) {
                    return new MatchResult(remaining, true);
                }
                remaining = outcome.remaining();
                if (outcome.skipTrade()) {
                    continue;
                }
            }
            long qty = Math.min(remaining, maker.remaining);
            events.add(new Trade(++tradeSeq, maker.price, qty, order.id(), maker.id,
                    order.accountId(), maker.accountId, order.side()));
            book.reduce(maker, qty);
            remaining -= qty;
            lastTradePrice = maker.price;
        }
        return new MatchResult(remaining, false);
    }

    private record StpOutcome(long remaining, boolean abort, boolean skipTrade) {}

    private StpOutcome applyStp(RestingOrder maker, long remaining, List<EngineEvent> events) {
        return switch (stpPolicy) {
            case NONE -> new StpOutcome(remaining, false, false);
            case CANCEL_NEWEST -> new StpOutcome(remaining, true, false);
            case CANCEL_OLDEST -> {
                events.add(new EngineEvent.OrderCanceled(maker.id, maker.remaining, RejectReason.SELF_TRADE_PREVENTED));
                book.remove(maker.id);
                yield new StpOutcome(remaining, false, true);
            }
            case DECREMENT -> {
                long d = Math.min(remaining, maker.remaining);
                long makerLeft = maker.remaining - d;
                book.reduce(maker, d);
                events.add(new EngineEvent.OrderCanceled(maker.id, makerLeft, RejectReason.SELF_TRADE_PREVENTED));
                yield new StpOutcome(remaining - d, false, true);
            }
        };
    }

    private static boolean crosses(Side aggressorSide, long levelPrice, long limitPrice) {
        return aggressorSide == Side.BUY ? levelPrice <= limitPrice : levelPrice >= limitPrice;
    }

    // ---- stop orders ------------------------------------------------------

    private boolean isParked(long orderId) {
        return stops.stream().anyMatch(ps -> ps.order().id() == orderId);
    }

    private boolean removeParked(long orderId) {
        return stops.removeIf(ps -> ps.order().id() == orderId);
    }

    private boolean isTriggered(Order stop) {
        if (lastTradePrice <= 0) {
            return false;
        }
        return stop.side() == Side.BUY
                ? lastTradePrice >= stop.stopPrice()
                : lastTradePrice <= stop.stopPrice();
    }

    private void checkStops(List<EngineEvent> events) {
        ParkedStop fire = null;
        for (ParkedStop ps : stops) {
            if (isTriggered(ps.order())) {
                fire = ps;
                break;
            }
        }
        if (fire == null) {
            return;
        }
        stops.remove(fire);
        process(activate(fire.order()), events);
    }

    private static Order activate(Order stop) {
        if (stop.type() == OrderType.STOP) {
            return Order.market(stop.id(), stop.accountId(), stop.side(), stop.quantity());
        }
        return Order.limit(stop.id(), stop.accountId(), stop.side(), stop.price(), stop.quantity());
    }
}
