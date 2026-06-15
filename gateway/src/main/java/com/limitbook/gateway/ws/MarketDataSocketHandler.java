package com.limitbook.gateway.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Registers/unregisters sessions with the broadcaster. The feed is read-only. */
@Component
public class MarketDataSocketHandler extends TextWebSocketHandler {

    private final MarketDataBroadcaster broadcaster;

    public MarketDataSocketHandler(MarketDataBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        broadcaster.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.unregister(session);
    }
}
