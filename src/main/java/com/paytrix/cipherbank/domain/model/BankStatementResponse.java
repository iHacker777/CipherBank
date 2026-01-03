package com.paytrix.cipherbank.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Response DTO for bank statement with all fields
 * Fields may be null based on user's role and column visibility
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BankStatementResponse {

    /**
     * Statement ID
     * Visible to: ADMIN
     */
    private Long id;

    /**
     * Transaction date and time (no timezone conversion)
     * Visible to: ADMIN, USER
     */
    private LocalDateTime transactionDateTime;

    /**
     * Transaction amount
     * Visible to: ADMIN, USER
     */
    private BigDecimal amount;

    /**
     * Account balance after transaction
     * Visible to: ADMIN
     */
    private BigDecimal balance;

    /**
     * Order ID / Transaction ID
     * Visible to: ADMIN, USER
     */
    private String orderId;

    /**
     * Payment reference / description
     * Visible to: ADMIN, USER
     */
    private String reference;

    /**
     * True if credit (money in), false if debit (money out)
     * Visible to: ADMIN, USER
     */
    private Boolean payIn;

    /**
     * Bank account number
     * Visible to: ADMIN
     */
    private Long accountNo;

    /**
     * UTR (Unique Transaction Reference)
     * Visible to: ADMIN, USER
     */
    private String utr;

    /**
     * Gateway transaction ID (set after payment verification)
     * Visible to: ADMIN
     */
    private Long gatewayTransactionId;

    /**
     * Upload timestamp
     * Visible to: ADMIN, USER
     */
    private Instant uploadTimestamp;

    /**
     * Approval status (PENDING, APPROVED, REJECTED)
     * Visible to: ADMIN
     */
    private String approvalStatus;

    /**
     * Processing status (true = processed, false = unprocessed)
     * Visible to: ADMIN
     */
    private Boolean processed;

    /**
     * Transaction type
     * Visible to: ADMIN
     */
    private String type;

    /**
     * Upload ID (foreign key to bank_statement_uploads)
     * Visible to: ADMIN
     */
    private Long uploadId;

    /**
     * Username who uploaded this statement
     * Visible to: ADMIN
     */
    private String uploadUsername;

    /**
     * Bank name
     * Visible to: ADMIN, USER
     */
    private String bankName;

    /**
     * Bank parser key (iob, kgb, indianbank)
     * Visible to: ADMIN
     */
    private String bankParserKey;
}