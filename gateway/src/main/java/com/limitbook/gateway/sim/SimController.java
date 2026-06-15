package com.limitbook.gateway.sim;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controls the synthetic order-flow simulator. */
@RestController
@RequestMapping("/api/sim")
public class SimController {

    private static final int DEFAULT_RATE = 2_000;

    private final Simulator simulator;

    public SimController(Simulator simulator) {
        this.simulator = simulator;
    }

    @GetMapping
    public Map<String, Object> status() {
        return Map.of("running", simulator.isRunning(), "rate", simulator.rate());
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestParam(defaultValue = "" + DEFAULT_RATE) int rate) {
        int applied = simulator.start(rate);
        return Map.of("running", true, "rate", applied);
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        simulator.stop();
        return Map.of("running", false, "rate", simulator.rate());
    }
}
