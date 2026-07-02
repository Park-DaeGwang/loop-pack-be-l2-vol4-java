package com.loopers.domain.metrics;

import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductMetricsService {

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;

    /**
     * 멱등 체크(이벤트 단위) → action 실행 → event_handled 기록까지 한 트랜잭션으로 묶는다.
     * 하나의 이벤트(예: 주문 결제확정)가 여러 상품에 영향을 줄 수 있어, 멱등 체크는 상품 단위가 아닌
     * 이벤트 단위로 한 번만 한다 — 그래야 다상품 주문에서 첫 상품 처리 후 나머지가 스킵되는 걸 막는다.
     */
    @Transactional
    public void applyIfNotHandled(UUID eventId, Runnable action) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            log.info("이미 처리된 이벤트 — eventId={}", eventId);
            return;
        }
        action.run();
        eventHandledRepository.save(new EventHandledModel(eventId));
    }

    /** 상품 하나의 지표를 갱신한다. staleness 체크 포함, 자체 멱등 체크는 하지 않는다(호출자가 이벤트 단위로 처리). */
    public void applyToProduct(UUID productId, ZonedDateTime eventTime, BiConsumer<ProductMetricsModel, ZonedDateTime> operation) {
        ProductMetricsModel metrics = productMetricsRepository.findByProductId(productId)
            .orElseGet(() -> new ProductMetricsModel(productId));

        if (metrics.isStale(eventTime)) {
            log.warn("오래된 이벤트 스킵 — productId={}, eventTime={}", productId, eventTime);
            return;
        }
        operation.accept(metrics, eventTime);
        productMetricsRepository.save(metrics);
    }

    /** view/sales처럼 단순 누적이라 순서와 무관한 연산용 — staleness 체크 없이 항상 반영한다. */
    public void applyToProductUnordered(UUID productId, Consumer<ProductMetricsModel> operation) {
        ProductMetricsModel metrics = productMetricsRepository.findByProductId(productId)
            .orElseGet(() -> new ProductMetricsModel(productId));

        operation.accept(metrics);
        productMetricsRepository.save(metrics);
    }
}
