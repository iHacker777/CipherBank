package com.paytrix.cipherbank.application.port.out;

import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.BankStatementUpload;

public interface BankStatementUploadRepositoryPort {
    BankStatementUpload save(BankStatementUpload upload);
}
