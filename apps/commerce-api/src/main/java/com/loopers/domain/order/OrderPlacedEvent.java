package com.loopers.domain.order;

import java.util.UUID;

public record OrderPlacedEvent(UUID orderId, UUID userId) {
}
