# DESIGN

Why the engine is built the way it is. Correctness and tail latency are the
goals; everything below serves one of those two.

## 1. Strict layering

```
frontend (React)  →  gateway (Spring Boot)  →  engine-core (pure Java)
   reads only          wraps + validates          matcher + book
```

`engine-core` has **zero** framework / network / I/O dependencies (only JUnit,
jqwik and JMH at test scope). It is a reusable library: the gateway is just one
possible host. Nothing from Spring or the UI can leak into the matching path,
which keeps the hot loop allocation-light and trivially testable.

## 2. Integer-tick pricing (no floats)

Prices and quantities are `long`. One tick = $0.01 (display-only conversion lives
in the UI). Floating point is banned in the core: `0.1 + 0.2 != 0.3` under
IEEE-754 would silently corrupt price priority and quantity conservation.
Integers are exact, branch-predictably comparable, and naturally sortable as
`TreeMap` keys.

## 3. Order book data structure

Each side is a `TreeMap<Long, PriceLevel>`:

- **Bids** use a reverse comparator → `firstKey()` is the highest (best) bid.
- **Asks** use natural order → `firstKey()` is the lowest (best) ask.
- Best-price lookup is O(1) (`firstEntry`); inserting/removing a level is O(log n).

Each `PriceLevel` is a FIFO `LinkedHashMap<orderId, RestingOrder>`:

- Insertion order **is** time priority — no separate timestamp needed.
- Enqueue O(1), dequeue-oldest O(1), and **remove-by-id O(1)** (what an
  `ArrayDeque` cannot do without an O(n) scan — and cancel/amend need it).

A flat `HashMap<orderId, RestingOrder>` indexes every resting order so
cancel/amend are O(1) average without walking levels.

## 4. Time priority via sequence, not clock

Each accepted order is stamped with a monotonic `orderSeq`; trades carry a
monotonic `tradeSeq`. Wall-clock time is non-monotonic and machine-dependent —
useless for deterministic replay. The sequence guarantees identical output for
identical input, every run (verified by a jqwik property and a cross-instance
determinism test).

## 5. Single-writer (LMAX-style) execution

One thread owns the book. Producers (HTTP handlers, the simulator) hand
`EngineCommand`s over a bounded `ArrayBlockingQueue`. Because only one thread
ever mutates the book, the matcher needs **no locks** on its hot path.
Backpressure is explicit: a full queue makes `offer` return false and the gateway
replies `503`.

The engine thread publishes read-only state for other threads:

- the latest L2 snapshot via an `AtomicReference` (single writer, many readers);
- recent trades in a bounded ring (the trade tape).

So REST `GET /book` / `GET /trades` and the WebSocket broadcaster read without
ever touching the matcher.

## 6. Order types & matching rules

Price priority (best price first) then FIFO time priority within a level.
Supported: LIMIT, MARKET, IOC, FOK, POST_ONLY, STOP, STOP_LIMIT, plus CANCEL and
amend. The maker sets the trade price (aggressors get price improvement).

- **IOC** fills what it can, cancels the rest (never rests).
- **FOK** is checked against available liquidity first; all-or-nothing.
- **POST_ONLY** is rejected if it would cross (maker-only).
- **STOP / STOP_LIMIT** park off-book and trigger when the last trade price
  crosses the stop, then activate as MARKET / LIMIT respectively. Triggering
  cascades deterministically.

## 7. Self-trade prevention

Configurable per engine: `CANCEL_NEWEST` (default), `CANCEL_OLDEST`, `DECREMENT`,
or `NONE`. Applied when an aggressor would match a resting order of the same
account.

## 8. Invariants (proved by tests)

- The book never crosses (best bid < best ask after matching).
- Quantity is conserved across every trade; no negative quantities.
- FIFO honored within a level; amend keeps priority only on a pure qty decrease.
- Deterministic replay.

Matcher line coverage is **100%**; engine-core overall ~99%. See
`engine-core/BENCHMARKS.md` for measured throughput and latency.
