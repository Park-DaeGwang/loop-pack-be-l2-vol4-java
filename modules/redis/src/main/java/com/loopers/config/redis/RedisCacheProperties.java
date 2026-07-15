package com.loopers.config.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value = "datasource.redis-cache")
public record RedisCacheProperties(String host, int port, int database) {}
