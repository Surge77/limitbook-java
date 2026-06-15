package com.limitbook.engine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EventModelTest {

    @Test
    void tradeRejectsNonPositiveFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new Trade(1, 0, 5, 1, 2, 10, 20, Side.BUY));
        assertThrows(IllegalArgumentException.class,
                () -> new Trade(1, 100, 0, 1, 2, 10, 20, Side.BUY));
    }

    @Test
    void canceledConvenienceCtorLeavesCauseNull() {
        EngineEvent.OrderCanceled c = new EngineEvent.OrderCanceled(7, 3);
        assertNull(c.cause());
        assertEquals(3, c.remainingQuantity());
    }

    @Test
    void engineEventsAreExhaustivelySwitchable() {
        EngineEvent e = new EngineEvent.OrderAccepted(1, 1, 5);
        String label = switch (e) {
            case EngineEvent.OrderAccepted a -> "accepted:" + a.restingQuantity();
            case EngineEvent.OrderRejected r -> "rejected:" + r.reason();
            case Trade t -> "trade:" + t.quantity();
            case EngineEvent.OrderCanceled x -> "canceled";
            case EngineEvent.OrderAmended x -> "amended";
            case EngineEvent.BookUpdated x -> "book";
        };
        assertEquals("accepted:5", label);
    }

    @Test
    void bookSnapshotIsDefensivelyCopied() {
        var bids = new java.util.ArrayList<>(List.of(new BookSnapshot.Level(100, 5, 1)));
        BookSnapshot snap = new BookSnapshot(bids, List.of(), 0, 1);
        bids.clear();
        assertEquals(1, snap.bids().size());
        assertTrue(snap.asks().isEmpty());
    }
}
