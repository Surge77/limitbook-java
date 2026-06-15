package com.limitbook.gateway;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limitbook.gateway.sim.Simulator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayWebSocketIT {

    @LocalServerPort
    private int port;

    @Autowired
    private Simulator simulator;

    @Test
    void clientReceivesCoalescedMarketDataFrames() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                received.compareAndSet(null, message.getPayload());
                latch.countDown();
            }
        }, "ws://localhost:" + port + "/ws").get(2, TimeUnit.SECONDS);

        simulator.start(1000);
        boolean got = latch.await(3, TimeUnit.SECONDS);
        simulator.stop();
        session.close(CloseStatus.NORMAL);

        assertTrue(got, "should receive at least one market-data frame");
        assertNotNull(received.get());
        assertTrue(received.get().contains("\"type\":\"tick\""), "frame: " + received.get());
    }
}
