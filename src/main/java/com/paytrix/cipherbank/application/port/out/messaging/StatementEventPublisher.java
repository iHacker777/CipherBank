package com.paytrix.cipherbank.application.port.out.messaging;

import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.BankStatement;

/**
 * Port for publishing statement events to message broker
 * This is a hexagonal architecture port (interface)
 * Implementation will be in infrastructure layer (Kafka adapter)
 */
public interface StatementEventPublisher {

    /**
     * Publish statement uploaded event
     * Called after statement is successfully saved to database
     *
     * @param statement The bank statement that was uploaded
     * @throws MessagingException if publishing fails
     */
    void publishStatementUploaded(BankStatement statement);

    /**
     * Publish multiple statements uploaded (batch)
     * Used when uploading multiple statements from a file
     *
     * @param statements List of bank statements
     */
    void publishStatementsUploaded(Iterable<BankStatement> statements);
}