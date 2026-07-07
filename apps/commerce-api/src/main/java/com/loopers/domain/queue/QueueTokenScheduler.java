package com.loopers.domain.queue;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QueueTokenScheduler {

    private static final String CB_NAME = "redisQueue";

    private final WaitingQueueService waitingQueueService;
    private final QueueDynamicConfig queueDynamicConfig;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Scheduled(fixedDelay = 1000)
    public void issueTokens() {
        if (circuitBreakerRegistry.circuitBreaker(CB_NAME).getState() == CircuitBreaker.State.OPEN) {
            return;
        }
        List<UUID> userIds = waitingQueueService.popBatch(queueDynamicConfig.batchSize());
        userIds.forEach(waitingQueueService::issueToken);
    }
}
