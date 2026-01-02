package com.paytrix.cipherbank.infrastructure.adapter.out.persistence.adapter.business;

import com.paytrix.cipherbank.application.port.out.business.BankStatementRepositoryPort;
import com.paytrix.cipherbank.domain.model.StatementSearchRequest;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.BankStatement;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.repository.business.JpaBankStatementRepository;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.specification.BankStatementSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter implementing BankStatementRepositoryPort using JPA
 *
 * Handles:
 * - Statement upload with deduplication
 * - Payment verification queries
 * - Advanced search with complex filters
 */
@Component
public class BankStatementRepositoryAdapter implements BankStatementRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(BankStatementRepositoryAdapter.class);

    private final JpaBankStatementRepository jpa;

    public BankStatementRepositoryAdapter(JpaBankStatementRepository jpa) {
        this.jpa = jpa;
    }

    // ============================================================
    // STATEMENT UPLOAD & DEDUPLICATION
    // ============================================================

    @Override
    public BankStatement save(BankStatement stmt) {
        try {
            return jpa.saveAndFlush(stmt);
        } catch (DataIntegrityViolationException dup) {
            // Likely unique key violation (dedupe). Caller can count it as deduped.
            log.debug("Duplicate statement detected during save (constraint violation)");
            return null;
        }
    }

    @Override
    @Deprecated
    public boolean existsByUtrAndOrderIdAndAccountNo(String utr, String orderId, Long accountNo) {
        // DEPRECATED: This checks a constraint that may not exist in database
        // Use existsByAccountNoAndUtr instead
        log.warn("DEPRECATED METHOD CALLED: existsByUtrAndOrderIdAndAccountNo - use existsByAccountNoAndUtr instead");
        return jpa.existsByUtrAndOrderIdAndAccountNo(utr, orderId, accountNo);
    }

    @Override
    public boolean existsByAccountNoAndUtr(Long accountNo, String utr) {
        // THIS IS THE CORRECT METHOD - matches database constraint uk_stmt_acct_utr
        return jpa.existsByAccountNoAndUtr(accountNo, utr);
    }

    // ============================================================
    // PAYMENT VERIFICATION
    // ============================================================

    @Override
    public List<BankStatement> findUnprocessedByOrderIdAndUtr(String orderId, String utr) {
        // Only find records where processed = false (0)
        return jpa.findByOrderIdAndUtrAndProcessed(orderId, utr, false);
    }

    @Override
    public List<BankStatement> findUnprocessedByOrderId(String orderId) {
        // Only find records where processed = false (0)
        return jpa.findByOrderIdAndProcessed(orderId, false);
    }

    @Override
    public List<BankStatement> findUnprocessedByUtr(String utr) {
        // Only find records where processed = false (0)
        return jpa.findByUtrAndProcessed(utr, false);
    }

    // ============================================================
    // ADVANCED SEARCH
    // ============================================================

    @Override
    public Page<BankStatement> searchStatements(StatementSearchRequest searchRequest, Pageable pageable) {
        log.debug("Executing advanced search - Filters: bankNames={}, accountNos={}, approvalStatuses={}, " +
                        "gatewayTxnIds={}, orderIds={}, processed={}, txnDateRange={}, utrs={}, " +
                        "uploadTsRange={}, amountRange={}, usernames={}",
                countItems(searchRequest.getBankNames()),
                countItems(searchRequest.getAccountNos()),
                countItems(searchRequest.getApprovalStatuses()),
                countItems(searchRequest.getGatewayTransactionIds()),
                countItems(searchRequest.getOrderIds()),
                searchRequest.getProcessed(),
                searchRequest.getTransactionDateTimeRange() != null ? "YES" : "NO",
                countItems(searchRequest.getUtrs()),
                searchRequest.getUploadTimestampRange() != null ? "YES" : "NO",
                searchRequest.getAmountRange() != null ? "YES" : "NO",
                countItems(searchRequest.getUsernames()));

        // Build JPA Specification from search request
        Specification<BankStatement> spec = BankStatementSpecification.buildSpecification(searchRequest);

        // Execute query with specification and pagination
        Page<BankStatement> page = jpa.findAll(spec, pageable);

        log.debug("Search query returned {} results out of {} total",
                page.getNumberOfElements(), page.getTotalElements());

        return page;
    }

    /**
     * Count items in list (for logging)
     */
    private int countItems(List<?> list) {
        return list != null ? list.size() : 0;
    }
}