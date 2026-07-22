package com.loopers.domain.ranking;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface RankingRepository {
    List<UUID> findTopRanked(String rankingKey, int offset, int limit);
    long countRanked(String rankingKey);
    Long findRank(String rankingKey, UUID productId);
    Double findScore(String rankingKey, UUID productId);
    void carryOver(String fromKey, String toKey, double weight, Duration ttl);
}
