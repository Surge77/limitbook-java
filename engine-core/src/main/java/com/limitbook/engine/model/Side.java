package com.limitbook.engine.model;

/** Order side. BUY bids (wants to buy), SELL asks (wants to sell). */
public enum Side {
    BUY,
    SELL;

    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }
}
