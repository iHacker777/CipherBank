package com.paytrix.cipherbank.infrastructure.config.kafka;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Topic Configuration
 * Auto-creates topics on application startup if they don't exist
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.topic.bank-statements-uploaded.name}")
    private String statementsUploadedTopic;

    @Value("${kafka.topics.bank-statements-uploaded.partitions}")
    private int statementsUploadedPartitions;

    @Value("${kafka.topic.bank-statements-uploaded.replication-factor}")
    private short statementsUploadedReplication;

    @Value("${kafka.topic.payment-statements-processed.name}")
    private String statementsProcessedTopic;

    @Value("${kafka.topic.payment-statements-processed.partitions}")
    private int statementsProcessedPartitions;

    @Value("${kafka.topic.payment-statements-processed.replication-factor}")
    private short statementsProcessedReplication;

    @Value("${kafka.topic.bank-statements-dlq.name}")
    private String statementsDlqTopic;

    @Value("${kafka.topics.bank-statements-dlq.partitions}")
    private int statementsDlqPartitions;

    @Value("${kafka.topics.bank-statements-dlq.replication-factor}")
    private short statementsDlqReplication;

    /**
     * Kafka Admin configuration
     * Needed for topic creation
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    /**
     * Topic: bank.statements.uploaded
     * Purpose: CipherBank publishes new statements for Gateway to process
     */
    @Bean
    public NewTopic statementsUploadedTopic() {
        return TopicBuilder.name(statementsUploadedTopic)
                .partitions(statementsUploadedPartitions)
                .replicas(statementsUploadedReplication)
                .config("compression.type", "snappy")
                .config("retention.ms", "604800000")  // 7 days
                .config("cleanup.policy", "delete")
                .build();
    }

    /**
     * Topic: payment.statements.processed
     * Purpose: Gateway publishes processing results back to CipherBank
     */
    @Bean
    public NewTopic statementsProcessedTopic() {
        return TopicBuilder.name(statementsProcessedTopic)
                .partitions(statementsProcessedPartitions)
                .replicas(statementsProcessedReplication)
                .config("compression.type", "snappy")
                .config("retention.ms", "259200000")  // 3 days
                .config("cleanup.policy", "delete")
                .build();
    }

    /**
     * Topic: bank.statements.uploaded.dlq
     * Purpose: Dead Letter Queue for failed message processing
     */
    @Bean
    public NewTopic statementsDlqTopic() {
        return TopicBuilder.name(statementsDlqTopic)
                .partitions(statementsDlqPartitions)
                .replicas(statementsDlqReplication)
                .config("retention.ms", "2592000000")  // 30 days
                .config("cleanup.policy", "delete")
                .build();
    }
}