package com.limitbook.gateway.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Incoming order from a client. {@code price} / {@code stopPrice} are in ticks and
 * are validated against the order type by the engine domain model. The gateway
 * assigns the actual order id, so clients never supply one.
 */
public record OrderRequest(
        @NotBlank String side,
        @NotBlank String type,
        Long price,
        @NotNull @Positive Long quantity,
        Long stopPrice,
        Long accountId) {
}
