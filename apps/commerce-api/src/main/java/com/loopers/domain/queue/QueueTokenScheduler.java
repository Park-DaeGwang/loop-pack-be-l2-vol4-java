package com.loopers.domain.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QueueTokenScheduler {

    private final WaitingQueueService waitingQueueService;
    private final QueueDynamicConfig queueDynamicConfig;

    @Scheduled(fixedDelay = 1000)
    public void issueTokens() {
        List<UUID> userIds = waitingQueueService.popBatch(queueDynamicConfig.batchSize());
        userIds.forEach(waitingQueueService::issueToken);
    }
}
