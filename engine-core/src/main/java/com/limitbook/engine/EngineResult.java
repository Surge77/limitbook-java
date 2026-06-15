package com.limitbook.engine;

import com.limitbook.engine.model.BookSnapshot;
import com.limitbook.engine.model.EngineEvent;
import java.util.List;

/**
 * The outcome of processing one command on the engine thread: the events it
 * produced, the wall-clock processing latency (for the latency histogram), and a
 * fresh L2 snapshot taken right after the command was applied.
 */
public record EngineResult(
        EngineCommand command,
        List<EngineEvent> events,
        long latencyNanos,
        BookSnapshot snapshot) {

    public EngineResult {
        events = List.copyOf(events);
    }
}
