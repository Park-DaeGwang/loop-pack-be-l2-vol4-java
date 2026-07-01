package com.loopers.domain.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.payment.PaymentConfirmedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 시스템 간 전파가 필요한 도메인 이벤트를 Outbox 테이블에 기록한다.
 * 비즈니스 트랜잭션과 원자성을 보장해야 하므로 AFTER_COMMIT이 아닌 일반 @EventListener(같은 트랜잭션)를 쓴다.
 */
@RequiredArgsConstructor
@Component
public class OutboxEventListener {

    private static final String CATALOG_EVENTS_TOPIC = "catalog-events";
    private static final String ORDER_EVENTS_TOPIC = "order-events";

    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    @EventListener
    public void handle(ProductLikedEvent event) {
        record(CATALOG_EVENTS_TOPIC, event.productId().toString(), event);
    }

    @EventListener
    public void handle(ProductUnlikedEvent event) {
        record(CATALOG_EVENTS_TOPIC, event.productId().toString(), event);
    }

    @EventListener
    public void handle(PaymentConfirmedEvent event) {
        record(ORDER_EVENTS_TOPIC, event.orderId().toString(), event);
    }

    private void record(String topic, String key, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventService.record(topic, key, event.getClass().getSimpleName(), payload);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "이벤트 직렬화에 실패했습니다.");
        }
    }
}
