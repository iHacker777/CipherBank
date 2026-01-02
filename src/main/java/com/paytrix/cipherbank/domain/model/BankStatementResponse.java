package com.paytrix.cipherbank.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * DTO for bank statement data returned to UI
 * Contains all relevant fields from BankStatement entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankStatementResponse {

    private Long id;
    private LocalDateTime transactionDateTime;
    private BigDecimal amount;
    private BigDecimal balance;
    private String orderId;
    private String reference;
    private boolean payIn;
    private Long accountNo;
    private String utr;
    private Long gatewayTransactionId;
    private Instant uploadTimestamp;
    private String approvalStatus;
    private boolean processed;
    private String type;

    // Upload metadata
    private Long uploadId;
    private String uploadUsername;
    private String bankName;
    private String bankParserKey;
}