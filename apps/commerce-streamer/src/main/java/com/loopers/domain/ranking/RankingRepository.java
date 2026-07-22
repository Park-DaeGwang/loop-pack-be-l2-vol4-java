package com.loopers.domain.ranking;

import java.time.Duration;
import java.util.UUID;

public interface RankingRepository {
    void incrementScore(String rankingKey, UUID productId, double delta, Duration ttl);
    boolean existsKey(String rankingKey);
    void setScore(String rankingKey, UUID productId, double score, Duration ttl);
}
