package com.limitbook.engine.book;

import com.limitbook.engine.model.BookSnapshot;
import com.limitbook.engine.model.Side;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Two-sided limit order book.
 *
 * <p>Data-structure choice (justification):
 * <ul>
 *   <li>Each side is a {@link TreeMap}&lt;price, {@link PriceLevel}&gt;. Bids use a
 *       reverse comparator so {@code firstKey()} is the highest (best) bid; asks
 *       use natural order so {@code firstKey()} is the lowest (best) ask. Best
 *       price is O(1) ({@code firstEntry}); insert/remove of a level is O(log n).</li>
 *   <li>Within a level, orders are a FIFO {@link LinkedHashMap} (see
 *       {@link PriceLevel}): O(1) enqueue, O(1) dequeue of the oldest, O(1)
 *       removal by id.</li>
 *   <li>A flat {@code index} map (id → {@link RestingOrder}) makes cancel/amend
 *       O(1) average without scanning levels.</li>
 * </ul>
 * This is pure state — it performs no matching. The matcher drives it.
 */
public final class OrderBook {

    private final TreeMap<Long, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, PriceLevel> asks = new TreeMap<>();
    private final Map<Long, RestingOrder> index = new HashMap<>();

    private TreeMap<Long, PriceLevel> sideMap(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    public boolean contains(long orderId) {
        return index.containsKey(orderId);
    }

    public RestingOrder get(long orderId) {
        return index.get(orderId);
    }

    public void rest(RestingOrder order) {
        sideMap(order.side).computeIfAbsent(order.price, PriceLevel::new).add(order);
        index.put(order.id, order);
    }

    private PriceLevel bestLevel(Side side) {
        Map.Entry<Long, PriceLevel> e = sideMap(side).firstEntry();
        return e == null ? null : e.getValue();
    }

    private Collection<PriceLevel> levels(Side side) {
        return sideMap(side).values();
    }

    /** Oldest order at the best level of {@code side}, or {@code null} if that side is empty. */
    public RestingOrder bestResting(Side side) {
        PriceLevel level = bestLevel(side);
        return level == null ? null : level.first();
    }

    /** True if a maker at {@code levelPrice} is matchable by an aggressor on {@code aggressorSide} priced at {@code limitPrice}. */
    private static boolean crosses(Side aggressorSide, long levelPrice, long limitPrice) {
        return aggressorSide == Side.BUY ? levelPrice <= limitPrice : levelPrice >= limitPrice;
    }

    /** True if an incoming order would immediately match resting liquidity (used by post-only). */
    public boolean wouldCross(Side aggressorSide, long limitPrice, boolean priced) {
        PriceLevel best = bestLevel(aggressorSide.opposite());
        return best != null && (!priced || crosses(aggressorSide, best.price, limitPrice));
    }

    /** Total quantity matchable by an aggressor right now, capped at {@code need} (used by FOK). */
    public long fillableQuantity(Side aggressorSide, long limitPrice, boolean priced, long need) {
        long acc = 0;
        for (PriceLevel level : levels(aggressorSide.opposite())) {
            if (priced && !crosses(aggressorSide, level.price, limitPrice)) {
                break;
            }
            acc += level.totalQuantity();
            if (acc >= need) {
                return acc;
            }
        }
        return acc;
    }

    public long bestBidPrice() {
        return bids.isEmpty() ? -1 : bids.firstKey();
    }

    public long bestAskPrice() {
        return asks.isEmpty() ? -1 : asks.firstKey();
    }

    /** Reduce a resting maker order by {@code delta}; drops the level if it empties. */
    public void reduce(RestingOrder maker, long delta) {
        PriceLevel level = sideMap(maker.side).get(maker.price);
        level.reduce(maker, delta);
        if (maker.remaining == 0) {
            index.remove(maker.id);
        }
        if (level.isEmpty()) {
            sideMap(maker.side).remove(maker.price);
        }
    }

    /** Remove a resting order entirely (cancel). Returns it, or {@code null} if absent. */
    public RestingOrder remove(long orderId) {
        RestingOrder order = index.remove(orderId);
        if (order == null) {
            return null;
        }
        PriceLevel level = sideMap(order.side).get(order.price);
        level.remove(order);
        if (level.isEmpty()) {
            sideMap(order.side).remove(order.price);
        }
        return order;
    }

    public BookSnapshot snapshot(int depth, long lastTradePrice, long sequence) {
        return new BookSnapshot(
                levelsToList(bids, depth),
                levelsToList(asks, depth),
                lastTradePrice,
                sequence);
    }

    private static List<BookSnapshot.Level> levelsToList(TreeMap<Long, PriceLevel> side, int depth) {
        List<BookSnapshot.Level> out = new ArrayList<>(Math.min(depth, side.size()));
        for (PriceLevel level : side.values()) {
            if (out.size() >= depth) {
                break;
            }
            out.add(new BookSnapshot.Level(level.price, level.totalQuantity(), level.orderCount()));
        }
        return out;
    }
}
