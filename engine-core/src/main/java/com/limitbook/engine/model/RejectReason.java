package com.limitbook.engine.model;

/** Why an order (or command) was rejected by the engine. */
public enum RejectReason {
    INVALID_PRICE,
    INVALID_QUANTITY,
    DUPLICATE_ORDER_ID,
    UNKNOWN_ORDER,
    POST_ONLY_WOULD_CROSS,
    FOK_NOT_FULLY_FILLABLE,
    MARKET_NO_LIQUIDITY,
    SELF_TRADE_PREVENTED,
    AMEND_NOT_ALLOWED
}
