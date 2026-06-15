package com.limitbook.engine.model;

/**
 * Self-trade prevention policy: what to do when an incoming order would match a
 * resting order owned by the same account.
 */
public enum StpPolicy {
    /** No prevention; an account may trade with itself. */
    NONE,
    /** Cancel the incoming (aggressing) order's remaining quantity. */
    CANCEL_NEWEST,
    /** Cancel the resting (older) order and continue matching the aggressor. */
    CANCEL_OLDEST,
    /** Cancel the smaller of the two quantities from both orders, no trade. */
    DECREMENT
}
