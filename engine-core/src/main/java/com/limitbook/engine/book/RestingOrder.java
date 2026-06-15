package com.limitbook.engine.book;

import com.limitbook.engine.model.Side;

/**
 * A live order resting in the book. Mutable only in {@code remaining}; identity,
 * price and time-priority sequence are fixed for the order's lifetime. An amend
 * that changes price or increases quantity is modeled as remove + re-rest with a
 * fresh sequence, so this object never moves between price levels.
 */
public final class RestingOrder {

    public final long id;
    public final long accountId;
    public final Side side;
    public final long price;
    public final long seq;
    public long remaining;

    public RestingOrder(long id, long accountId, Side side, long price, long quantity, long seq) {
        this.id = id;
        this.accountId = accountId;
        this.side = side;
        this.price = price;
        this.remaining = quantity;
        this.seq = seq;
    }
}
