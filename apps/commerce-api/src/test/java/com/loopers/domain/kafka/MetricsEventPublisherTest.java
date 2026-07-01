package com.loopers.domain.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.payment.PaymentConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsEventPublisherTest {

    @DisplayName("handle")
    static class Handle {

        @SuppressWarnings("unchecked")
        private KafkaTemplate<Object, Object> kafkaTemplate() {
            KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
            when(kafkaTemplate.send(any(String.class), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
            return kafkaTemplate;
        }

        @DisplayName("ProductLikedEvent를 받으면, catalog-events 토픽으로 발행한다.")
        @Test
        void publishes_whenProductLikedEventReceived() {
            KafkaTemplate<Object, Object> kafkaTemplate = kafkaTemplate();
            MetricsEventPublisher publisher = new MetricsEventPublisher(kafkaTemplate, new ObjectMapper());
            UUID productId = UUID.randomUUID();

            publisher.handle(new ProductLikedEvent(UUID.randomUUID(), productId));

            verify(kafkaTemplate).send(eq("catalog-events"), eq(productId.toString()), contains(productId.toString()));
        }

        @DisplayName("ProductUnlikedEvent를 받으면, catalog-events 토픽으로 발행한다.")
        @Test
        void publishes_whenProductUnlikedEventReceived() {
            KafkaTemplate<Object, Object> kafkaTemplate = kafkaTemplate();
            MetricsEventPublisher publisher = new MetricsEventPublisher(kafkaTemplate, new ObjectMapper());
            UUID productId = UUID.randomUUID();

            publisher.handle(new ProductUnlikedEvent(UUID.randomUUID(), productId));

            verify(kafkaTemplate).send(eq("catalog-events"), eq(productId.toString()), contains(productId.toString()));
        }

        @DisplayName("PaymentConfirmedEvent를 받으면, order-events 토픽으로 발행한다.")
        @Test
        void publishes_whenPaymentConfirmedEventReceived() {
            KafkaTemplate<Object, Object> kafkaTemplate = kafkaTemplate();
            MetricsEventPublisher publisher = new MetricsEventPublisher(kafkaTemplate, new ObjectMapper());
            UUID orderId = UUID.randomUUID();

            publisher.handle(new PaymentConfirmedEvent(orderId, UUID.randomUUID(), 10000L));

            verify(kafkaTemplate).send(eq("order-events"), eq(orderId.toString()), contains(orderId.toString()));
        }
    }
}
