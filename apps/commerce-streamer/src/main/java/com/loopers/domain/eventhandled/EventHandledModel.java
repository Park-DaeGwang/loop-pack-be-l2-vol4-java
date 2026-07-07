package com.loopers.domain.eventhandled;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/** BaseEntity를 상속하지 않는다 — 자동 생성 id가 아니라 eventId 자체가 PK인 게 자연스러운 케이스라 의도적으로 이탈. */
@Entity
@Table(name = "event_handled")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandledModel {

    @Id
    @Column(name = "event_id", columnDefinition = "BINARY(16)")
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private ZonedDateTime processedAt;

    public EventHandledModel(UUID eventId) {
        this.eventId = eventId;
        this.processedAt = ZonedDateTime.now();
    }
}
