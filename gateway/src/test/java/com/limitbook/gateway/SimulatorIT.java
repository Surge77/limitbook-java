package com.limitbook.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limitbook.engine.EngineService;
import com.limitbook.engine.model.BookSnapshot;
import com.limitbook.gateway.sim.Simulator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SimulatorIT {

    @Autowired
    private Simulator simulator;

    @Autowired
    private EngineService engine;

    @Test
    void simulatorGeneratesOrderFlowAndTrades() throws InterruptedException {
        simulator.start(2000);
        assertTrue(simulator.isRunning(), "status must reflect a running simulator");
        assertEquals(2000, simulator.rate());
        Thread.sleep(400);
        simulator.stop();
        engine.awaitIdle(2000);

        assertTrue(engine.ordersProcessed() > 0, "simulator should produce orders");
        assertTrue(engine.tradesEmitted() > 0, "market-making flow should cross and trade");
        assertFalse(simulator.isRunning(), "status must reflect a stopped simulator");
    }

    @Test
    void marketMakingKeepsTheBookTwoSidedAndBounded() throws InterruptedException {
        simulator.start(4000);
        Thread.sleep(600);
        simulator.stop();
        engine.awaitIdle(2000);

        BookSnapshot book = engine.latestSnapshot();
        assertFalse(book.bids().isEmpty(), "bids present");
        assertFalse(book.asks().isEmpty(), "asks present");
        long spread = book.asks().get(0).price() - book.bids().get(0).price();
        assertTrue(spread > 0 && spread < 200, "spread stays bounded, not blown out: " + spread);
    }
}
