package com.paytrix.cipherbank.infrastructure.adapter.out.persistence.adapter;

import com.paytrix.cipherbank.application.port.out.BankStatementRepositoryPort;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.BankStatement;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.repository.JpaBankStatementRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

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
            return null;
        }
    }
}
