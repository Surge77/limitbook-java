package com.limitbook.engine;

import com.limitbook.engine.model.EngineEvent;
import com.limitbook.engine.model.RejectReason;
import com.limitbook.engine.model.Trade;
import java.util.List;
import java.util.Optional;

/** Small test helpers for filtering the event stream returned by the engine. */
final class Matchers {

    private Matchers() {}

    static List<Trade> trades(List<EngineEvent> events) {
        return events.stream().filter(Trade.class::isInstance).map(Trade.class::cast).toList();
    }

    static Optional<EngineEvent.OrderAccepted> accepted(List<EngineEvent> events) {
        return events.stream()
                .filter(EngineEvent.OrderAccepted.class::isInstance)
                .map(EngineEvent.OrderAccepted.class::cast)
                .findFirst();
    }

    static Optional<EngineEvent.OrderRejected> rejected(List<EngineEvent> events) {
        return events.stream()
                .filter(EngineEvent.OrderRejected.class::isInstance)
                .map(EngineEvent.OrderRejected.class::cast)
                .findFirst();
    }

    static Optional<EngineEvent.OrderCanceled> canceled(List<EngineEvent> events) {
        return events.stream()
                .filter(EngineEvent.OrderCanceled.class::isInstance)
                .map(EngineEvent.OrderCanceled.class::cast)
                .findFirst();
    }

    static boolean hasReject(List<EngineEvent> events, RejectReason reason) {
        return rejected(events).filter(r -> r.reason() == reason).isPresent();
    }
}
