package com.paytrix.cipherbank.application.port.out.business;

import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.BankStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for bank statements
 * Provides data access operations for bank statement queries
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
     * Find statement by ID
     * Used by Kafka consumer to update statement with Gateway response
     *
     * @param id Statement ID (primary key)
     * @return Optional containing statement if found
     */
    Optional<BankStatement> findById(Long id);

    /**
     * Check if a duplicate statement already exists based on ACTUAL database constraint
     * Database constraint: uk_stmt_acct_utr on (account_no, utr)
     *
     * This is the CORRECT deduplication method matching the database schema.
     * One UTR should only appear once per account, regardless of order_id.
     *
     * @param accountNo Account number
     * @param utr Unique Transaction Reference
     * @return true if duplicate exists, false otherwise
     */
    boolean existsByAccountNoAndUtr(Long accountNo, String utr);

    /**
     * @deprecated Use existsByAccountNoAndUtr() instead
     * This method checked 3 columns (utr, orderId, accountNo) but the database
     * constraint only enforces 2 columns (accountNo, utr).
     *
     * Kept for backward compatibility but should not be used for new code.
     */
    @Deprecated
    boolean existsByUtrAndOrderIdAndAccountNo(String utr, String orderId, Long accountNo);

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

    /**
     * Find all bank statements matching the given specification with pagination
     * Used for advanced search with complex filters
     *
     * @param spec JPA Specification for building dynamic queries
     * @param pageable Pagination and sorting parameters
     * @return Page of bank statements matching the criteria
     */
    Page<BankStatement> findAll(Specification<BankStatement> spec, Pageable pageable);
}