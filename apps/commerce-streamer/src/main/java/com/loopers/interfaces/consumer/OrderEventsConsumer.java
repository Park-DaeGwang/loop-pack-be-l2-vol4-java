package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.metrics.ProductMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
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
public class OrderEventsConsumer {

    private static final String EVENT_TYPE_HEADER = "eventType";

    private final ProductMetricsService productMetricsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                process(record);
            } catch (Exception e) {
                log.error("order-events 메시지 처리 실패 — offset={}", record.offset(), e);
            }
        }
        acknowledgment.acknowledge();
    }

    private void process(ConsumerRecord<Object, Object> record) throws Exception {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!"PaymentConfirmedEvent".equals(eventType)) {
            log.warn("알 수 없는 eventType — {}", eventType);
            return;
        }
        OrderEventPayload payload = objectMapper.readValue((String) record.value(), OrderEventPayload.class);
        ZonedDateTime eventTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(record.timestamp()), ZoneId.systemDefault());

        productMetricsService.applyIfNotHandled(payload.eventId(), () -> {
            for (OrderItemPayload item : payload.items()) {
                productMetricsService.applyToProduct(item.productId(), eventTime, (m, t) -> m.incrementSales(t));
            }
        });
    }

    private String headerValue(ConsumerRecord<Object, Object> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderEventPayload(UUID eventId, List<OrderItemPayload> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderItemPayload(UUID productId, int quantity) {
    }
}
