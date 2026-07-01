package com.loopers.domain.like;

import java.util.UUID;

public record ProductUnlikedEvent(UUID userId, UUID productId) {
}
