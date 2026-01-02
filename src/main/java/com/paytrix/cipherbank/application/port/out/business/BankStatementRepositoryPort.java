package com.paytrix.cipherbank.application.port.out.business;

import com.paytrix.cipherbank.domain.model.StatementSearchRequest;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.BankStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Repository port for BankStatement operations
 *
 * Includes methods for:
 * - Statement upload and deduplication
 * - Payment verification
 * - Advanced search with pagination
 */
public interface BankStatementRepositoryPort {

    // ============================================================
    // STATEMENT UPLOAD & DEDUPLICATION
    // ============================================================

    /**
     * Save a bank statement
     * Returns null if duplicate (constraint violation)
     *
     * @param stmt Bank statement to save
     * @return Saved statement or null if duplicate
     */
    BankStatement save(BankStatement stmt);

    /**
     * DEPRECATED: Use existsByAccountNoAndUtr instead
     * @deprecated Checks wrong constraint - use existsByAccountNoAndUtr
     */
    @Deprecated
    boolean existsByUtrAndOrderIdAndAccountNo(String utr, String orderId, Long accountNo);

    /**
     * Check if statement exists with same account number and UTR
     * Matches database constraint uk_stmt_acct_utr (account_no, utr)
     * THIS IS THE CORRECT METHOD FOR DEDUPLICATION
     *
     * @param accountNo Account number
     * @param utr Unique Transaction Reference
     * @return true if exists, false otherwise
     */
    boolean existsByAccountNoAndUtr(Long accountNo, String utr);

    // ============================================================
    // PAYMENT VERIFICATION
    // ============================================================

    /**
     * Find ALL unprocessed statements matching orderId and utr combination
     * Only returns records where processed = false
     * Returns list to detect multiple matches (data inconsistency)
     *
     * @param orderId Order ID
     * @param utr Unique Transaction Reference
     * @return List of unprocessed matching statements
     */
    List<BankStatement> findUnprocessedByOrderIdAndUtr(String orderId, String utr);

    /**
     * Find ALL unprocessed statements matching orderId only
     * Only returns records where processed = false
     * Returns list to detect multiple matches (data inconsistency)
     *
     * @param orderId Order ID
     * @return List of unprocessed matching statements
     */
    List<BankStatement> findUnprocessedByOrderId(String orderId);

    /**
     * Find ALL unprocessed statements matching utr only
     * Only returns records where processed = false
     * Returns list to detect multiple matches (data inconsistency)
     *
     * @param utr Unique Transaction Reference
     * @return List of unprocessed matching statements
     */
    List<BankStatement> findUnprocessedByUtr(String utr);

    // ============================================================
    // ADVANCED SEARCH with complex filters
    // ============================================================

    /**
     * Advanced search with multiple complex filters
     *
     * Supports filtering by:
     * - Bank parser keys (multiple)
     * - Account numbers (multiple)
     * - Approval statuses (multiple)
     * - Gateway transaction IDs (multiple)
     * - Order IDs (multiple)
     * - Processed status (single)
     * - Transaction date/time range (flexible format)
     * - UTRs (multiple)
     * - Upload timestamp range (flexible format)
     * - Amount range (min/max)
     * - Usernames (multiple)
     *
     * Plus pagination and sorting on any column.
     *
     * EMPTY FILTERS: Returns all records with pagination
     *
     * @param searchRequest Search criteria with all filters
     * @param pageable Pagination and sorting parameters
     * @return Page of bank statements matching all criteria
     */
    Page<BankStatement> searchStatements(StatementSearchRequest searchRequest, Pageable pageable);
}