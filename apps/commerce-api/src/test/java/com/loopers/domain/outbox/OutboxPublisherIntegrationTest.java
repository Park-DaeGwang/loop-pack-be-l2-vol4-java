package com.loopers.domain.outbox;

import com.loopers.infrastructure.outbox.OutboxEventJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Kafka(Testcontainers)까지 붙여서 Outbox 발행이 진짜로 성공하는지 검증한다.
 * mock으로는 직렬화/연결/브로커 수락 여부를 확인할 수 없어 별도로 둔다.
 */
@SpringBootTest
class OutboxPublisherIntegrationTest {

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("미발행 이벤트를 실제 Kafka로 발행하면, published 상태로 갱신된다.")
    @Test
    void marksPublished_whenSentToRealKafka() {
        // arrange
        String productId = UUID.randomUUID().toString();
        outboxEventService.record("catalog-events", productId, "ProductLikedEvent", "{\"productId\":\"" + productId + "\"}");

        // act
        outboxPublisher.publishPendingEvents();

        // assert
        OutboxEventModel event = outboxEventJpaRepository.findAll().get(0);
        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
    }
}
