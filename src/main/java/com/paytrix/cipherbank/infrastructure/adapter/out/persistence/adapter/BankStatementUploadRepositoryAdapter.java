package com.paytrix.cipherbank.infrastructure.adapter.out.persistence.adapter;

import com.paytrix.cipherbank.application.port.out.BankStatementUploadRepositoryPort;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.BankStatementUpload;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.repository.JpaBankStatementUploadRepository;
import org.springframework.stereotype.Component;

@Component
public class BankStatementUploadRepositoryAdapter implements BankStatementUploadRepositoryPort {

    private final JpaBankStatementUploadRepository jpa;

    public BankStatementUploadRepositoryAdapter(JpaBankStatementUploadRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public BankStatementUpload save(BankStatementUpload upload) {
        return jpa.save(upload);
    }
}
