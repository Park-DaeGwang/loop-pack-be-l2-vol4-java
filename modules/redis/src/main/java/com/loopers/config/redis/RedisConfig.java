package com.loopers.config.redis;


import io.lettuce.core.ReadFrom;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;
import java.util.function.Consumer;

@Configuration
@EnableConfigurationProperties({RedisProperties.class, RedisCacheProperties.class})
public class RedisConfig{
    private static final String CONNECTION_MASTER = "redisConnectionMaster";
    public static final String REDIS_TEMPLATE_MASTER = "redisTemplateMaster";
    public static final String REDIS_TEMPLATE_CACHE = "redisTemplateCache";

    private final RedisProperties redisProperties;
    private final RedisCacheProperties redisCacheProperties;

    public RedisConfig(RedisProperties redisProperties, RedisCacheProperties redisCacheProperties) {
        this.redisProperties = redisProperties;
        this.redisCacheProperties = redisCacheProperties;
    }

    @Primary
    @Bean
    public LettuceConnectionFactory defaultRedisConnectionFactory() {
        int database = redisProperties.database();
        RedisNodeInfo master = redisProperties.master();
        List<RedisNodeInfo> replicas = redisProperties.replicas();
        return lettuceConnectionFactory(
                database, master, replicas,
                b -> b.readFrom(ReadFrom.REPLICA_PREFERRED)
        );
    }

    @Qualifier(CONNECTION_MASTER)
    @Bean
    public LettuceConnectionFactory masterRedisConnectionFactory() {
        int database = redisProperties.database();
        RedisNodeInfo master = redisProperties.master();
        List<RedisNodeInfo> replicas = redisProperties.replicas();
        return lettuceConnectionFactory(
                database, master, replicas,
                b -> b.readFrom(ReadFrom.MASTER)
        );
    }

    @Primary
    @Bean
    public RedisTemplate<String, String> defaultRedisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        return defaultRedisTemplate(redisTemplate, lettuceConnectionFactory);
    }

    @Qualifier(REDIS_TEMPLATE_MASTER)
    @Bean
    public RedisTemplate<String, String> masterRedisTemplate(
            @Qualifier(CONNECTION_MASTER) LettuceConnectionFactory lettuceConnectionFactory
    ) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        return defaultRedisTemplate(redisTemplate, lettuceConnectionFactory);
    }


    private LettuceConnectionFactory lettuceConnectionFactory(
            int database,
            RedisNodeInfo master,
            List<RedisNodeInfo> replicas,
            Consumer<LettuceClientConfiguration.LettuceClientConfigurationBuilder> customizer
    ){
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder();
        if(customizer != null) customizer.accept(builder);
        LettuceClientConfiguration clientConfig = builder.build();
        RedisStaticMasterReplicaConfiguration masterReplicaConfig = new RedisStaticMasterReplicaConfiguration(master.host(), master.port());
        masterReplicaConfig.setDatabase(database);
        for(RedisNodeInfo r : replicas){
            masterReplicaConfig.addNode(r.host(), r.port());
        }
        return new LettuceConnectionFactory(masterReplicaConfig, clientConfig);
    }

    @Qualifier(REDIS_TEMPLATE_CACHE)
    @Bean
    public RedisTemplate<String, String> cacheRedisTemplate() {
        RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration(
            redisCacheProperties.host(), redisCacheProperties.port()
        );
        standaloneConfig.setDatabase(redisCacheProperties.database());
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(standaloneConfig);
        connectionFactory.afterPropertiesSet();
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        return defaultRedisTemplate(redisTemplate, connectionFactory);
    }

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://" + redisProperties.master().host() + ":" + redisProperties.master().port())
            .setDatabase(redisProperties.database());
        return Redisson.create(config);
    }

    private <K,V> RedisTemplate<K,V> defaultRedisTemplate(
            RedisTemplate<K,V> template,
            LettuceConnectionFactory connectionFactory
    ){
        StringRedisSerializer s = new StringRedisSerializer();
        template.setKeySerializer(s);
        template.setValueSerializer(s);
        template.setHashKeySerializer(s);
        template.setHashValueSerializer(s);
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}
