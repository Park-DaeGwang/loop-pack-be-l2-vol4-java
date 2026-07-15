package com.loopers.domain.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
class RankingServiceIntegrationTest {

    @Autowired
    private RankingService rankingService;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    private static final String DATE = "20260715";
    private static final String KEY = "ranking:all:20260715";

    @AfterEach
    void tearDown() {
        Set<String> keys = redisTemplate.keys("ranking:all:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void Top_N_조회_시_score_높은_순으로_반환된다() {
        UUID productA = UUID.randomUUID();
        UUID productB = UUID.randomUUID();
        UUID productC = UUID.randomUUID();
        redisTemplate.opsForZSet().add(KEY, productA.toString(), 1.0);
        redisTemplate.opsForZSet().add(KEY, productB.toString(), 3.0);
        redisTemplate.opsForZSet().add(KEY, productC.toString(), 2.0);

        List<UUID> result = rankingService.getTopRanked(DATE, 1, 10);

        assertThat(result).containsExactly(productB, productC, productA);
    }

    @Test
    void 개별_상품_순위가_1_based로_반환된다() {
        UUID productA = UUID.randomUUID();
        UUID productB = UUID.randomUUID();
        redisTemplate.opsForZSet().add(KEY, productA.toString(), 2.0);
        redisTemplate.opsForZSet().add(KEY, productB.toString(), 1.0);

        Long rank = rankingService.getRank(DATE, productA);

        assertThat(rank).isEqualTo(1L);
    }

    @Test
    void 랭킹에_없는_상품_순위는_null이다() {
        UUID productId = UUID.randomUUID();

        Long rank = rankingService.getRank(DATE, productId);

        assertThat(rank).isNull();
    }

    @Test
    void 전체_랭킹_수를_반환한다() {
        redisTemplate.opsForZSet().add(KEY, UUID.randomUUID().toString(), 1.0);
        redisTemplate.opsForZSet().add(KEY, UUID.randomUUID().toString(), 2.0);

        long count = rankingService.countRanked(DATE);

        assertThat(count).isEqualTo(2L);
    }
}
