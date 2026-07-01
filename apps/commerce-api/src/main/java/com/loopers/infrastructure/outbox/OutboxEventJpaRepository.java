package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEventModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventModel, UUID> {
}
