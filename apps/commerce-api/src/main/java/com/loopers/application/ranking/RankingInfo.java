package com.loopers.application.ranking;

import java.util.UUID;

public record RankingInfo(
    long rank,
    Double score,
    UUID productId,
    String productName,
    String brandName,
    Long price
) {}
