package com.paytrix.cipherbank.infrastructure.config.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer Configuration
 * Configures consumers for receiving events from Kafka
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group-id}")
    private String groupId;

    @Value("${kafka.consumer.concurrency:3}")
    private int concurrency;

    /**
     * Consumer Factory Configuration
     * Key: Long (statement ID)
     * Value: JSON (event objects)
     */
    @Bean
    public ConsumerFactory<Long, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();

        // Bootstrap servers
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Consumer group
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // Deserializers with error handling
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, LongDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // JSON deserialization settings
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.paytrix.cipherbank.*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.paytrix.cipherbank.infrastructure.adapter.in.kafka.event.StatementProcessedEvent");

        // Consumer behavior
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);  // Manual commit
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");  // Start from beginning
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);  // Batch size

        // Session settings
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);  // 30 seconds
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);  // 10 seconds
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);  // 5 minutes

        // Isolation level (read only committed messages)
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka Listener Container Factory
     * Configures how consumers listen to topics
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Long, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<Long, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // Concurrency (number of consumer threads)
        factory.setConcurrency(concurrency);

        // Manual acknowledgment mode
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // Error handling (we'll handle errors in the consumer)
        factory.setCommonErrorHandler(null);  // Custom error handling in consumer

        return factory;
    }
}