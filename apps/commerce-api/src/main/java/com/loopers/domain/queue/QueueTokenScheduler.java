package com.loopers.domain.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QueueTokenScheduler {

    private static final int BATCH_SIZE = 160;

    private final WaitingQueueService waitingQueueService;

    @Scheduled(fixedDelay = 1000)
    public void issueTokens() {
        List<UUID> userIds = waitingQueueService.popBatch(BATCH_SIZE);
        userIds.forEach(waitingQueueService::issueToken);
    }
}
