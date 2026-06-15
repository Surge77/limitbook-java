package com.limitbook.gateway;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.limitbook.engine.EngineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EngineService engine;

    @Test
    void submitValidOrderIsAcceptedAndRestsInBook() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":10000,\"quantity\":5}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.orderId").value(greaterThanOrEqualTo(1)));

        engine.awaitIdle(2000);
        mockMvc.perform(get("/api/book"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bids[0].price").value(10000));
    }

    @Test
    void missingRequiredFieldIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LIMIT\",\"price\":100,\"quantity\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.fieldErrors.side").exists());
    }

    @Test
    void domainInvalidOrderIsRejected() throws Exception {
        // LIMIT without a price violates the engine domain invariant
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.order").exists());
    }

    @Test
    void cancelIsAccepted() throws Exception {
        mockMvc.perform(delete("/api/orders/123"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    void tradesEndpointReturnsArray() throws Exception {
        mockMvc.perform(get("/api/trades?limit=10"))
                .andExpect(status().isOk());
    }
}
