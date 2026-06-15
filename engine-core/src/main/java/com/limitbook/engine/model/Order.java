package com.limitbook.engine.model;

/**
 * An order as submitted to the engine (a command), independent of book state.
 *
 * <p>{@code price} and {@code stopPrice} are expressed in integer ticks (see
 * DESIGN.md; default 1 tick = $0.01). Non-priced types (MARKET, STOP) must use
 * {@link #NO_PRICE}. Non-stop types must use {@link #NO_PRICE} for stopPrice.
 *
 * <p>Time priority is assigned by the engine on acceptance (a monotonic
 * sequence), not carried on the submitted order — wall-clock time is neither
 * monotonic nor deterministic for replay.
 */
public record Order(
        long id,
        long accountId,
        Side side,
        OrderType type,
        long price,
        long quantity,
        long stopPrice) {

    public static final long NO_PRICE = 0L;

    public Order {
        if (id <= 0) {
            throw new IllegalArgumentException("order id must be positive: " + id);
        }
        if (side == null || type == null) {
            throw new IllegalArgumentException("side and type are required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        if (type.isPriced()) {
            if (price <= 0) {
                throw new IllegalArgumentException(type + " requires a positive price: " + price);
            }
        } else if (price != NO_PRICE) {
            throw new IllegalArgumentException(type + " must not carry a price: " + price);
        }
        if (type.isStop()) {
            if (stopPrice <= 0) {
                throw new IllegalArgumentException(type + " requires a positive stop price: " + stopPrice);
            }
        } else if (stopPrice != NO_PRICE) {
            throw new IllegalArgumentException(type + " must not carry a stop price: " + stopPrice);
        }
    }

    public static Order limit(long id, long accountId, Side side, long price, long quantity) {
        return new Order(id, accountId, side, OrderType.LIMIT, price, quantity, NO_PRICE);
    }

    public static Order market(long id, long accountId, Side side, long quantity) {
        return new Order(id, accountId, side, OrderType.MARKET, NO_PRICE, quantity, NO_PRICE);
    }

    public static Order ioc(long id, long accountId, Side side, long price, long quantity) {
        return new Order(id, accountId, side, OrderType.IOC, price, quantity, NO_PRICE);
    }

    public static Order fok(long id, long accountId, Side side, long price, long quantity) {
        return new Order(id, accountId, side, OrderType.FOK, price, quantity, NO_PRICE);
    }

    public static Order postOnly(long id, long accountId, Side side, long price, long quantity) {
        return new Order(id, accountId, side, OrderType.POST_ONLY, price, quantity, NO_PRICE);
    }

    public static Order stop(long id, long accountId, Side side, long stopPrice, long quantity) {
        return new Order(id, accountId, side, OrderType.STOP, NO_PRICE, quantity, stopPrice);
    }

    public static Order stopLimit(long id, long accountId, Side side, long stopPrice, long price, long quantity) {
        return new Order(id, accountId, side, OrderType.STOP_LIMIT, price, quantity, stopPrice);
    }
}
