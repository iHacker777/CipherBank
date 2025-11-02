package com.paytrix.cipherbank.application.port.out;

import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.BankProfile;
import java.util.Optional;

public interface BankProfileRepositoryPort {
    Optional<BankProfile> findByParserKey(String parserKey);
}
