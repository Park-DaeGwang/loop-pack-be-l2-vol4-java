package com.loopers.domain.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.payment.PaymentConfirmedEvent;
import com.loopers.domain.product.ProductViewedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsEventPublisherTest {

    @DisplayName("handle")
    static class Handle {

        @SuppressWarnings("unchecked")
        private KafkaTemplate<Object, Object> kafkaTemplate() {
            KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
            when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
            return kafkaTemplate;
        }

        @SuppressWarnings("unchecked")
        private ProducerRecord<Object, Object> captureSentRecord(KafkaTemplate<Object, Object> kafkaTemplate) {
            ArgumentCaptor<ProducerRecord<Object, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate).send(captor.capture());
            return captor.getValue();
        }

        private String headerValue(ProducerRecord<Object, Object> record, String key) {
            return new String(record.headers().lastHeader(key).value(), StandardCharsets.UTF_8);
        }

        @DisplayName("ProductLikedEvent를 받으면, catalog-events 토픽 + eventType 헤더로 발행한다.")
        @Test
        void publishes_whenProductLikedEventReceived() {
            KafkaTemplate<Object, Object> kafkaTemplate = kafkaTemplate();
            MetricsEventPublisher publisher = new MetricsEventPublisher(kafkaTemplate, new ObjectMapper());
            UUID productId = UUID.randomUUID();

            publisher.handle(new ProductLikedEvent(UUID.randomUUID(), productId));

            ProducerRecord<Object, Object> sent = captureSentRecord(kafkaTemplate);
            assertThat(sent.topic()).isEqualTo("catalog-events");
            assertThat(sent.key()).isEqualTo(productId.toString());
            assertThat(headerValue(sent, "eventType")).isEqualTo("ProductLikedEvent");
            assertThat((String) sent.value()).contains(productId.toString());
        }

        @DisplayName("ProductUnlikedEvent를 받으면, catalog-events 토픽 + eventType 헤더로 발행한다.")
        @Test
        void publishes_whenProductUnlikedEventReceived() {
            KafkaTemplate<Object, Object> kafkaTemplate = kafkaTemplate();
            MetricsEventPublisher publisher = new MetricsEventPublisher(kafkaTemplate, new ObjectMapper());
            UUID productId = UUID.randomUUID();

            publisher.handle(new ProductUnlikedEvent(UUID.randomUUID(), productId));

            ProducerRecord<Object, Object> sent = captureSentRecord(kafkaTemplate);
            assertThat(sent.topic()).isEqualTo("catalog-events");
            assertThat(headerValue(sent, "eventType")).isEqualTo("ProductUnlikedEvent");
        }

        @DisplayName("ProductViewedEvent를 받으면, catalog-events 토픽 + eventType 헤더로 발행한다.")
        @Test
        void publishes_whenProductViewedEventReceived() {
            KafkaTemplate<Object, Object> kafkaTemplate = kafkaTemplate();
            MetricsEventPublisher publisher = new MetricsEventPublisher(kafkaTemplate, new ObjectMapper());
            UUID productId = UUID.randomUUID();

            publisher.handle(new ProductViewedEvent(productId));

            ProducerRecord<Object, Object> sent = captureSentRecord(kafkaTemplate);
            assertThat(sent.topic()).isEqualTo("catalog-events");
            assertThat(headerValue(sent, "eventType")).isEqualTo("ProductViewedEvent");
        }

        @DisplayName("PaymentConfirmedEvent를 받으면, order-events 토픽 + eventType 헤더로 발행한다.")
        @Test
        void publishes_whenPaymentConfirmedEventReceived() {
            KafkaTemplate<Object, Object> kafkaTemplate = kafkaTemplate();
            MetricsEventPublisher publisher = new MetricsEventPublisher(kafkaTemplate, new ObjectMapper());
            UUID orderId = UUID.randomUUID();

            publisher.handle(new PaymentConfirmedEvent(orderId, UUID.randomUUID(), 10000L, List.of()));

            ProducerRecord<Object, Object> sent = captureSentRecord(kafkaTemplate);
            assertThat(sent.topic()).isEqualTo("order-events");
            assertThat(sent.key()).isEqualTo(orderId.toString());
            assertThat(headerValue(sent, "eventType")).isEqualTo("PaymentConfirmedEvent");
        }
    }
}
