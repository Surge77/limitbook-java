package com.limitbook.gateway.web.dto;

/** Acknowledgement that a command was accepted onto the engine queue. */
public record CommandAck(boolean accepted, long orderId) {
}
