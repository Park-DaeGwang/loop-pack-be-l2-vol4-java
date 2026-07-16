package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class RankingScoreService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final String DAILY_KEY_PREFIX  = "ranking:all:";
    private static final String HOURLY_KEY_PREFIX = "ranking:hourly:";
    private static final DateTimeFormatter DATE_FORMAT  = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZONE);
    private static final DateTimeFormatter HOUR_FORMAT  = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZONE);
    private static final Duration TTL_DAILY  = Duration.ofDays(2);
    private static final Duration TTL_HOURLY = Duration.ofHours(4);

    static final String EVENT_VIEW  = "VIEW";
    static final String EVENT_LIKE  = "LIKE";
    static final String EVENT_ORDER = "ORDER";

    static final double DEFAULT_WEIGHT_VIEW  = 0.1;
    static final double DEFAULT_WEIGHT_LIKE  = 0.2;
    static final double DEFAULT_WEIGHT_ORDER = 0.7;

    private final RankingRepository rankingRepository;
    private final RankingWeightService rankingWeightService;

    public void applyView(UUID productId, ZonedDateTime eventTime) {
        double weight = rankingWeightService.getWeight(EVENT_VIEW, DEFAULT_WEIGHT_VIEW);
        rankingRepository.incrementScore(toDailyKey(eventTime),  productId, weight, TTL_DAILY);
        rankingRepository.incrementScore(toHourlyKey(eventTime), productId, weight, TTL_HOURLY);
    }

    public void applyLike(UUID productId, ZonedDateTime eventTime) {
        double weight = rankingWeightService.getWeight(EVENT_LIKE, DEFAULT_WEIGHT_LIKE);
        rankingRepository.incrementScore(toDailyKey(eventTime),  productId, weight, TTL_DAILY);
        rankingRepository.incrementScore(toHourlyKey(eventTime), productId, weight, TTL_HOURLY);
    }

    public void applyUnlike(UUID productId, ZonedDateTime eventTime) {
        double weight = rankingWeightService.getWeight(EVENT_LIKE, DEFAULT_WEIGHT_LIKE);
        rankingRepository.incrementScore(toDailyKey(eventTime),  productId, -weight, TTL_DAILY);
        rankingRepository.incrementScore(toHourlyKey(eventTime), productId, -weight, TTL_HOURLY);
    }

    public void applyOrder(UUID productId, int quantity, ZonedDateTime eventTime) {
        double weight = rankingWeightService.getWeight(EVENT_ORDER, DEFAULT_WEIGHT_ORDER);
        rankingRepository.incrementScore(toDailyKey(eventTime),  productId, weight * quantity, TTL_DAILY);
        rankingRepository.incrementScore(toHourlyKey(eventTime), productId, weight * quantity, TTL_HOURLY);
    }

    private String toDailyKey(ZonedDateTime t)  { return DAILY_KEY_PREFIX  + DATE_FORMAT.format(t); }
    private String toHourlyKey(ZonedDateTime t) { return HOURLY_KEY_PREFIX + HOUR_FORMAT.format(t); }
}
