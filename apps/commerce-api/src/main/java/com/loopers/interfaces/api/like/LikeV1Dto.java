package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;

import java.util.UUID;

public class LikeV1Dto {

    public record LikeResponse(UUID productId) {
        public static LikeResponse from(LikeInfo info) {
            return new LikeResponse(info.productId());
        }
    }
}
