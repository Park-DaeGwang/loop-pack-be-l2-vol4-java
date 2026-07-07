package com.loopers.domain.queue;

import com.loopers.config.QueueProperties;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class QueueDynamicConfig {

    private static final String CONFIG_KEY = "queue:config";
    private static final String RATE_LIMITER_KEY = "order:ratelimit";

    private final RedisTemplate<String, String> redisTemplate;
    private final RedissonClient redissonClient;
    private final QueueProperties defaults;

    public QueueDynamicConfig(
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate,
        RedissonClient redissonClient,
        QueueProperties defaults
    ) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.defaults = defaults;
    }

    public long tokenTtlSeconds() {
        String val = (String) redisTemplate.opsForHash().get(CONFIG_KEY, "tokenTtlSeconds");
        return val != null ? Long.parseLong(val) : defaults.tokenTtlSeconds();
    }

    public int rateLimitPerSecond() {
        String val = (String) redisTemplate.opsForHash().get(CONFIG_KEY, "rateLimitPerSecond");
        return val != null ? Integer.parseInt(val) : defaults.rateLimitPerSecond();
    }

    public int batchSize() {
        String val = (String) redisTemplate.opsForHash().get(CONFIG_KEY, "batchSize");
        return val != null ? Integer.parseInt(val) : defaults.batchSize();
    }

    public void updateTokenTtlSeconds(long value) {
        redisTemplate.opsForHash().put(CONFIG_KEY, "tokenTtlSeconds", String.valueOf(value));
    }

    public void updateRateLimitPerSecond(int value) {
        redisTemplate.opsForHash().put(CONFIG_KEY, "rateLimitPerSecond", String.valueOf(value));
        redissonClient.getRateLimiter(RATE_LIMITER_KEY).delete();
    }

    public void updateBatchSize(int value) {
        redisTemplate.opsForHash().put(CONFIG_KEY, "batchSize", String.valueOf(value));
    }
}
