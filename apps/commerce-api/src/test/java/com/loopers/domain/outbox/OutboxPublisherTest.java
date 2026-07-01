package com.loopers.domain.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    @DisplayName("publishPendingEvents")
    static class PublishPendingEvents {

        @DisplayName("미발행 이벤트를 Kafka 전송에 성공하면, markPublished를 호출한다.")
        @Test
        @SuppressWarnings("unchecked")
        void marksPublished_whenKafkaSendSucceeds() {
            // arrange
            OutboxEventService outboxEventService = mock(OutboxEventService.class);
            KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
            OutboxEventModel event = new OutboxEventModel("catalog-events", UUID.randomUUID().toString(), "ProductLikedEvent", "{}");
            when(outboxEventService.findUnpublished(100)).thenReturn(List.of(event));
            when(kafkaTemplate.send(any(String.class), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
            OutboxPublisher publisher = new OutboxPublisher(outboxEventService, kafkaTemplate);

            // act
            publisher.publishPendingEvents();

            // assert
            verify(outboxEventService).markPublished(event.getId());
        }

        @DisplayName("Kafka 전송이 실패하면, markPublished를 호출하지 않는다.")
        @Test
        @SuppressWarnings("unchecked")
        void doesNotMarkPublished_whenKafkaSendFails() {
            // arrange
            OutboxEventService outboxEventService = mock(OutboxEventService.class);
            KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
            OutboxEventModel event = new OutboxEventModel("catalog-events", UUID.randomUUID().toString(), "ProductLikedEvent", "{}");
            when(outboxEventService.findUnpublished(100)).thenReturn(List.of(event));
            CompletableFuture<SendResult<Object, Object>> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("broker unavailable"));
            when(kafkaTemplate.send(any(String.class), any(), any())).thenReturn(failed);
            OutboxPublisher publisher = new OutboxPublisher(outboxEventService, kafkaTemplate);

            // act
            publisher.publishPendingEvents();

            // assert
            verify(outboxEventService, never()).markPublished(any());
        }
    }
}
