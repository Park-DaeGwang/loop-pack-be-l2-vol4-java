package com.loopers.domain.payment;

import java.util.UUID;

public record PaymentConfirmedEvent(UUID eventId, UUID orderId, UUID userId, Long amount) {
    public PaymentConfirmedEvent(UUID orderId, UUID userId, Long amount) {
        this(UUID.randomUUID(), orderId, userId, amount);
    }
}
