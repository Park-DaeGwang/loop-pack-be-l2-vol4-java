package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private static final long THROUGHPUT_PER_SECOND = 160L;

    private final WaitingQueueRepository waitingQueueRepository;
    private final QueueDynamicConfig queueDynamicConfig;

    public long enter(UUID userId) {
        long score = System.currentTimeMillis();
        waitingQueueRepository.enter(userId, score);
        return waitingQueueRepository.findRank(userId)
            .map(rank -> rank + 1)
            .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR));
    }

    public QueuePositionResult getPosition(UUID userId) {
        return waitingQueueRepository.findToken(userId)
            .map(QueuePositionResult::admitted)
            .orElseGet(() -> {
                long rank = waitingQueueRepository.findRank(userId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND));
                long position = rank + 1;
                long estimatedWaitSeconds = position / THROUGHPUT_PER_SECOND;
                return QueuePositionResult.waiting(position, estimatedWaitSeconds);
            });
    }

    public long getSize() {
        return waitingQueueRepository.size();
    }

    public void issueToken(UUID userId) {
        String token = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(queueDynamicConfig.tokenTtlSeconds());
        waitingQueueRepository.saveToken(userId, token, ttl);
    }

    public List<UUID> popBatch(int count) {
        return waitingQueueRepository.popBatch(count);
    }

    public Optional<String> findToken(UUID userId) {
        return waitingQueueRepository.findToken(userId);
    }

    public void removeToken(UUID userId) {
        waitingQueueRepository.removeToken(userId);
    }
}
