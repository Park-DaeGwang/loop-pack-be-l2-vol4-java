package com.loopers.domain.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingScoreServiceTest {

    private RankingRepository rankingRepository;
    private RankingWeightService rankingWeightService;
    private RankingScoreService rankingScoreService;

    @BeforeEach
    void setUp() {
        rankingRepository = mock(RankingRepository.class);
        rankingWeightService = mock(RankingWeightService.class);
        when(rankingWeightService.getWeight(anyString(), anyDouble()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        rankingScoreService = new RankingScoreService(rankingRepository, rankingWeightService);
    }

    @Test
    void view_이벤트_점수가_0_1_증가한다() {
        UUID productId = UUID.randomUUID();
        ZonedDateTime eventTime = ZonedDateTime.parse("2026-07-15T10:00:00+09:00");

        rankingScoreService.applyView(productId, eventTime);

        verify(rankingRepository).incrementScore("ranking:all:20260715", productId, 0.1, Duration.ofDays(2));
        verify(rankingRepository).incrementScore("ranking:hourly:2026071510", productId, 0.1, Duration.ofHours(4));
    }

    @Test
    void like_이벤트_점수가_0_2_증가한다() {
        UUID productId = UUID.randomUUID();
        ZonedDateTime eventTime = ZonedDateTime.parse("2026-07-15T10:00:00+09:00");

        rankingScoreService.applyLike(productId, eventTime);

        verify(rankingRepository).incrementScore("ranking:all:20260715", productId, 0.2, Duration.ofDays(2));
        verify(rankingRepository).incrementScore("ranking:hourly:2026071510", productId, 0.2, Duration.ofHours(4));
    }

    @Test
    void unlike_이벤트_점수가_0_2_감소한다() {
        UUID productId = UUID.randomUUID();
        ZonedDateTime eventTime = ZonedDateTime.parse("2026-07-15T10:00:00+09:00");

        rankingScoreService.applyUnlike(productId, eventTime);

        verify(rankingRepository).incrementScore("ranking:all:20260715", productId, -0.2, Duration.ofDays(2));
        verify(rankingRepository).incrementScore("ranking:hourly:2026071510", productId, -0.2, Duration.ofHours(4));
    }

    @Test
    void order_이벤트_점수가_0_7_곱하기_수량만큼_증가한다() {
        UUID productId = UUID.randomUUID();
        ZonedDateTime eventTime = ZonedDateTime.parse("2026-07-15T10:00:00+09:00");
        ArgumentCaptor<Double> deltaCaptor = ArgumentCaptor.forClass(Double.class);

        rankingScoreService.applyOrder(productId, 3, eventTime);

        verify(rankingRepository).incrementScore(
            org.mockito.ArgumentMatchers.eq("ranking:all:20260715"),
            org.mockito.ArgumentMatchers.eq(productId),
            deltaCaptor.capture(),
            org.mockito.ArgumentMatchers.eq(Duration.ofDays(2))
        );
        assertThat(deltaCaptor.getValue()).isCloseTo(2.1, within(0.001));
    }

    @Test
    void 키_날짜는_이벤트_타임스탬프_기준으로_생성된다() {
        UUID productId = UUID.randomUUID();
        ZonedDateTime eventTime = ZonedDateTime.parse("2026-07-14T23:59:59+09:00");

        rankingScoreService.applyView(productId, eventTime);

        verify(rankingRepository).incrementScore("ranking:all:20260714", productId, 0.1, Duration.ofDays(2));
        verify(rankingRepository).incrementScore("ranking:hourly:2026071423", productId, 0.1, Duration.ofHours(4));
    }
}
