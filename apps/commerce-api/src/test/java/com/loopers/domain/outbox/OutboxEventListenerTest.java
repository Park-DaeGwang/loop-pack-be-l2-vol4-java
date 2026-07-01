package com.loopers.domain.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.payment.PaymentConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventListenerTest {

    private final OutboxEventService outboxEventService = mock(OutboxEventService.class);
    private final OutboxEventListener listener = new OutboxEventListener(outboxEventService, new ObjectMapper());

    @DisplayName("ProductLikedEvent를 받으면, catalog-events 토픽/productId 키로 기록한다.")
    @Test
    void records_whenProductLikedEventReceived() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        listener.handle(new ProductLikedEvent(userId, productId));

        verify(outboxEventService).record(
            eq("catalog-events"), eq(productId.toString()), eq("ProductLikedEvent"), contains(productId.toString())
        );
    }

    @DisplayName("ProductUnlikedEvent를 받으면, catalog-events 토픽/productId 키로 기록한다.")
    @Test
    void records_whenProductUnlikedEventReceived() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        listener.handle(new ProductUnlikedEvent(userId, productId));

        verify(outboxEventService).record(
            eq("catalog-events"), eq(productId.toString()), eq("ProductUnlikedEvent"), contains(productId.toString())
        );
    }

    @DisplayName("PaymentConfirmedEvent를 받으면, order-events 토픽/orderId 키로 기록한다.")
    @Test
    void records_whenPaymentConfirmedEventReceived() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        listener.handle(new PaymentConfirmedEvent(orderId, userId, 10000L));

        verify(outboxEventService).record(
            eq("order-events"), eq(orderId.toString()), eq("PaymentConfirmedEvent"), contains(orderId.toString())
        );
    }
}
