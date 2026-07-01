package com.loopers.domain.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.payment.PaymentConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * product_metrics 집계용 이벤트를 Kafka로 발행한다.
 * 표시용 집계 데이터라 유실을 감수할 수 있어 Outbox 없이 직접 발행한다 — 유실 시 최악의 경우
 * 화면에 보이는 숫자가 잠깐 부정확한 정도이지, 비즈니스 사실(좋아요/결제) 자체는 이미 각자 확정돼 있다.
 * @Async를 붙이는 이유: kafkaTemplate.send()는 평소엔 논블로킹이지만 브로커 지연/버퍼 포화 시
 * max.block.ms(기본 60s)까지 호출 스레드를 블로킹할 수 있어, 메인 요청 스레드와 분리해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsEventPublisher {

    private static final String CATALOG_EVENTS_TOPIC = "catalog-events";
    private static final String ORDER_EVENTS_TOPIC = "order-events";

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Async("kafkaEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductLikedEvent event) {
        publish(CATALOG_EVENTS_TOPIC, event.productId().toString(), event);
    }

    @Async("kafkaEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductUnlikedEvent event) {
        publish(CATALOG_EVENTS_TOPIC, event.productId().toString(), event);
    }

    @Async("kafkaEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PaymentConfirmedEvent event) {
        publish(ORDER_EVENTS_TOPIC, event.orderId().toString(), event);
    }

    private void publish(String topic, String key, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, payload)
                .exceptionally(ex -> {
                    log.warn("메트릭 이벤트 발행 실패 — topic={}, key={}", topic, key, ex);
                    return null;
                });
        } catch (Exception e) {
            log.warn("메트릭 이벤트 직렬화 실패 — topic={}, key={}", topic, key, e);
        }
    }
}
