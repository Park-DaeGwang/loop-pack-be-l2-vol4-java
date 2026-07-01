package com.loopers.domain.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.payment.PaymentConfirmedEvent;
import com.loopers.domain.product.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;

/**
 * product_metrics 집계용 이벤트를 Kafka로 발행한다.
 * 표시용 집계 데이터라 유실을 감수할 수 있어 Outbox 없이 직접 발행한다 — 유실 시 최악의 경우
 * 화면에 보이는 숫자가 잠깐 부정확한 정도이지, 비즈니스 사실(좋아요/결제) 자체는 이미 각자 확정돼 있다.
 * 한 토픽에 여러 이벤트 타입이 섞여 나가므로(catalog-events: liked/unliked/viewed),
 * 컨슈머가 구분할 수 있도록 Kafka 헤더에 eventType을 실어 보낸다 — payload JSON만으로는
 * ProductLikedEvent/UnlikedEvent가 필드가 동일해 구분이 불가능하기 때문이다.
 * @Async를 붙이는 이유: kafkaTemplate.send()는 평소엔 논블로킹이지만 브로커 지연/버퍼 포화 시
 * max.block.ms(기본 60s)까지 호출 스레드를 블로킹할 수 있어, 메인 요청 스레드와 분리해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsEventPublisher {

    private static final String CATALOG_EVENTS_TOPIC = "catalog-events";
    private static final String ORDER_EVENTS_TOPIC = "order-events";
    private static final String EVENT_TYPE_HEADER = "eventType";

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
    public void handle(ProductViewedEvent event) {
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
            ProducerRecord<Object, Object> record = new ProducerRecord<>(topic, null, key, payload);
            record.headers().add(EVENT_TYPE_HEADER, event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record)
                .exceptionally(ex -> {
                    log.warn("메트릭 이벤트 발행 실패 — topic={}, key={}", topic, key, ex);
                    return null;
                });
        } catch (Exception e) {
            log.warn("메트릭 이벤트 직렬화 실패 — topic={}, key={}", topic, key, e);
        }
    }
}
