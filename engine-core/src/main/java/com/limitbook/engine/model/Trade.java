package com.limitbook.engine.model;

/**
 * A single execution between an aggressing (taker) order and a resting (maker)
 * order. Price is the resting order's price (price-time priority: the maker
 * sets the price). Quantity is the matched amount in lots.
 */
public record Trade(
        long seq,
        long price,
        long quantity,
        long takerOrderId,
        long makerOrderId,
        long takerAccountId,
        long makerAccountId,
        Side aggressorSide) implements EngineEvent {

    public Trade {
        if (quantity <= 0) {
            throw new IllegalArgumentException("trade quantity must be positive: " + quantity);
        }
        if (price <= 0) {
            throw new IllegalArgumentException("trade price must be positive: " + price);
        }
    }
}
