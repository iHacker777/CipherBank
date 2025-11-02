package com.paytrix.cipherbank.infrastructure.adapter.out.persistence.repository;

import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.BankStatement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaBankStatementRepository extends JpaRepository<BankStatement, Long> {
}
