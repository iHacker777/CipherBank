package com.paytrix.cipherbank.infrastructure.adapter.out.persistence.repository;

import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.BankStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaBankStatementRepository extends JpaRepository<BankStatement, Long> {

    /**
     * Check if a bank statement exists for given upload and UTR
     */
    @Query("SELECT CASE WHEN COUNT(bs) > 0 THEN true ELSE false END FROM BankStatement bs " +
            "WHERE bs.upload.id = :uploadId AND bs.utr = :utr")
    boolean existsByUploadIdAndUtr(@Param("uploadId") Long uploadId, @Param("utr") String utr);

    /**
     * Check if a bank statement exists for given account number and UTR
     */
    @Query("SELECT CASE WHEN COUNT(bs) > 0 THEN true ELSE false END FROM BankStatement bs " +
            "WHERE bs.accountNo = :accountNo AND bs.utr = :utr")
    boolean existsByAccountNoAndUtr(@Param("accountNo") Long accountNo, @Param("utr") String utr);
}