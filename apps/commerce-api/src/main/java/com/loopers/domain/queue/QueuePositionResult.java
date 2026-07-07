package com.loopers.domain.queue;

import java.util.Optional;

public record QueuePositionResult(
    long position,
    long estimatedWaitSeconds,
    Optional<String> token
) {
    public static QueuePositionResult waiting(long position, long estimatedWaitSeconds) {
        return new QueuePositionResult(position, estimatedWaitSeconds, Optional.empty());
    }

    public static QueuePositionResult admitted(String token) {
        return new QueuePositionResult(0L, 0L, Optional.of(token));
    }
}
