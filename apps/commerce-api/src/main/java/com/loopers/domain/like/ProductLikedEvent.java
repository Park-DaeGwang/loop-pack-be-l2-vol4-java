package com.loopers.domain.like;

import java.util.UUID;

public record ProductLikedEvent(UUID eventId, UUID userId, UUID productId) {
    public ProductLikedEvent(UUID userId, UUID productId) {
        this(UUID.randomUUID(), userId, productId);
    }
}
