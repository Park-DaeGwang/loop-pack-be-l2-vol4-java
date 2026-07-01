package com.loopers.domain.outbox;

public interface OutboxEventRepository {

    OutboxEventModel save(OutboxEventModel event);
}
