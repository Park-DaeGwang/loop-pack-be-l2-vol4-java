package com.loopers.infrastructure.eventhandled;

import com.loopers.domain.eventhandled.EventHandledModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledModel, UUID> {
}
