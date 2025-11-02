package com.paytrix.cipherbank.infrastructure.adapter.out.persistence.repository;

import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.BankStatementUpload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaBankStatementUploadRepository extends JpaRepository<BankStatementUpload, Long> {
}
