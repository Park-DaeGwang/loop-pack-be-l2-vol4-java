package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    static final String EVENT_VIEW   = "VIEW";
    static final String EVENT_LIKE   = "LIKE";
    static final String EVENT_UNLIKE = "UNLIKE";
    static final String EVENT_ORDER  = "ORDER";

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

    public record BatchEvent(String eventType, UUID productId, int quantity, ZonedDateTime eventTime) {}

    public void flushBatch(List<BatchEvent> events) {
        Map<String, Map<UUID, Double>> deltasByKey = new LinkedHashMap<>();

        for (BatchEvent event : events) {
            double delta = resolveDelta(event);
            if (delta == 0.0) continue;
            accumulate(deltasByKey, toDailyKey(event.eventTime()),  event.productId(), delta);
            accumulate(deltasByKey, toHourlyKey(event.eventTime()), event.productId(), delta);
        }

        for (Map.Entry<String, Map<UUID, Double>> entry : deltasByKey.entrySet()) {
            String key = entry.getKey();
            Duration ttl = key.startsWith(HOURLY_KEY_PREFIX) ? TTL_HOURLY : TTL_DAILY;
            for (Map.Entry<UUID, Double> score : entry.getValue().entrySet()) {
                rankingRepository.incrementScore(key, score.getKey(), score.getValue(), ttl);
            }
        }
    }

    private double resolveDelta(BatchEvent e) {
        return switch (e.eventType()) {
            case EVENT_VIEW   ->  rankingWeightService.getWeight(EVENT_VIEW,  DEFAULT_WEIGHT_VIEW);
            case EVENT_LIKE   ->  rankingWeightService.getWeight(EVENT_LIKE,  DEFAULT_WEIGHT_LIKE);
            case EVENT_UNLIKE -> -rankingWeightService.getWeight(EVENT_LIKE,  DEFAULT_WEIGHT_LIKE);
            case EVENT_ORDER  ->  rankingWeightService.getWeight(EVENT_ORDER, DEFAULT_WEIGHT_ORDER) * e.quantity();
            default -> 0.0;
        };
    }

    private void accumulate(Map<String, Map<UUID, Double>> map, String key, UUID productId, double delta) {
        map.computeIfAbsent(key, k -> new LinkedHashMap<>()).merge(productId, delta, Double::sum);
    }

    private String toDailyKey(ZonedDateTime t)  { return DAILY_KEY_PREFIX  + DATE_FORMAT.format(t); }
    private String toHourlyKey(ZonedDateTime t) { return HOURLY_KEY_PREFIX + HOUR_FORMAT.format(t); }
}
