package com.loopers.domain.like;

import java.util.UUID;

public record ProductUnlikedEvent(UUID eventId, UUID userId, UUID productId) {
    public ProductUnlikedEvent(UUID userId, UUID productId) {
        this(UUID.randomUUID(), userId, productId);
    }
}
