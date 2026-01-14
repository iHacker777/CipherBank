package com.paytrix.cipherbank.infrastructure.adapter.out.persistence.repository.business;

import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.BankStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

/**
 * JPA Repository for bank statements
 * Extends JpaSpecificationExecutor for dynamic query support
 */
public interface JpaBankStatementRepository extends JpaRepository<BankStatement, Long>,
        JpaSpecificationExecutor<BankStatement> {

    /**
     * Check if a statement already exists with the given Account Number and UTR
     * This matches the ACTUAL database constraint: uk_stmt_acct_utr on (account_no, utr)
     *
     * Business Rule: One UTR should only appear once per account, regardless of order_id
     *
     * @param accountNo Account Number
     * @param utr Unique Transaction Reference
     * @return true if a matching record exists, false otherwise
     */
    boolean existsByAccountNoAndUtr(Long accountNo, String utr);

    /**
     * Batch check for existing UTRs for a given account
     * Returns only the UTRs that already exist in the database
     *
     * This is much more efficient than calling existsByAccountNoAndUtr() in a loop
     *
     * Example:
     * Input: accountNo=12345, utrs=["UTR001", "UTR002", "UTR003"]
     * Output: ["UTR002"] (if only UTR002 exists in DB)
     *
     * @param accountNo Account number
     * @param utrs Set of UTRs to check
     * @return Set of UTRs that already exist (subset of input)
     */
    @Query("SELECT bs.utr FROM BankStatement bs WHERE bs.accountNo = :accountNo AND bs.utr IN :utrs")
    Set<String> findExistingUtrsByAccountNo(@Param("accountNo") Long accountNo, @Param("utrs") Set<String> utrs);

    /**
     * @deprecated This method checks 3 columns but database constraint only enforces 2 columns
     * Use existsByAccountNoAndUtr() instead
     *
     * Check if a statement already exists with the given UTR, Order ID, and Account Number
     *
     * @param utr Unique Transaction Reference
     * @param orderId Order/Transaction ID
     * @param accountNo Account Number
     * @return true if a matching record exists, false otherwise
     */
    @Deprecated
    boolean existsByUtrAndOrderIdAndAccountNo(String utr, String orderId, Long accountNo);

    /**
     * Find ALL unprocessed statements matching orderId and utr (PRIORITY 1)
     * Only returns records where processed = false (0)
     * Returns list to detect multiple matches (data inconsistency)
     *
     * @param orderId Order ID
     * @param utr Unique Transaction Reference
     * @param processed Process status (should be false)
     * @return List of unprocessed matching statements
     */
    List<BankStatement> findByOrderIdAndUtrAndProcessed(String orderId, String utr, boolean processed);

    /**
     * Find ALL unprocessed statements matching orderId only (PRIORITY 2)
     * Only returns records where processed = false (0)
     * Returns list to detect multiple matches (data inconsistency)
     *
     * @param orderId Order ID
     * @param processed Process status (should be false)
     * @return List of unprocessed matching statements
     */
    List<BankStatement> findByOrderIdAndProcessed(String orderId, boolean processed);

    /**
     * Find ALL unprocessed statements matching utr only (PRIORITY 3)
     * Only returns records where processed = false (0)
     * Returns list to detect multiple matches (data inconsistency)
     *
     * @param utr Unique Transaction Reference
     * @param processed Process status (should be false)
     * @return List of unprocessed matching statements
     */
    List<BankStatement> findByUtrAndProcessed(String utr, boolean processed);
}