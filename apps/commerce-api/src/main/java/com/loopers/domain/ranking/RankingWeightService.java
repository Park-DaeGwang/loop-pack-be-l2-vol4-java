package com.loopers.domain.ranking;

import com.loopers.config.CacheConfig;
import com.loopers.infrastructure.ranking.RankingWeightJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class RankingWeightService {

    private final RankingWeightJpaRepository rankingWeightJpaRepository;

    @CacheEvict(cacheNames = CacheConfig.RANKING_WEIGHT_CACHE, key = "#eventType")
    @Transactional
    public void updateWeight(String eventType, double weight) {
        RankingWeightEntity entity = rankingWeightJpaRepository.findById(eventType)
            .orElse(new RankingWeightEntity(eventType, weight));
        entity.updateWeight(weight);
        rankingWeightJpaRepository.save(entity);
    }
}
