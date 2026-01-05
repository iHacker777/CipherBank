package com.paytrix.cipherbank.infrastructure.adapter.in.kafka;

import com.paytrix.cipherbank.application.port.out.business.BankStatementRepositoryPort;
import com.paytrix.cipherbank.infrastructure.adapter.in.kafka.event.StatementProcessedEvent;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.BankStatement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for statement processed events from Payment Gateway
 *
 * Hexagonal Architecture:
 * - This is an ADAPTER in the infrastructure layer (input adapter)
 * - Receives events from Kafka and updates domain entities
 * - Uses repository PORT to update database
 */
@Slf4j
@Component
public class StatementResponseConsumer {

    private final BankStatementRepositoryPort statementRepository;

    public StatementResponseConsumer(BankStatementRepositoryPort statementRepository) {
        this.statementRepository = statementRepository;
    }

    /**
     * Consume statement processed events from Gateway
     *
     * Topic: payment.statements.processed
     * Group: cipherbank-statement-response-consumer
     *
     * @param event The statement processed event from Gateway
     * @param partition The Kafka partition
     * @param offset The Kafka offset
     * @param acknowledgment Manual acknowledgment object
     */
    @KafkaListener(
            topics = "${kafka.topics.statements-processed.name}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeStatementProcessed(
            @Payload StatementProcessedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("Received statement processed event - MessageId: {}, StatementId: {}, Partition: {}, Offset: {}",
                event.getMessageId(),
                event.getPayload().getStatementId(),
                partition,
                offset);

        try {
            // Process the event
            processStatementResponse(event);

            // Acknowledge message (commit offset)
            acknowledgment.acknowledge();

            log.info("Successfully processed statement response - StatementId: {}, GatewayTxnId: {}, Status: {}",
                    event.getPayload().getStatementId(),
                    event.getPayload().getGatewayTransactionId(),
                    event.getPayload().getApprovalStatus());

        } catch (Exception e) {
            log.error("Error processing statement response - StatementId: {}, Error: {}",
                    event.getPayload().getStatementId(),
                    e.getMessage(),
                    e);

            // Don't acknowledge - Kafka will retry
            throw e;
        }
    }

    /**
     * Process the statement response and update database
     *
     * @param event The statement processed event
     */
    private void processStatementResponse(StatementProcessedEvent event) {
        Long statementId = event.getPayload().getStatementId();
        Long gatewayTxnId = event.getPayload().getGatewayTransactionId();

        // Find statement in database
        BankStatement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new RuntimeException(
                        "Statement not found: " + statementId));

        // IDEMPOTENCY CHECK: If already processed, skip
        if (statement.isProcessed()) {
            log.warn("Statement {} already processed, skipping update", statementId);
            return;
        }

        // Update statement with Gateway response
        statement.setGatewayTransactionId(gatewayTxnId);
        statement.setApprovalStatus(event.getApprovalStatusEnum());
        statement.setProcessed(true);

        // Save to database
        statementRepository.save(statement);

        log.debug("Updated statement - ID: {}, GatewayTxnId: {}, ApprovalStatus: {}, Processed: true",
                statementId, gatewayTxnId, event.getPayload().getApprovalStatus());
    }
}