package com.paytrix.cipherbank.application.port.out;

import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.BankStatement;

public interface BankStatementRepositoryPort {
    BankStatement save(BankStatement stmt);
}
