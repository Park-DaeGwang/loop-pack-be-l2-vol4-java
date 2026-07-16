package com.loopers.interfaces.consumer;

import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.Consumer;
import java.time.LocalDate;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
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
    private ConsumerFactory<Object, Object> consumerFactory;

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
        // 파티션 0 고정 — DLQ 테스트(파티션 1)와 같은 파티션에 우연히 몰려 서로 블로킹하는 것 방지
        ProducerRecord<Object, Object> record = new ProducerRecord<>("catalog-events", 0, productId.toString(), payload);
        record.headers().add("eventType", "ProductLikedEvent".getBytes(StandardCharsets.UTF_8));

        // act
        kafkaTemplate.send(record).get();

        // assert — 컨슈머 처리는 비동기라 폴링으로 확인
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            var metrics = productMetricsRepository.findByProductIdAndDate(productId, LocalDate.now());
            if (metrics.isPresent() && metrics.get().getLikeCount() == 1) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(productMetricsRepository.findByProductIdAndDate(productId, LocalDate.now()))
            .hasValueSatisfying(m -> assertThat(m.getLikeCount()).isEqualTo(1));
    }

    @DisplayName("메시지 처리가 재시도 후에도 계속 실패하면, catalog-events-dlt로 원본 레코드가 발행된다.")
    @Test
    void publishesToDlt_whenProcessingFailsRepeatedly() throws ExecutionException, InterruptedException {
        // arrange — eventType 헤더는 있지만 payload가 깨진 JSON이라 역직렬화가 항상 실패한다
        String key = UUID.randomUUID().toString();
        String brokenPayload = "{ not valid json";
        // 파티션 1 고정 — 위 정상 처리 테스트(파티션 0)와 겹치지 않게 해서, 이 테스트가 유발하는
        // 장시간 재시도가 다른 테스트의 파티션을 막아 실패시키는 것을 방지
        ProducerRecord<Object, Object> record = new ProducerRecord<>("catalog-events", 1, key, brokenPayload);
        record.headers().add("eventType", "ProductLikedEvent".getBytes(StandardCharsets.UTF_8));

        try (Consumer<Object, Object> dltConsumer = consumerFactory.createConsumer("dlt-test-group", "dlt-test")) {
            dltConsumer.subscribe(List.of("catalog-events-dlt"));

            // act
            kafkaTemplate.send(record).get();

            // assert — 재시도(500ms+1000ms+2000ms) 소진 후 DLT에서 원본 키/페이로드를 그대로 확인
            // fetch.min.bytes=1MB인데 테스트 메시지는 그 기준을 못 채워서 poll마다 fetch.max.wait.ms(5s)를
            // 다 채우고 돌아옴 - 재시도 간격이 애플리케이션 backoff(500ms~2s)가 아니라 fetch 대기(5s)에 지배됨
            long deadline = System.currentTimeMillis() + 60000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<Object, Object> polled = dltConsumer.poll(Duration.ofSeconds(1));
                for (var dltRecord : polled) {
                    if (key.equals(dltRecord.key())) {
                        assertThat(dltRecord.value()).isEqualTo(brokenPayload);
                        return;
                    }
                }
            }
            throw new AssertionError("제한 시간 내에 DLT에서 레코드를 확인하지 못했다 — key=" + key);
        }
    }
}
