package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RankingSchedulerTest {

    @Test
    void carryOverRanking_호출_시_RankingService_carryOverForTomorrow가_실행된다() {
        RankingService rankingService = mock(RankingService.class);
        RankingScheduler scheduler = new RankingScheduler(rankingService);

        scheduler.carryOverRanking();

        verify(rankingService).carryOverForTomorrow();
    }
}
