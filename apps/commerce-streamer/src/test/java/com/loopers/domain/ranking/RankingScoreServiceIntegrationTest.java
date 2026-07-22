package com.loopers.domain.ranking;

import com.loopers.config.redis.RedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class RankingScoreServiceIntegrationTest {

    @Autowired
    private RankingScoreService rankingScoreService;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void tearDown() {
        Set<String> keys = redisTemplate.keys("ranking:all:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void view_이벤트_ZSET에_점수가_반영된다() {
        UUID productId = UUID.randomUUID();
        ZonedDateTime eventTime = ZonedDateTime.parse("2026-07-15T10:00:00+09:00");

        rankingScoreService.applyView(productId, eventTime);

        Double score = redisTemplate.opsForZSet().score("ranking:all:20260715", productId.toString());
        assertThat(score).isCloseTo(0.1, within(0.001));
    }

    @Test
    void 동일_상품_여러_이벤트_누적된다() {
        UUID productId = UUID.randomUUID();
        ZonedDateTime eventTime = ZonedDateTime.parse("2026-07-15T10:00:00+09:00");

        rankingScoreService.applyView(productId, eventTime);
        rankingScoreService.applyLike(productId, eventTime);
        rankingScoreService.applyOrder(productId, 2, eventTime);

        Double score = redisTemplate.opsForZSet().score("ranking:all:20260715", productId.toString());
        assertThat(score).isCloseTo(0.1 + 0.2 + 1.4, within(0.001));
    }

    @Test
    void ZSET_키에_TTL이_설정된다() {
        UUID productId = UUID.randomUUID();
        ZonedDateTime eventTime = ZonedDateTime.parse("2026-07-15T10:00:00+09:00");

        rankingScoreService.applyView(productId, eventTime);

        Long ttl = redisTemplate.getExpire("ranking:all:20260715");
        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(Duration.ofDays(2).toSeconds());
    }

    @Test
    void 날짜가_다른_이벤트는_다른_키에_저장된다() {
        UUID productId = UUID.randomUUID();

        rankingScoreService.applyView(productId, ZonedDateTime.parse("2026-07-15T10:00:00+09:00"));
        rankingScoreService.applyView(productId, ZonedDateTime.parse("2026-07-14T10:00:00+09:00"));

        Double score15 = redisTemplate.opsForZSet().score("ranking:all:20260715", productId.toString());
        Double score14 = redisTemplate.opsForZSet().score("ranking:all:20260714", productId.toString());
        assertThat(score15).isCloseTo(0.1, within(0.001));
        assertThat(score14).isCloseTo(0.1, within(0.001));
    }
}
