package com.loopers.domain.like;

import java.util.UUID;

public record ProductLikedEvent(UUID userId, UUID productId) {
}
