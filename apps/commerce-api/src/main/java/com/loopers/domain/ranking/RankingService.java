package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class RankingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZONE);
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZONE);
    private static final Duration TTL = Duration.ofDays(2);
    private static final double CARRY_OVER_WEIGHT = 0.1;
    private static final int HOURLY_FALLBACK_MAX = 3;

    private final RankingRepository rankingRepository;

    public List<UUID> getTopRanked(RankingType type, String date, int page, int size) {
        String key = resolveKey(type, date);
        return rankingRepository.findTopRanked(key, (page - 1) * size, size);
    }

    public long countRanked(RankingType type, String date) {
        return rankingRepository.countRanked(resolveKey(type, date));
    }

    public Long getRank(RankingType type, String date, UUID productId) {
        Long rank = rankingRepository.findRank(resolveKey(type, date), productId);
        return rank == null ? null : rank + 1;
    }

    public Double getScore(RankingType type, String date, UUID productId) {
        return rankingRepository.findScore(resolveKey(type, date), productId);
    }

    public void carryOverForTomorrow() {
        String today    = LocalDate.now(ZONE).format(DATE_FORMAT);
        String tomorrow = LocalDate.now(ZONE).plusDays(1).format(DATE_FORMAT);
        rankingRepository.carryOver(RankingType.DAILY.toKey(today), RankingType.DAILY.toKey(tomorrow), CARRY_OVER_WEIGHT, TTL);
    }

    private String resolveKey(RankingType type, String date) {
        if (type != RankingType.HOURLY) {
            return type.toKey(date);
        }
        ZonedDateTime target = ZonedDateTime.parse(date, HOUR_FORMAT);
        for (int i = 0; i <= HOURLY_FALLBACK_MAX; i++) {
            String key = RankingType.HOURLY.toKey(target.minusHours(i).format(HOUR_FORMAT));
            if (rankingRepository.countRanked(key) > 0) {
                return key;
            }
        }
        return RankingType.HOURLY.toKey(date);
    }
}
