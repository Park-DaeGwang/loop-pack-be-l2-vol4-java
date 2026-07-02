package com.loopers.domain.metrics;

import com.loopers.domain.eventhandled.EventHandledRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductMetricsServiceTest {

    @DisplayName("applyIfNotHandled")
    @Nested
    class ApplyIfNotHandled {

        @DisplayName("이미 처리된 eventId면, action을 실행하지 않는다.")
        @Test
        void skipsAction_whenAlreadyHandled() {
            ProductMetricsRepository productMetricsRepository = mock(ProductMetricsRepository.class);
            EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
            ProductMetricsService service = new ProductMetricsService(productMetricsRepository, eventHandledRepository);
            UUID eventId = UUID.randomUUID();
            when(eventHandledRepository.existsByEventId(eventId)).thenReturn(true);
            AtomicInteger callCount = new AtomicInteger();

            service.applyIfNotHandled(eventId, callCount::incrementAndGet);

            assertThat(callCount.get()).isZero();
            verify(eventHandledRepository, never()).save(any());
        }

        @DisplayName("처리되지 않은 eventId면, action을 실행하고 event_handled에 기록한다.")
        @Test
        void runsActionAndMarksHandled_whenNotHandled() {
            ProductMetricsRepository productMetricsRepository = mock(ProductMetricsRepository.class);
            EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
            ProductMetricsService service = new ProductMetricsService(productMetricsRepository, eventHandledRepository);
            UUID eventId = UUID.randomUUID();
            when(eventHandledRepository.existsByEventId(eventId)).thenReturn(false);
            AtomicInteger callCount = new AtomicInteger();

            service.applyIfNotHandled(eventId, callCount::incrementAndGet);

            assertThat(callCount.get()).isEqualTo(1);
            verify(eventHandledRepository, times(1)).save(any());
        }
    }

    @DisplayName("applyToProduct")
    @Nested
    class ApplyToProduct {

        @DisplayName("신규 상품이면, 새로 생성해서 operation을 적용하고 저장한다.")
        @Test
        void createsAndSaves_whenProductMetricsNotExists() {
            ProductMetricsRepository productMetricsRepository = mock(ProductMetricsRepository.class);
            EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
            ProductMetricsService service = new ProductMetricsService(productMetricsRepository, eventHandledRepository);
            UUID productId = UUID.randomUUID();
            when(productMetricsRepository.findByProductId(productId)).thenReturn(Optional.empty());

            service.applyToProduct(productId, ZonedDateTime.now(), (m, t) -> m.incrementLike(t));

            verify(productMetricsRepository).save(any());
        }

        @DisplayName("stale한 이벤트면, operation을 적용하지 않고 저장도 하지 않는다.")
        @Test
        void skipsSave_whenStale() {
            ProductMetricsRepository productMetricsRepository = mock(ProductMetricsRepository.class);
            EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
            ProductMetricsService service = new ProductMetricsService(productMetricsRepository, eventHandledRepository);
            UUID productId = UUID.randomUUID();
            ZonedDateTime now = ZonedDateTime.now();
            ProductMetricsModel existing = new ProductMetricsModel(productId);
            existing.incrementLike(now);
            when(productMetricsRepository.findByProductId(productId)).thenReturn(Optional.of(existing));

            service.applyToProduct(productId, now.minusSeconds(10), (m, t) -> m.incrementLike(t));

            assertThat(existing.getLikeCount()).isEqualTo(1); // 추가 반영 안 됨
            verify(productMetricsRepository, never()).save(any());
        }
    }

    @DisplayName("applyToProductUnordered")
    @Nested
    class ApplyToProductUnordered {

        @DisplayName("신규 상품이면, 새로 생성해서 operation을 적용하고 저장한다.")
        @Test
        void createsAndSaves_whenProductMetricsNotExists() {
            ProductMetricsRepository productMetricsRepository = mock(ProductMetricsRepository.class);
            EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
            ProductMetricsService service = new ProductMetricsService(productMetricsRepository, eventHandledRepository);
            UUID productId = UUID.randomUUID();
            when(productMetricsRepository.findByProductId(productId)).thenReturn(Optional.empty());

            service.applyToProductUnordered(productId, ProductMetricsModel::incrementView);

            verify(productMetricsRepository).save(any());
        }

        @DisplayName("이전 이벤트보다 시간상 앞선 이벤트여도, staleness 체크 없이 항상 반영한다.")
        @Test
        void alwaysApplies_regardlessOfOrder() {
            ProductMetricsRepository productMetricsRepository = mock(ProductMetricsRepository.class);
            EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
            ProductMetricsService service = new ProductMetricsService(productMetricsRepository, eventHandledRepository);
            UUID productId = UUID.randomUUID();
            ProductMetricsModel existing = new ProductMetricsModel(productId);
            existing.incrementLike(ZonedDateTime.now()); // lastEventAt을 미래 시점으로 미리 세팅
            when(productMetricsRepository.findByProductId(productId)).thenReturn(Optional.of(existing));

            service.applyToProductUnordered(productId, ProductMetricsModel::incrementView);

            assertThat(existing.getViewCount()).isEqualTo(1);
            verify(productMetricsRepository).save(existing);
        }
    }
}
