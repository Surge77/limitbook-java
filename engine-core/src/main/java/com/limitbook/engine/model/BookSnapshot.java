package com.limitbook.engine.model;

import java.util.List;

/**
 * Aggregated L2 view of the book: quantity summed per price level. Bids are
 * ordered best (highest) first; asks best (lowest) first. Safe to publish to
 * clients — contains no per-order detail.
 */
public record BookSnapshot(
        List<Level> bids,
        List<Level> asks,
        long lastTradePrice,
        long sequence) {

    public BookSnapshot {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }

    /** One aggregated price level. */
    public record Level(long price, long quantity, int orderCount) {}
}
