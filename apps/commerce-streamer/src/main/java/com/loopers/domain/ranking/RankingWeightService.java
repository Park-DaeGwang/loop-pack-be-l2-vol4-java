package com.loopers.domain.ranking;

import com.loopers.config.RankingWeightCacheConfig;
import com.loopers.infrastructure.ranking.RankingWeightJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class RankingWeightService {

    private final RankingWeightJpaRepository rankingWeightJpaRepository;

    @Cacheable(cacheNames = RankingWeightCacheConfig.RANKING_WEIGHT_CACHE, key = "#eventType")
    public double getWeight(String eventType, double defaultWeight) {
        return rankingWeightJpaRepository.findById(eventType)
            .map(RankingWeightEntity::getWeight)
            .orElse(defaultWeight);
    }
}
