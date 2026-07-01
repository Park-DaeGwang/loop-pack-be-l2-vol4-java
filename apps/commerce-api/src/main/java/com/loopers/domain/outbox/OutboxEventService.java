package com.loopers.domain.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    /** 비즈니스 트랜잭션과 같은 트랜잭션에서 호출되어야 한다 — 그래야 원자성이 보장된다. */
    public void record(String topic, String eventKey, String eventType, String payload) {
        outboxEventRepository.save(new OutboxEventModel(topic, eventKey, eventType, payload));
    }
}
