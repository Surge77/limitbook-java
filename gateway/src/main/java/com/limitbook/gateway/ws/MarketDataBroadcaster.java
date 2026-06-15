package com.limitbook.gateway.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limitbook.engine.EngineService;
import com.limitbook.engine.model.BookSnapshot;
import com.limitbook.gateway.metrics.EngineMetrics;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Pushes a coalesced market-data frame to all connected clients at a fixed cadence
 * (see {@code fixedRate}), independent of how fast the engine matches. All socket
 * I/O runs on a single sender thread so the engine thread is never blocked and
 * {@link WebSocketSession#sendMessage} is never called concurrently for a session.
 */
@Component
public class MarketDataBroadcaster {

    private static final int TRADE_TAPE_SIZE = 50;
    private static final double NANOS_PER_SEC = 1_000_000_000.0;

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ws-sender");
        t.setDaemon(true);
        return t;
    });

    private final EngineService engine;
    private final EngineMetrics metrics;
    private final ObjectMapper mapper;

    private long lastOrders;
    private long lastTrades;
    private long lastNanos = System.nanoTime();

    public MarketDataBroadcaster(EngineService engine, EngineMetrics metrics, ObjectMapper mapper) {
        this.engine = engine;
        this.metrics = metrics;
        this.mapper = mapper;
    }

    public void register(WebSocketSession session) {
        sessions.add(session);
        sender.submit(() -> sendTo(session, frame()));
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    @Scheduled(fixedRate = 33)
    public void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        String json = frame();
        for (WebSocketSession s : sessions) {
            sender.submit(() -> sendTo(s, json));
        }
    }

    private String frame() {
        BookSnapshot snap = engine.latestSnapshot();
        long now = System.nanoTime();
        double dt = Math.max(1e-6, (now - lastNanos) / NANOS_PER_SEC);
        long orders = metrics.ordersTotal();
        long trades = metrics.tradesTotal();
        double ordersPerSec = (orders - lastOrders) / dt;
        double tradesPerSec = (trades - lastTrades) / dt;
        lastOrders = orders;
        lastTrades = trades;
        lastNanos = now;

        long bestBid = snap.bids().isEmpty() ? -1 : snap.bids().get(0).price();
        long bestAsk = snap.asks().isEmpty() ? -1 : snap.asks().get(0).price();
        long spread = (bestBid > 0 && bestAsk > 0) ? bestAsk - bestBid : -1;

        Map<String, Object> metricsMap = new LinkedHashMap<>();
        metricsMap.put("ordersPerSec", Math.round(ordersPerSec));
        metricsMap.put("tradesPerSec", Math.round(tradesPerSec));
        metricsMap.put("p50Us", round2(metrics.latencyP50Micros()));
        metricsMap.put("p99Us", round2(metrics.latencyP99Micros()));
        metricsMap.put("spread", spread);
        metricsMap.put("ordersTotal", orders);
        metricsMap.put("tradesTotal", trades);

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "tick");
        msg.put("book", snap);
        msg.put("trades", engine.recentTrades(TRADE_TAPE_SIZE));
        msg.put("metrics", metricsMap);
        try {
            return mapper.writeValueAsString(msg);
        } catch (Exception e) {
            return "{\"type\":\"tick\"}";
        }
    }

    private static void sendTo(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception ignored) {
            // a broken client must not affect others; it will be cleaned up on close
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
