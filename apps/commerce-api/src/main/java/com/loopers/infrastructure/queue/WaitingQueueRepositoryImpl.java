package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class WaitingQueueRepositoryImpl implements WaitingQueueRepository {

    private static final String WAITING_QUEUE_KEY = "queue:waiting";
    private static final String TOKEN_KEY_PREFIX = "queue:token:";

    private final RedisTemplate<String, String> redisTemplate;

    public WaitingQueueRepositoryImpl(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean enter(UUID userId, long score) {
        Boolean added = redisTemplate.opsForZSet()
            .addIfAbsent(WAITING_QUEUE_KEY, userId.toString(), score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Optional<Long> findRank(UUID userId) {
        Long rank = redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, userId.toString());
        return Optional.ofNullable(rank);
    }

    @Override
    public long size() {
        Long count = redisTemplate.opsForZSet().size(WAITING_QUEUE_KEY);
        return count == null ? 0L : count;
    }

    @Override
    public List<UUID> popBatch(int count) {
        Set<ZSetOperations.TypedTuple<String>> popped =
            redisTemplate.opsForZSet().popMin(WAITING_QUEUE_KEY, count);
        if (popped == null || popped.isEmpty()) {
            return Collections.emptyList();
        }
        return popped.stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .map(UUID::fromString)
            .toList();
    }

    @Override
    public void saveToken(UUID userId, String token, Duration ttl) {
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + userId, token, ttl);
    }

    @Override
    public Optional<String> findToken(UUID userId) {
        String token = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId);
        return Optional.ofNullable(token);
    }

    @Override
    public void removeToken(UUID userId) {
        redisTemplate.delete(TOKEN_KEY_PREFIX + userId);
    }
}
