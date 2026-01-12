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

import java.util.List;
import java.util.Optional;

/**
 * Adapter implementing BankStatementRepositoryPort using JPA
 *
 * Handles:
 * - Statement upload with deduplication
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
    public Optional<BankStatement> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public boolean existsByAccountNoAndUtr(Long accountNo, String utr) {
        // This matches the actual database constraint: uk_stmt_acct_utr on (account_no, utr)
        return jpa.existsByAccountNoAndUtr(accountNo, utr);
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