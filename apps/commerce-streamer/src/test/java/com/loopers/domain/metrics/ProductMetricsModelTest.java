package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMetricsModelTest {

    @DisplayName("increment/decrement")
    @Nested
    class IncrementDecrement {

        @DisplayName("incrementLike 호출 시, likeCount가 1 증가하고 lastEventAt이 갱신된다.")
        @Test
        void incrementsLikeCount() {
            ProductMetricsModel metrics = new ProductMetricsModel(UUID.randomUUID());
            ZonedDateTime eventTime = ZonedDateTime.now();

            metrics.incrementLike(eventTime);

            assertThat(metrics.getLikeCount()).isEqualTo(1);
            assertThat(metrics.getLastEventAt()).isEqualTo(eventTime);
        }

        @DisplayName("decrementLike 호출 시, likeCount가 0 미만으로 내려가지 않는다.")
        @Test
        void decrementsLikeCount_neverBelowZero() {
            ProductMetricsModel metrics = new ProductMetricsModel(UUID.randomUUID());

            metrics.decrementLike(ZonedDateTime.now());

            assertThat(metrics.getLikeCount()).isZero();
        }

        @DisplayName("incrementView 호출 시, viewCount가 1 증가한다.")
        @Test
        void incrementsViewCount() {
            ProductMetricsModel metrics = new ProductMetricsModel(UUID.randomUUID());

            metrics.incrementView(ZonedDateTime.now());

            assertThat(metrics.getViewCount()).isEqualTo(1);
        }

        @DisplayName("incrementSales 호출 시, salesCount가 1 증가한다.")
        @Test
        void incrementsSalesCount() {
            ProductMetricsModel metrics = new ProductMetricsModel(UUID.randomUUID());

            metrics.incrementSales(ZonedDateTime.now());

            assertThat(metrics.getSalesCount()).isEqualTo(1);
        }
    }

    @DisplayName("isStale")
    @Nested
    class IsStale {

        @DisplayName("lastEventAt이 없으면(최초), stale이 아니다.")
        @Test
        void notStale_whenNoLastEventAt() {
            ProductMetricsModel metrics = new ProductMetricsModel(UUID.randomUUID());

            assertThat(metrics.isStale(ZonedDateTime.now())).isFalse();
        }

        @DisplayName("들어온 이벤트 시각이 lastEventAt보다 이전이면, stale이다.")
        @Test
        void isStale_whenEventTimeBeforeLastEventAt() {
            ProductMetricsModel metrics = new ProductMetricsModel(UUID.randomUUID());
            ZonedDateTime now = ZonedDateTime.now();
            metrics.incrementLike(now);

            assertThat(metrics.isStale(now.minusSeconds(10))).isTrue();
        }

        @DisplayName("들어온 이벤트 시각이 lastEventAt보다 이후면, stale이 아니다.")
        @Test
        void notStale_whenEventTimeAfterLastEventAt() {
            ProductMetricsModel metrics = new ProductMetricsModel(UUID.randomUUID());
            ZonedDateTime now = ZonedDateTime.now();
            metrics.incrementLike(now);

            assertThat(metrics.isStale(now.plusSeconds(10))).isFalse();
        }
    }
}
