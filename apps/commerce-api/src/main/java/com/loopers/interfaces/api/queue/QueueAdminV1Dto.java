package com.loopers.interfaces.api.queue;

public class QueueAdminV1Dto {

    public record ConfigResponse(long tokenTtlSeconds, int rateLimitPerSecond, int batchSize) {}

    public record ConfigUpdateRequest(Long tokenTtlSeconds, Integer rateLimitPerSecond, Integer batchSize) {}
}
