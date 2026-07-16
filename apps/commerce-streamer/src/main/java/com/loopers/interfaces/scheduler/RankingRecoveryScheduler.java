package com.loopers.interfaces.scheduler;

import com.loopers.domain.ranking.RankingRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingRecoveryScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final RankingRecoveryService rankingRecoveryService;

    @Scheduled(fixedDelay = 600_000) // 10분 주기
    public void recoverRanking() {
        LocalDate today     = LocalDate.now(ZONE);
        LocalDate yesterday = today.minusDays(1);
        rankingRecoveryService.recoverIfNeeded(today);
        rankingRecoveryService.recoverIfNeeded(yesterday);
    }
}
