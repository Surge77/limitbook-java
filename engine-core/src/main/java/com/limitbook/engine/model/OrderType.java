package com.limitbook.engine.model;

/**
 * Supported order types.
 *
 * <p>IOC, FOK and POST_ONLY are limit-priced orders distinguished by their
 * time-in-force / participation semantics. MARKET and STOP (plain) carry no
 * limit price. STOP_LIMIT carries both a stop trigger and a limit price.
 */
public enum OrderType {
    LIMIT(true, false),
    MARKET(false, false),
    IOC(true, false),
    FOK(true, false),
    POST_ONLY(true, false),
    STOP(false, true),
    STOP_LIMIT(true, true);

    private final boolean priced;
    private final boolean stopTriggered;

    OrderType(boolean priced, boolean stopTriggered) {
        this.priced = priced;
        this.stopTriggered = stopTriggered;
    }

    /** True if the type requires a positive limit price. */
    public boolean isPriced() {
        return priced;
    }

    /** True if the type rests off-book until a stop price is triggered. */
    public boolean isStop() {
        return stopTriggered;
    }
}
