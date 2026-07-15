package com.loopers.domain.queue;

import java.util.Optional;

public record QueuePositionResult(
    long position,
    long estimatedWaitSeconds,
    Optional<String> token,
    long recommendedPollingIntervalSeconds
) {
    public static QueuePositionResult waiting(long position, long estimatedWaitSeconds) {
        return new QueuePositionResult(position, estimatedWaitSeconds, Optional.empty(), recommendedPollingInterval(position));
    }

    public static QueuePositionResult admitted(String token) {
        return new QueuePositionResult(0L, 0L, Optional.of(token), 0L);
    }

    private static long recommendedPollingInterval(long position) {
        if (position <= 160)  return 1L;
        if (position <= 1600) return 3L;
        return 5L;
    }
}
