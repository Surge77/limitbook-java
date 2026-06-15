package com.limitbook.engine.book;

import java.util.LinkedHashMap;

/**
 * One price level: a FIFO queue of resting orders at the same price. Backed by a
 * {@link LinkedHashMap} keyed by order id — insertion order gives time priority,
 * and removal by id (cancel/amend) is O(1). {@code totalQuantity} is maintained
 * incrementally so L2 snapshots and FOK checks never re-sum the queue.
 */
final class PriceLevel {

    final long price;
    private final LinkedHashMap<Long, RestingOrder> orders = new LinkedHashMap<>();
    private long totalQuantity;

    PriceLevel(long price) {
        this.price = price;
    }

    void add(RestingOrder order) {
        orders.put(order.id, order);
        totalQuantity += order.remaining;
    }

    /** Oldest resting order at this level (highest time priority). */
    RestingOrder first() {
        return orders.values().iterator().next();
    }

    void remove(RestingOrder order) {
        if (orders.remove(order.id) != null) {
            totalQuantity -= order.remaining;
        }
    }

    /** Reduce a resting order by {@code delta}, keeping the level total in sync. */
    void reduce(RestingOrder order, long delta) {
        order.remaining -= delta;
        totalQuantity -= delta;
        if (order.remaining == 0) {
            orders.remove(order.id);
        }
    }

    boolean isEmpty() {
        return orders.isEmpty();
    }

    int orderCount() {
        return orders.size();
    }

    long totalQuantity() {
        return totalQuantity;
    }
}
