package com.paytrix.cipherbank.infrastructure.adapter.out.persistence.adapter.business;

import com.paytrix.cipherbank.application.port.out.business.BankStatementRepositoryPort;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.BankStatement;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.repository.business.JpaBankStatementRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter implementing BankStatementRepositoryPort using JPA
 *
 * Handles:
 * - Statement upload with deduplication
 * - Batch inserts for performance
 * - Payment verification queries
 * - Advanced search with complex filters
 */
@Slf4j
@Component
public class BankStatementRepositoryAdapter implements BankStatementRepositoryPort {

    private final JpaBankStatementRepository jpa;

    public BankStatementRepositoryAdapter(JpaBankStatementRepository jpa) {
        this.jpa = jpa;
    }

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
    public List<BankStatement> saveAll(List<BankStatement> statements) {
        if (statements == null || statements.isEmpty()) {
            return List.of();
        }

        try {
            // saveAll with flush - batch insert for performance
            List<BankStatement> saved = jpa.saveAllAndFlush(statements);
            log.debug("Batch saved {} statements successfully", saved.size());
            return saved;

        } catch (DataIntegrityViolationException e) {
            // Batch insert failed due to constraint violation
            // Fall back to individual saves to identify which ones are duplicates
            log.warn("Batch insert failed (likely duplicates), falling back to individual saves");
            return saveIndividually(statements);
        }
    }

    /**
     * Fallback method: Save statements one by one
     * Used when batch insert fails due to constraint violations
     * Returns only successfully saved statements
     */
    private List<BankStatement> saveIndividually(List<BankStatement> statements) {
        List<BankStatement> saved = new ArrayList<>();

        for (BankStatement stmt : statements) {
            try {
                BankStatement savedStmt = jpa.saveAndFlush(stmt);
                saved.add(savedStmt);
            } catch (DataIntegrityViolationException e) {
                // This is a duplicate - skip it
                log.debug("Skipped duplicate: AccountNo={}, UTR={}",
                        stmt.getAccountNo(), stmt.getUtr());
            }
        }

        log.debug("Individual save completed: {}/{} statements saved",
                saved.size(), statements.size());
        return saved;
    }

    @Override
    public Optional<BankStatement> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public boolean existsByAccountNoAndUtr(Long accountNo, String utr) {
        // This matches the actual database constraint: uk_stmt_acct_utr on (account_no, utr)
        return jpa.existsByAccountNoAndUtr(accountNo, utr);
    }

    @Override
    public Set<String> findExistingUtrsByAccountNo(Long accountNo, Set<String> utrs) {
        if (utrs == null || utrs.isEmpty()) {
            return Set.of();
        }
        return jpa.findExistingUtrsByAccountNo(accountNo, utrs);
    }

    @Override
    @Deprecated
    public boolean existsByUtrAndOrderIdAndAccountNo(String utr, String orderId, Long accountNo) {
        // Deprecated - database doesn't enforce this 3-column constraint
        // Kept for backward compatibility
        return jpa.existsByUtrAndOrderIdAndAccountNo(utr, orderId, accountNo);
    }

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

    @Override
    public Page<BankStatement> findAll(Specification<BankStatement> spec, Pageable pageable) {
        return jpa.findAll(spec, pageable);
    }
}