package com.limitbook.gateway;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limitbook.engine.EngineService;
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
        Thread.sleep(400);
        simulator.stop();
        engine.awaitIdle(2000);

        assertTrue(engine.ordersProcessed() > 0, "simulator should produce orders");
        assertTrue(engine.tradesEmitted() > 0, "random walk around mid should cross and trade");
    }
}
