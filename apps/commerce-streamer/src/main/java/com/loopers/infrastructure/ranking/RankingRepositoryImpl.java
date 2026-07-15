package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.UUID;

@Repository
public class RankingRepositoryImpl implements RankingRepository {

    private static final Duration TTL = Duration.ofDays(2);

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRepositoryImpl(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrementScore(String rankingKey, UUID productId, double delta) {
        redisTemplate.opsForZSet().incrementScore(rankingKey, productId.toString(), delta);
        Long ttl = redisTemplate.getExpire(rankingKey);
        if (ttl == null || ttl < 0) {
            redisTemplate.expire(rankingKey, TTL);
        }
    }
}
