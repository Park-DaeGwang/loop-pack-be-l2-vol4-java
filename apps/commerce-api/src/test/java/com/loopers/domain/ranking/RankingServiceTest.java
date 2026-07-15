package com.loopers.domain.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankingServiceTest {

    private RankingRepository rankingRepository;
    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        rankingRepository = mock(RankingRepository.class);
        rankingService = new RankingService(rankingRepository);
    }

    @Test
    void Top_N_조회_시_page와_size로_offset을_계산한다() {
        String date = "20260715";
        UUID productId = UUID.randomUUID();
        when(rankingRepository.findTopRanked("ranking:all:20260715", 0, 20))
            .thenReturn(List.of(productId));

        List<UUID> result = rankingService.getTopRanked(date, 1, 20);

        assertThat(result).containsExactly(productId);
    }

    @Test
    void 두번째_페이지_조회_시_offset이_size만큼_이동한다() {
        String date = "20260715";
        UUID productId = UUID.randomUUID();
        when(rankingRepository.findTopRanked("ranking:all:20260715", 20, 20))
            .thenReturn(List.of(productId));

        List<UUID> result = rankingService.getTopRanked(date, 2, 20);

        assertThat(result).containsExactly(productId);
    }

    @Test
    void 개별_상품_순위_조회_시_1_based로_반환한다() {
        UUID productId = UUID.randomUUID();
        when(rankingRepository.findRank("ranking:all:20260715", productId)).thenReturn(0L);

        Long rank = rankingService.getRank("20260715", productId);

        assertThat(rank).isEqualTo(1L);
    }

    @Test
    void 랭킹에_없는_상품은_순위가_null이다() {
        UUID productId = UUID.randomUUID();
        when(rankingRepository.findRank("ranking:all:20260715", productId)).thenReturn(null);

        Long rank = rankingService.getRank("20260715", productId);

        assertThat(rank).isNull();
    }

    @Test
    void 전체_랭킹_수를_반환한다() {
        when(rankingRepository.countRanked("ranking:all:20260715")).thenReturn(100L);

        long count = rankingService.countRanked("20260715");

        assertThat(count).isEqualTo(100L);
    }
}
