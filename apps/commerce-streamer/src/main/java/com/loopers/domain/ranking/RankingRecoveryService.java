package com.loopers.domain.ranking;

import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingRecoveryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZONE);
    private static final Duration TTL_DAILY = Duration.ofDays(2);

    private static final double DEFAULT_WEIGHT_VIEW  = 0.1;
    private static final double DEFAULT_WEIGHT_LIKE  = 0.2;
    private static final double DEFAULT_WEIGHT_ORDER = 0.7;

    private final RankingRepository rankingRepository;
    private final RankingWeightService rankingWeightService;
    private final ProductMetricsRepository productMetricsRepository;

    public void recoverIfNeeded(LocalDate date) {
        String key = "ranking:all:" + date.format(DATE_FORMAT);

        if (rankingRepository.existsKey(key)) {
            return;
        }

        List<ProductMetricsModel> metrics = productMetricsRepository.findAllByDate(date);
        if (metrics.isEmpty()) {
            log.info("랭킹 복구 대상 없음 — key={}", key);
            return;
        }

        double wView  = rankingWeightService.getWeight("VIEW",  DEFAULT_WEIGHT_VIEW);
        double wLike  = rankingWeightService.getWeight("LIKE",  DEFAULT_WEIGHT_LIKE);
        double wOrder = rankingWeightService.getWeight("ORDER", DEFAULT_WEIGHT_ORDER);

        for (ProductMetricsModel m : metrics) {
            double score = m.getViewCount()  * wView
                         + m.getLikeCount()  * wLike
                         + m.getSalesCount() * wOrder;
            rankingRepository.setScore(key, m.getProductId(), score, TTL_DAILY);
        }

        log.info("랭킹 복구 완료 — key={}, 상품 수={}", key, metrics.size());
    }
}
