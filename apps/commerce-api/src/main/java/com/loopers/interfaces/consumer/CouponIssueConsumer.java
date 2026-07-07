package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.coupon.CouponIssueProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueConsumer {

    private static final String EVENT_TYPE_HEADER = "eventType";

    private final CouponIssueProcessingService couponIssueProcessingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "coupon-issue-requests", groupId = "coupon-issue-consumer", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<Object, Object> record = records.get(i);
            try {
                process(record);
            } catch (Exception e) {
                throw new BatchListenerFailedException(
                    "coupon-issue-requests 메시지 처리 실패 — offset=" + record.offset(), e, i);
            }
        }
        acknowledgment.acknowledge();
    }

    private void process(ConsumerRecord<Object, Object> record) throws Exception {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!"CouponIssueRequestedEvent".equals(eventType)) {
            log.warn("알 수 없는 eventType — {}", eventType);
            return;
        }
        CouponIssuePayload payload = objectMapper.readValue((String) record.value(), CouponIssuePayload.class);
        couponIssueProcessingService.process(payload.eventId(), payload.requestId(), payload.userId(), payload.templateId());
    }

    private String headerValue(ConsumerRecord<Object, Object> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CouponIssuePayload(UUID eventId, UUID requestId, UUID userId, UUID templateId) {
    }
}
