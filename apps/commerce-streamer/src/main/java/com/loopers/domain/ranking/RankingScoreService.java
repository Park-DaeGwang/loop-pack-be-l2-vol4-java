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

    private static final double WEIGHT_VIEW = 0.1;
    private static final double WEIGHT_LIKE = 0.2;
    private static final double WEIGHT_ORDER = 0.7;

    private final RankingRepository rankingRepository;

    public void applyView(UUID productId, ZonedDateTime eventTime) {
        rankingRepository.incrementScore(toKey(eventTime), productId, WEIGHT_VIEW);
    }

    public void applyLike(UUID productId, ZonedDateTime eventTime) {
        rankingRepository.incrementScore(toKey(eventTime), productId, WEIGHT_LIKE);
    }

    public void applyUnlike(UUID productId, ZonedDateTime eventTime) {
        rankingRepository.incrementScore(toKey(eventTime), productId, -WEIGHT_LIKE);
    }

    public void applyOrder(UUID productId, int quantity, ZonedDateTime eventTime) {
        rankingRepository.incrementScore(toKey(eventTime), productId, WEIGHT_ORDER * quantity);
    }

    private String toKey(ZonedDateTime eventTime) {
        return KEY_PREFIX + eventTime.format(DATE_FORMAT);
    }
}
