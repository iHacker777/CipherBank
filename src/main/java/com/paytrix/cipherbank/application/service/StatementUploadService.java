package com.paytrix.cipherbank.application.service;

import com.paytrix.cipherbank.application.port.in.StatementUploadUseCase;
import com.paytrix.cipherbank.application.port.out.BankProfileRepositoryPort;
import com.paytrix.cipherbank.application.port.out.BankStatementRepositoryPort;
import com.paytrix.cipherbank.application.port.out.BankStatementUploadRepositoryPort;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.*;
import com.paytrix.cipherbank.infrastructure.config.parser.ParserConfigLoader;
import com.paytrix.cipherbank.infrastructure.parser.ParserEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class StatementUploadService implements StatementUploadUseCase {

    private final BankProfileRepositoryPort bankProfileRepo;
    private final BankStatementUploadRepositoryPort uploadRepo;
    private final BankStatementRepositoryPort stmtRepo;
    private final ParserConfigLoader configLoader;

    public StatementUploadService(BankProfileRepositoryPort bankProfileRepo,
                                  BankStatementUploadRepositoryPort uploadRepo,
                                  BankStatementRepositoryPort stmtRepo,
                                  ParserConfigLoader configLoader) {

        this.bankProfileRepo = bankProfileRepo;
        this.uploadRepo = uploadRepo;
        this.stmtRepo = stmtRepo;
        this.configLoader = configLoader;
    }

    @Override
    @Transactional
    public UploadResult upload(UploadCommand cmd) {
        var bank = bankProfileRepo.findByParserKey(cmd.parserKey())
                .orElseThrow(() -> new IllegalArgumentException("Unknown parserKey: " + cmd.parserKey()));

        // create upload row
        var upload = new BankStatementUpload();
        upload.setUsername(cmd.username());
        upload.setUploadTime(Instant.now());
        upload.setBank(bank);
        upload = uploadRepo.save(upload);

        int inserted = 0, deduped = 0, parsed = 0;
        String accountNo = cmd.accountNoOverride(); // optional override

        try {
            var bankCfg = configLoader.getBankConfig(cmd.parserKey());
            var rows = ParserEngine.parse(cmd.inputStream(), cmd.originalFilename(), cmd.contentType(), bankCfg, accountNo);
            parsed = rows.size();

            for (var r : rows) {
                var s = new BankStatement();
                s.setUpload(upload);
                s.setTransactionDateTime(r.getTransactionDateTime());
                s.setAmount(r.getAmount());
                s.setBalance(r.getBalance());
                s.setReference(r.getReference());
                s.setOrderId(r.getOrderId());
                s.setUtr(r.getUtr());
                s.setPayIn(r.isPayIn());
                s.setApprovalStatus(ApprovalStatus.PENDING);
                s.setProcessed(false);
                s.setType(r.getOrderId()); // optional: mimic your fallback
                s.setUploadTimestamp(Instant.now());
                // account number: prefer override else leave null (or later parse via ParserEngine if you extend it)
                // s.setAccountNo(accountNo != null ? Long.valueOf(accountNo) : null);

                var saved = stmtRepo.save(s);
                if (saved == null) deduped++; else inserted++;
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to process file: " + cmd.originalFilename(), ex);
        }

        return new UploadResult(upload.getId(), cmd.parserKey(), accountNo, parsed, inserted, deduped, upload.getUploadTime());
    }
}
