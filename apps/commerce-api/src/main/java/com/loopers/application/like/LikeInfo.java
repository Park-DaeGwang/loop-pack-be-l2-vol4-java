package com.loopers.application.like;

import java.util.UUID;

public record LikeInfo(UUID productId) {
    public static LikeInfo of(UUID productId) {
        return new LikeInfo(productId);
    }
}
