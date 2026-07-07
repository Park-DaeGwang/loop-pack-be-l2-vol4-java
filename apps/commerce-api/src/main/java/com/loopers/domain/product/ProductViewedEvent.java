package com.loopers.domain.product;

import java.util.UUID;

public record ProductViewedEvent(UUID eventId, UUID productId) {
    public ProductViewedEvent(UUID productId) {
        this(UUID.randomUUID(), productId);
    }
}
