package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class RankingScoreService {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

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
        rankingRepository.incrementScore(toKey(eventTime), productId, weight);
    }

    public void applyLike(UUID productId, ZonedDateTime eventTime) {
        double weight = rankingWeightService.getWeight(EVENT_LIKE, DEFAULT_WEIGHT_LIKE);
        rankingRepository.incrementScore(toKey(eventTime), productId, weight);
    }

    public void applyUnlike(UUID productId, ZonedDateTime eventTime) {
        double weight = rankingWeightService.getWeight(EVENT_LIKE, DEFAULT_WEIGHT_LIKE);
        rankingRepository.incrementScore(toKey(eventTime), productId, -weight);
    }

    public void applyOrder(UUID productId, int quantity, ZonedDateTime eventTime) {
        double weight = rankingWeightService.getWeight(EVENT_ORDER, DEFAULT_WEIGHT_ORDER);
        rankingRepository.incrementScore(toKey(eventTime), productId, weight * quantity);
    }

    private String toKey(ZonedDateTime eventTime) {
        return KEY_PREFIX + eventTime.format(DATE_FORMAT);
    }
}
