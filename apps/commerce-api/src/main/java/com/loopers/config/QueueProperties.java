package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("queue")
public record QueueProperties(long tokenTtlSeconds, int rateLimitPerSecond, int batchSize) {}
