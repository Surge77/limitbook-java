package com.limitbook.gateway.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** New price and quantity for a resting order. */
public record AmendRequest(
        @NotNull @Positive Long price,
        @NotNull @Positive Long quantity) {
}
