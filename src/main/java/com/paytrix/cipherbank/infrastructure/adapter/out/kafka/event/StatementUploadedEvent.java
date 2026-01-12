package com.paytrix.cipherbank.infrastructure.adapter.out.kafka.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when a new bank statement is uploaded
 * Published to: bank.statements.uploaded
 * Consumer: Payment Gateway Core
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementUploadedEvent {

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
     * Actual payload with statement data
     */
    private StatementPayload payload;

    /**
     * Create event from statement data
     */
    public static StatementUploadedEvent from(Long id, Long accountNo, BigDecimal amount,
                                              String orderId, String utr, LocalDateTime transactionDateTime) {
        return StatementUploadedEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .payload(StatementPayload.builder()
                        .id(id)
                        .accountNo(accountNo)
                        .amount(amount)
                        .orderId(orderId)
                        .utr(utr)
                        .transactionDateTime(transactionDateTime)
                        .build())
                .build();
    }

    /**
     * Statement payload data
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatementPayload {
        /**
         * CipherBank's bank_statements table primary key
         */
        private Long id;

        /**
         * Bank account number
         */
        private Long accountNo;

        /**
         * Transaction amount
         */
        private BigDecimal amount;

        /**
         * Order ID from payment reference
         */
        private String orderId;

        /**
         * UTR (Unique Transaction Reference)
         */
        private String utr;

        /**
         * Transaction date and time
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime transactionDateTime;
    }
}