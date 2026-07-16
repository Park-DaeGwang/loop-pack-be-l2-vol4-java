package com.loopers.domain.ranking;

import java.time.Duration;
import java.util.UUID;

public interface RankingRepository {
    void incrementScore(String rankingKey, UUID productId, double delta, Duration ttl);
}
