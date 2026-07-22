package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class RankingRepositoryImpl implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRepositoryImpl(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<UUID> findTopRanked(String rankingKey, int offset, int limit) {
        Set<String> members = redisTemplate.opsForZSet()
            .reverseRange(rankingKey, offset, offset + limit - 1);
        if (members == null) return List.of();
        return members.stream()
            .map(UUID::fromString)
            .toList();
    }

    @Override
    public long countRanked(String rankingKey) {
        Long count = redisTemplate.opsForZSet().size(rankingKey);
        return count == null ? 0L : count;
    }

    @Override
    public Long findRank(String rankingKey, UUID productId) {
        return redisTemplate.opsForZSet().reverseRank(rankingKey, productId.toString());
    }

    @Override
    public Double findScore(String rankingKey, UUID productId) {
        return redisTemplate.opsForZSet().score(rankingKey, productId.toString());
    }

    @Override
    public void carryOver(String fromKey, String toKey, double weight, Duration ttl) {
        redisTemplate.opsForZSet().unionAndStore(
            fromKey,
            Collections.emptyList(),
            toKey,
            Aggregate.SUM,
            Weights.of(weight)
        );
        redisTemplate.expire(toKey, ttl);
    }
}
