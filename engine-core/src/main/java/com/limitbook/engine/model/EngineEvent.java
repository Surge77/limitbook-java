package com.limitbook.engine.model;

/**
 * Everything the engine emits in response to a command. Sealed so consumers can
 * switch exhaustively over the full set of outcomes.
 */
public sealed interface EngineEvent
        permits EngineEvent.OrderAccepted,
                EngineEvent.OrderRejected,
                Trade,
                EngineEvent.OrderCanceled,
                EngineEvent.OrderAmended,
                EngineEvent.BookUpdated {

    /** Order passed validation and entered the book (fully or partially resting). */
    record OrderAccepted(long orderId, long seq, long restingQuantity) implements EngineEvent {}

    /** Order was rejected; no book state changed. */
    record OrderRejected(long orderId, RejectReason reason) implements EngineEvent {}

    /** A resting order was removed by an explicit cancel or by a TIF rule. */
    record OrderCanceled(long orderId, long remainingQuantity, RejectReason cause) implements EngineEvent {
        public OrderCanceled(long orderId, long remainingQuantity) {
            this(orderId, remainingQuantity, null);
        }
    }

    /** A resting order was amended. {@code lostPriority} true if it went to the back of its new level. */
    record OrderAmended(long orderId, long newPrice, long newQuantity, long newSeq, boolean lostPriority)
            implements EngineEvent {}

    /** Aggregated L2 view changed; carries a fresh snapshot. */
    record BookUpdated(BookSnapshot snapshot) implements EngineEvent {}
}
