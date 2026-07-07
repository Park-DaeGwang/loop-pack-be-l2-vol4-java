package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @Override
    public OutboxEventModel save(OutboxEventModel event) {
        return outboxEventJpaRepository.save(event);
    }

    @Override
    public List<OutboxEventModel> findUnpublished(int limit) {
        return outboxEventJpaRepository.findByPublishedFalseOrderByCreatedAtAsc(PageRequest.of(0, limit));
    }

    @Override
    public Optional<OutboxEventModel> findById(UUID id) {
        return outboxEventJpaRepository.findById(id);
    }
}
