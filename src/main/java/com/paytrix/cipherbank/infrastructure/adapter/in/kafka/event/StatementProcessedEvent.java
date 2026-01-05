package com.paytrix.cipherbank.infrastructure.adapter.in.kafka.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published by Payment Gateway when statement is processed
 * Published to: payment.statements.processed
 * Consumer: CipherBank
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementProcessedEvent {

    /**
     * Unique message ID (for idempotency and tracing)
     */
    private String messageId;

    /**
     * Timestamp when message was created
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant timestamp;

    /**
     * Actual payload with processing result
     */
    private ProcessedPayload payload;

    /**
     * Processing result payload
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProcessedPayload {
        /**
         * Original statement ID from CipherBank (bank_statements.id)
         * This is the primary key that CipherBank will use to update the record
         */
        private Long statementId;

        /**
         * Gateway's transaction ID
         * Will be stored in bank_statements.gateway_transaction_id
         */
        private Long gatewayTransactionId;

        /**
         * Approval status from Gateway
         * Values: APPROVED, REJECTED, PENDING, FAILED
         * Will be stored in bank_statements.approval_status
         */
        private String approvalStatus;

        /**
         * Timestamp when Gateway processed the statement
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private Instant processedAt;
    }

    /**
     * Convert approval status string to enum
     */
    public ApprovalStatus getApprovalStatusEnum() {
        try {
            return ApprovalStatus.valueOf(payload.getApprovalStatus());
        } catch (IllegalArgumentException e) {
            // Default to PENDING if invalid status
            return ApprovalStatus.PENDING;
        }
    }
}