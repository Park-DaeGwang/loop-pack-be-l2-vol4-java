package com.loopers.domain.queue;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
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
            log.warn("QueueTokenScheduler skipped — redisQueue circuit breaker is OPEN");
            return;
        }
        List<UUID> userIds = waitingQueueService.popBatch(queueDynamicConfig.batchSize());
        int issued = 0;
        for (UUID userId : userIds) {
            try {
                waitingQueueService.issueToken(userId);
                issued++;
            } catch (Exception e) {
                log.error("QueueTokenScheduler failed to issue token for userId={}", userId, e);
            }
        }
        log.info("QueueTokenScheduler issued {}/{} tokens", issued, userIds.size());
    }
}
