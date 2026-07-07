package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogEventsConsumer {

    private static final String EVENT_TYPE_HEADER = "eventType";

    private final ProductMetricsService productMetricsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "catalog-events", groupId = "catalog-metrics-consumer", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<Object, Object> record = records.get(i);
            try {
                process(record);
            } catch (Exception e) {
                throw new BatchListenerFailedException(
                    "catalog-events 메시지 처리 실패 — offset=" + record.offset(), e, i);
            }
        }
        acknowledgment.acknowledge();
    }

    private void process(ConsumerRecord<Object, Object> record) throws Exception {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (eventType == null) {
            log.warn("eventType 헤더 없음 — offset={}", record.offset());
            return;
        }
        CatalogEventPayload payload = objectMapper.readValue((String) record.value(), CatalogEventPayload.class);
        ZonedDateTime eventTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(record.timestamp()), ZoneId.systemDefault());

        switch (eventType) {
            case "ProductLikedEvent" -> productMetricsService.applyIfNotHandled(payload.eventId(), () ->
                productMetricsService.applyToProduct(payload.productId(), eventTime, (m, t) -> m.incrementLike(t)));
            case "ProductUnlikedEvent" -> productMetricsService.applyIfNotHandled(payload.eventId(), () ->
                productMetricsService.applyToProduct(payload.productId(), eventTime, (m, t) -> m.decrementLike(t)));
            case "ProductViewedEvent" -> productMetricsService.applyIfNotHandled(payload.eventId(), () ->
                productMetricsService.applyToProductUnordered(payload.productId(), ProductMetricsModel::incrementView));
            default -> log.warn("알 수 없는 eventType — {}", eventType);
        }
    }

    private String headerValue(ConsumerRecord<Object, Object> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CatalogEventPayload(UUID eventId, UUID productId) {
    }
}
