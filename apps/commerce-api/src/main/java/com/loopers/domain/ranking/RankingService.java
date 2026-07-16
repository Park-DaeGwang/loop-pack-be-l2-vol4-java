package com.loopers.domain.ranking;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingService {

    private static final String CB_NAME = "redisRanking";
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZONE);
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZONE);
    private static final Duration TTL = Duration.ofDays(2);
    private static final double CARRY_OVER_WEIGHT = 0.1;
    private static final int HOURLY_FALLBACK_MAX = 3;

    private final RankingRepository rankingRepository;
    private final RankingSnapshotService rankingSnapshotService;

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getTopRankedFallback")
    public List<UUID> getTopRanked(RankingType type, String date, int page, int size) {
        String key = resolveKey(type, date);
        return rankingRepository.findTopRanked(key, (page - 1) * size, size);
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "countRankedFallback")
    public long countRanked(RankingType type, String date) {
        return rankingRepository.countRanked(resolveKey(type, date));
    }

    public Long getRank(RankingType type, String date, UUID productId) {
        try {
            Long rank = rankingRepository.findRank(resolveKey(type, date), productId);
            return rank == null ? null : rank + 1;
        } catch (Exception e) {
            log.warn("Redis 장애 — 상품 랭킹 조회 실패, productId={}", productId);
            return null;
        }
    }

    public Double getScore(RankingType type, String date, UUID productId) {
        try {
            return rankingRepository.findScore(resolveKey(type, date), productId);
        } catch (Exception e) {
            log.warn("Redis 장애 — 상품 점수 조회 실패, productId={}", productId);
            return null;
        }
    }

    public void carryOverForTomorrow() {
        String today    = LocalDate.now(ZONE).format(DATE_FORMAT);
        String tomorrow = LocalDate.now(ZONE).plusDays(1).format(DATE_FORMAT);
        rankingRepository.carryOver(RankingType.DAILY.toKey(today), RankingType.DAILY.toKey(tomorrow), CARRY_OVER_WEIGHT, TTL);
    }

    private List<UUID> getTopRankedFallback(RankingType type, String date, int page, int size, Exception e) {
        log.warn("Redis 장애 — 랭킹 스냅샷 반환, type={}, date={}", type, date);
        String key = type.toKey(date);
        return rankingSnapshotService.get(key, (page - 1) * size, size);
    }

    private long countRankedFallback(RankingType type, String date, Exception e) {
        log.warn("Redis 장애 — 스냅샷 카운트 반환, type={}, date={}", type, date);
        return rankingSnapshotService.count(type.toKey(date));
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
