package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    // 100ms마다 ~18명 발급 = 초당 180명. waiting-queue-systems.md 처리량 설계 기준 참고.
    private static final long THROUGHPUT_PER_SECOND = 180L;
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    private final WaitingQueueRepository waitingQueueRepository;

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
        waitingQueueRepository.saveToken(userId, token, TOKEN_TTL);
    }

    public List<UUID> popBatch(int count) {
        return waitingQueueRepository.popBatch(count);
    }

    public void removeToken(UUID userId) {
        waitingQueueRepository.removeToken(userId);
    }
}
