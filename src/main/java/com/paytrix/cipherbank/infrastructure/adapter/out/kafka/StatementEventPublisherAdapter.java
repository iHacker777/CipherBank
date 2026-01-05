package com.paytrix.cipherbank.infrastructure.adapter.out.kafka;

import com.paytrix.cipherbank.application.port.out.messaging.StatementEventPublisher;
import com.paytrix.cipherbank.infrastructure.adapter.out.kafka.event.StatementUploadedEvent;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.BankStatement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Kafka implementation of StatementEventPublisher port
 * Publishes statement events to Kafka topics
 *
 * Hexagonal Architecture:
 * - This is an ADAPTER in the infrastructure layer
 * - Implements the PORT defined in application layer
 * - Domain/Application layers don't know about Kafka
 */
@Slf4j
@Component
public class StatementEventPublisherAdapter implements StatementEventPublisher {

    private final KafkaTemplate<Long, Object> kafkaTemplate;

    @Value("${kafka.topics.statements-uploaded.name}")
    private String statementsUploadedTopic;

    @Value("${kafka.producer.retry-attempts:3}")
    private int retryAttempts;

    public StatementEventPublisherAdapter(KafkaTemplate<Long, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishStatementUploaded(BankStatement statement) {
        log.info("Publishing statement uploaded event - ID: {}, AccountNo: {}, Amount: {}",
                statement.getId(), statement.getAccountNo(), statement.getAmount());

        try {
            // Create event from statement
            StatementUploadedEvent event = StatementUploadedEvent.from(
                    statement.getId(),
                    statement.getAccountNo(),
                    statement.getAmount(),
                    statement.getOrderId(),
                    statement.getUtr(),
                    statement.getTransactionDateTime()
            );

            // Publish to Kafka
            // Key: accountNo (ensures ordering per account)
            // Value: event object
            CompletableFuture<SendResult<Long, Object>> future =
                    kafkaTemplate.send(statementsUploadedTopic, statement.getAccountNo(), event);

            // Wait for confirmation (with timeout)
            SendResult<Long, Object> result = future.get(5, TimeUnit.SECONDS);

            log.info("Successfully published statement event - ID: {}, Partition: {}, Offset: {}",
                    statement.getId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

        } catch (Exception e) {
            log.error("Failed to publish statement uploaded event - ID: {}, Error: {}",
                    statement.getId(), e.getMessage(), e);

            // Don't throw exception - statement is already saved to DB
            // A scheduled job can republish failed events later
            // This prevents statement upload from failing due to Kafka issues
        }
    }

    @Override
    public void publishStatementsUploaded(Iterable<BankStatement> statements) {
        log.info("Publishing batch of statement uploaded events");

        int success = 0;
        int failed = 0;

        for (BankStatement statement : statements) {
            try {
                publishStatementUploaded(statement);
                success++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to publish statement {} in batch", statement.getId(), e);
            }
        }

        log.info("Batch publish completed - Success: {}, Failed: {}", success, failed);
    }
}