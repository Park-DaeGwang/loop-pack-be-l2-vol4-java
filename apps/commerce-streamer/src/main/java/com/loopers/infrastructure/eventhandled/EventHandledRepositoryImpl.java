package com.loopers.infrastructure.eventhandled;

import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Override
    public boolean existsByEventId(UUID eventId) {
        return eventHandledJpaRepository.existsById(eventId);
    }

    @Override
    public void save(EventHandledModel eventHandled) {
        eventHandledJpaRepository.save(eventHandled);
    }
}
