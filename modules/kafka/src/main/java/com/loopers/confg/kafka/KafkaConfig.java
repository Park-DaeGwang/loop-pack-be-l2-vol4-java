package com.loopers.confg.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaConfig {
    public static final String BATCH_LISTENER = "BATCH_LISTENER_DEFAULT";

    public static final int MAX_POLLING_SIZE = 3000; // read 3000 msg
    public static final int FETCH_MIN_BYTES = (1024 * 1024); // 1mb
    public static final int FETCH_MAX_WAIT_MS = 5 * 1000; // broker waiting time = 5s
    public static final int SESSION_TIMEOUT_MS = 60 * 1000; // session timeout = 1m
    public static final int HEARTBEAT_INTERVAL_MS = 20 * 1000; // heartbeat interval = 20s ( 1/3 of session_timeout )
    public static final int MAX_POLL_INTERVAL_MS = 2 * 60 * 1000; // max poll interval = 2m

    // 토픽 파티션 수 — concurrency=3과 맞춤. key 기반 라우팅(productId/orderId/templateId)으로
    // 개별 엔티티 순서는 파티션 수와 무관하게 보장되므로, 쿠폰 토픽도 동일하게 3개로 둔다.
    private static final int DEFAULT_PARTITIONS = 3;
    private static final short DEFAULT_REPLICATION_FACTOR = 1; // 로컬 단일 브로커

    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name("catalog-events")
            .partitions(DEFAULT_PARTITIONS)
            .replicas(DEFAULT_REPLICATION_FACTOR)
            .build();
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
            .partitions(DEFAULT_PARTITIONS)
            .replicas(DEFAULT_REPLICATION_FACTOR)
            .build();
    }

    @Bean
    public NewTopic couponIssueRequestsTopic() {
        return TopicBuilder.name("coupon-issue-requests")
            .partitions(DEFAULT_PARTITIONS)
            .replicas(DEFAULT_REPLICATION_FACTOR)
            .build();
    }

    // DLQ 토픽 — DeadLetterPublishingRecoverer 기본 네이밍은 "<원본토픽>-dlt"(소문자, 하이픈).
    // 원본 레코드의 파티션 번호를 그대로 써서 발행하므로, 원본과 파티션 수도 맞춰야 한다.
    @Bean
    public NewTopic catalogEventsDlt() {
        return TopicBuilder.name("catalog-events-dlt")
            .partitions(DEFAULT_PARTITIONS)
            .replicas(DEFAULT_REPLICATION_FACTOR)
            .build();
    }

    @Bean
    public NewTopic orderEventsDlt() {
        return TopicBuilder.name("order-events-dlt")
            .partitions(DEFAULT_PARTITIONS)
            .replicas(DEFAULT_REPLICATION_FACTOR)
            .build();
    }

    @Bean
    public NewTopic couponIssueRequestsDlt() {
        return TopicBuilder.name("coupon-issue-requests-dlt")
            .partitions(DEFAULT_PARTITIONS)
            .replicas(DEFAULT_REPLICATION_FACTOR)
            .build();
    }

    @Bean
    public ProducerFactory<Object, Object> producerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public ConsumerFactory<Object, Object> consumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<Object, Object> kafkaTemplate(ProducerFactory<Object, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ByteArrayJsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new ByteArrayJsonMessageConverter(objectMapper);
    }

    // 재시도 소진 시 원본 레코드를 <토픽명>.DLT로 그대로 발행 (기본 네이밍 컨벤션)
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<Object, Object> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate);
    }

    // 실패 레코드 재시도 3회, backoff 500ms -> 1000ms -> 2000ms(2배씩 증가). 소진되면 recoverer가 DLQ로 발행.
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean(name = BATCH_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> defaultBatchListenerContainerFactory(
            KafkaProperties kafkaProperties,
            ByteArrayJsonMessageConverter converter,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        Map<String, Object> consumerConfig = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerConfig.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_POLLING_SIZE);
        consumerConfig.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, FETCH_MIN_BYTES);
        consumerConfig.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, FETCH_MAX_WAIT_MS);
        consumerConfig.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, SESSION_TIMEOUT_MS);
        consumerConfig.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, HEARTBEAT_INTERVAL_MS);
        consumerConfig.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, MAX_POLL_INTERVAL_MS);

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerConfig));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL); // 수동 커밋
        factory.setBatchMessageConverter(new BatchMessagingMessageConverter(converter));
        factory.setConcurrency(3);
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
