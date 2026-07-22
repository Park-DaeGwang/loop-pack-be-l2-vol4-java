package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;

import java.util.UUID;

public class RankingV1Dto {

    public record RankingResponse(
        long rank,
        Double score,
        UUID productId,
        String productName,
        String brandName,
        Long price
    ) {
        public static RankingResponse from(RankingInfo info) {
            return new RankingResponse(
                info.rank(), info.score(), info.productId(),
                info.productName(), info.brandName(), info.price()
            );
        }
    }
}
