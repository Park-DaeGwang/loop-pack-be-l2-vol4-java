package com.loopers.domain.payment;

import java.util.List;
import java.util.UUID;

public record PaymentConfirmedEvent(UUID eventId, UUID orderId, UUID userId, Long amount, List<OrderItemSummary> items) {

    public PaymentConfirmedEvent(UUID orderId, UUID userId, Long amount, List<OrderItemSummary> items) {
        this(UUID.randomUUID(), orderId, userId, amount, items);
    }

    public record OrderItemSummary(UUID productId, int quantity) {
    }
}
