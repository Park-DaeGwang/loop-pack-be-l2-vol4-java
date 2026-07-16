package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class RankingService {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration TTL = Duration.ofDays(2);
    private static final double CARRY_OVER_WEIGHT = 0.1;

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

    public void carryOverForTomorrow() {
        String today    = LocalDate.now(ZoneId.systemDefault()).format(DATE_FORMAT);
        String tomorrow = LocalDate.now(ZoneId.systemDefault()).plusDays(1).format(DATE_FORMAT);
        rankingRepository.carryOver(KEY_PREFIX + today, KEY_PREFIX + tomorrow, CARRY_OVER_WEIGHT, TTL);
    }
}
