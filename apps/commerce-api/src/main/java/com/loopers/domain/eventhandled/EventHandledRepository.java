package com.loopers.domain.eventhandled;

import java.util.UUID;

public interface EventHandledRepository {
    boolean existsByEventId(UUID eventId);
    void save(EventHandledModel eventHandled);
}
