package com.loopers.testcontainers;

import com.redis.testcontainers.RedisContainer;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.utility.DockerImageName;

@Configuration
public class RedisTestContainersConfig {
    private static final RedisContainer redisContainer = new RedisContainer(DockerImageName.parse("redis:latest"));

    static {
        redisContainer.start();
    }

    public RedisTestContainersConfig() {
        System.setProperty("datasource.redis.database", "0");
        System.setProperty("datasource.redis.master.host", redisContainer.getHost());
        System.setProperty("datasource.redis.master.port", String.valueOf(redisContainer.getFirstMappedPort()));
        System.setProperty("datasource.redis.replicas[0].host", redisContainer.getHost());
        System.setProperty("datasource.redis.replicas[0].port", String.valueOf(redisContainer.getFirstMappedPort()));
        // 테스트에서는 캐시 Redis를 동일 컨테이너로 연결
        System.setProperty("datasource.redis-cache.host", redisContainer.getHost());
        System.setProperty("datasource.redis-cache.port", String.valueOf(redisContainer.getFirstMappedPort()));
        System.setProperty("datasource.redis-cache.database", "1");
    }
}
