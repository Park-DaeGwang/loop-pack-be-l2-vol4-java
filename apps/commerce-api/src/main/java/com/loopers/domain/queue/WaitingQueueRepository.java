package com.loopers.domain.queue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitingQueueRepository {
    boolean enter(UUID userId, long score);
    Optional<Long> findRank(UUID userId);
    long size();
    List<UUID> popBatch(int count);
    void saveToken(UUID userId, String token, Duration ttl);
    Optional<String> findToken(UUID userId);
    void removeToken(UUID userId);
}
