package com.limitbook.engine;

/**
 * Callback invoked on the engine thread after each command is processed. Keep
 * implementations cheap and non-blocking — work done here runs inside the
 * single-writer loop and delays the next command. Hand heavy work (network I/O,
 * serialization) off to another thread.
 */
@FunctionalInterface
public interface EngineListener {
    void onResult(EngineResult result);
}
