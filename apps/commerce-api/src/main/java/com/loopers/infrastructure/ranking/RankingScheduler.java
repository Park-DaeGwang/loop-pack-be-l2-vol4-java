package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingScheduler {

    private final RankingService rankingService;

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOverRanking() {
        log.info("랭킹 carry-over 시작");
        rankingService.carryOverForTomorrow();
        log.info("랭킹 carry-over 완료");
    }
}
