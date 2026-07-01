package com.loopers.domain.outbox;

import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.payment.PaymentConfirmedEvent;
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxEventIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("ProductLikedEvent가 발행되면, catalog-events 토픽으로 outbox에 기록된다.")
    @Test
    void recordsOutboxRow_whenProductLikedEventPublished() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        eventPublisher.publishEvent(new ProductLikedEvent(userId, productId));

        List<OutboxEventModel> events = outboxEventJpaRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTopic()).isEqualTo("catalog-events");
        assertThat(events.get(0).getEventKey()).isEqualTo(productId.toString());
        assertThat(events.get(0).isPublished()).isFalse();
    }

    @DisplayName("PaymentConfirmedEvent가 발행되면, order-events 토픽으로 outbox에 기록된다.")
    @Test
    void recordsOutboxRow_whenPaymentConfirmedEventPublished() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        eventPublisher.publishEvent(new PaymentConfirmedEvent(orderId, userId, 10000L));

        List<OutboxEventModel> events = outboxEventJpaRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTopic()).isEqualTo("order-events");
        assertThat(events.get(0).getEventKey()).isEqualTo(orderId.toString());
    }
}
