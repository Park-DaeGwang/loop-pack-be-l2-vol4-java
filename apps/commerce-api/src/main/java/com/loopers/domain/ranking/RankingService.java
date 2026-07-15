package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class RankingService {

    private static final String KEY_PREFIX = "ranking:all:";

    private final RankingRepository rankingRepository;

    public List<UUID> getTopRanked(String date, int page, int size) {
        int offset = (page - 1) * size;
        return rankingRepository.findTopRanked(KEY_PREFIX + date, offset, size);
    }

    public long countRanked(String date) {
        return rankingRepository.countRanked(KEY_PREFIX + date);
    }

    public Long getRank(String date, UUID productId) {
        Long rank = rankingRepository.findRank(KEY_PREFIX + date, productId);
        return rank == null ? null : rank + 1;
    }

    public Double getScore(String date, UUID productId) {
        return rankingRepository.findScore(KEY_PREFIX + date, productId);
    }
}
