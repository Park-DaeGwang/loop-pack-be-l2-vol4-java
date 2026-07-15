package com.loopers.domain.ranking;

import java.util.UUID;

public interface RankingRepository {
    void incrementScore(String rankingKey, UUID productId, double delta);
}
