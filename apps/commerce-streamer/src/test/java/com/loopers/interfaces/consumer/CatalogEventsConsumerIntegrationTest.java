package com.loopers.interfaces.consumer;

import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CatalogEventsConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private ProductMetricsRepository productMetricsRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("catalog-events로 ProductLikedEvent가 발행되면, product_metrics의 likeCount가 반영된다.")
    @Test
    void appliesLikeCount_whenProductLikedEventConsumed() throws ExecutionException, InterruptedException {
        // arrange
        UUID productId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"productId\":\"" + productId + "\"}";
        ProducerRecord<Object, Object> record = new ProducerRecord<>("catalog-events", null, productId.toString(), payload);
        record.headers().add("eventType", "ProductLikedEvent".getBytes(StandardCharsets.UTF_8));

        // act
        kafkaTemplate.send(record).get();

        // assert — 컨슈머 처리는 비동기라 폴링으로 확인
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            var metrics = productMetricsRepository.findByProductId(productId);
            if (metrics.isPresent() && metrics.get().getLikeCount() == 1) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(productMetricsRepository.findByProductId(productId))
            .hasValueSatisfying(m -> assertThat(m.getLikeCount()).isEqualTo(1));
    }
}
