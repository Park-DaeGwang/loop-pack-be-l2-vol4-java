package com.loopers.domain.outbox;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository {

    OutboxEventModel save(OutboxEventModel event);

    List<OutboxEventModel> findUnpublished(int limit);

    Optional<OutboxEventModel> findById(UUID id);
}
