package com.loopers.domain.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final int BATCH_SIZE = 100;
    private static final String EVENT_TYPE_HEADER = "eventType";

    private final OutboxEventService outboxEventService;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        List<OutboxEventModel> events = outboxEventService.findUnpublished(BATCH_SIZE);
        for (OutboxEventModel event : events) {
            publish(event);
        }
    }

    // Kafka 전송(네트워크 I/O)은 트랜잭션 밖에서 수행 — DB 트랜잭션을 오래 붙잡지 않기 위함.
    // 발행 성공 확인 후에만 별도 트랜잭션(markPublished)으로 상태를 갱신한다.
    private void publish(OutboxEventModel event) {
        try {
            ProducerRecord<Object, Object> record = new ProducerRecord<>(event.getTopic(), null, event.getEventKey(), event.getPayload());
            record.headers().add(EVENT_TYPE_HEADER, event.getEventType().getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record).get();
            outboxEventService.markPublished(event.getId());
        } catch (Exception e) {
            log.warn("Outbox 이벤트 발행 실패 — id={}, topic={}, eventType={}", event.getId(), event.getTopic(), event.getEventType(), e);
        }
    }
}
