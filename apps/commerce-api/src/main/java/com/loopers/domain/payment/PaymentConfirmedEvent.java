package com.loopers.domain.payment;

import java.util.UUID;

public record PaymentConfirmedEvent(UUID orderId, UUID userId, Long amount) {
}
