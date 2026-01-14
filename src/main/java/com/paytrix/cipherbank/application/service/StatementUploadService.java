package com.paytrix.cipherbank.application.service;

import com.paytrix.cipherbank.application.port.in.StatementUploadUseCase;
import com.paytrix.cipherbank.application.port.out.business.BankProfileRepositoryPort;
import com.paytrix.cipherbank.application.port.out.business.BankStatementRepositoryPort;
import com.paytrix.cipherbank.application.port.out.business.BankStatementUploadRepositoryPort;
import com.paytrix.cipherbank.application.port.out.messaging.StatementEventPublisher;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.ApprovalStatus;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.BankStatement;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.BankStatementUpload;
import com.paytrix.cipherbank.infrastructure.config.StatementUploadConfigProperties;
import com.paytrix.cipherbank.infrastructure.config.parser.ParserConfigLoader;
import com.paytrix.cipherbank.infrastructure.parser.ParserEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class StatementUploadService implements StatementUploadUseCase {

    private static final Logger log = LoggerFactory.getLogger(StatementUploadService.class);

    private final BankProfileRepositoryPort bankProfileRepo;
    private final BankStatementUploadRepositoryPort uploadRepo;
    private final BankStatementRepositoryPort stmtRepo;
    private final ParserConfigLoader configLoader;
    private final StatementEventPublisher eventPublisher;
    private final StatementUploadConfigProperties uploadConfig;

    public StatementUploadService(BankProfileRepositoryPort bankProfileRepo,
                                  BankStatementUploadRepositoryPort uploadRepo,
                                  BankStatementRepositoryPort stmtRepo,
                                  ParserConfigLoader configLoader,
                                  StatementEventPublisher eventPublisher,
                                  StatementUploadConfigProperties uploadConfig) {

        this.bankProfileRepo = bankProfileRepo;
        this.uploadRepo = uploadRepo;
        this.stmtRepo = stmtRepo;
        this.configLoader = configLoader;
        this.eventPublisher = eventPublisher;
        this.uploadConfig = uploadConfig;

        // Log configuration on startup
        log.info("StatementUploadService initialized with config:");
        log.info("  - Batch size: {}", uploadConfig.getBatchSize());
        log.info("  - Parallel processing: {}", uploadConfig.getEnableParallelProcessing());
        if (uploadConfig.getEnableParallelProcessing()) {
            log.info("  - Parallel threads: {}", uploadConfig.getParallelThreads());
        }
    }

    @Override
    @Transactional
    public UploadResult upload(UploadCommand cmd) {
        long startTime = System.currentTimeMillis();
        log.info("Starting upload process for parserKey: {}, username: {}", cmd.parserKey(), cmd.username());

        var bank = bankProfileRepo.findByParserKey(cmd.parserKey())
                .orElseThrow(() -> new IllegalArgumentException("Unknown parserKey: " + cmd.parserKey()));

        // create upload row
        var upload = new BankStatementUpload();
        upload.setUsername(cmd.username());
        upload.setUploadTime(Instant.now());
        upload.setBank(bank);
        upload = uploadRepo.save(upload);

        log.info("Created upload record with ID: {}", upload.getId());

        int parsed = 0;
        int inserted = 0;
        int deduped = 0;
        String accountNo = cmd.accountNoOverride(); // optional override

        // Track statements for Kafka publishing
        List<BankStatement> publishedStatements = new ArrayList<>();

        try {
            var bankCfg = configLoader.getBankConfig(cmd.parserKey());
            var rows = ParserEngine.parse(cmd.inputStream(), cmd.originalFilename(), cmd.contentType(), bankCfg, accountNo);
            parsed = rows.size();

            log.info("Parsed {} rows from file: {}", parsed, cmd.originalFilename());

            if (parsed == 0) {
                log.warn("No rows to process");
                return new UploadResult(upload.getId(), cmd.parserKey(), accountNo, parsed, inserted, deduped, upload.getUploadTime());
            }

            // Process in batches
            if (uploadConfig.getEnableParallelProcessing() && parsed > uploadConfig.getBatchSize() * 2) {
                // Parallel processing for large files
                var result = processBatchesParallel(rows, upload, accountNo);
                inserted = result.inserted;
                deduped = result.deduped;
                publishedStatements = result.publishedStatements;
            } else {
                // Sequential processing (default)
                var result = processBatchesSequential(rows, upload, accountNo);
                inserted = result.inserted;
                deduped = result.deduped;
                publishedStatements = result.publishedStatements;
            }

            // Publish all inserted statements to Kafka
            if (!publishedStatements.isEmpty()) {
                log.info("Publishing {} statements to Kafka", publishedStatements.size());
                publishStatementsToKafka(publishedStatements);
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            log.info("Upload processing complete - Parsed: {}, Inserted: {}, Deduped: {}, Published: {}, Duration: {}ms",
                    parsed, inserted, deduped, publishedStatements.size(), duration);

            if (uploadConfig.getLogBatchStatistics()) {
                logStatistics(parsed, inserted, deduped, duration);
            }

        } catch (Exception ex) {
            log.error("Failed to process file: {}", cmd.originalFilename(), ex);
            throw new RuntimeException("Failed to process file: " + cmd.originalFilename(), ex);
        }

        return new UploadResult(upload.getId(), cmd.parserKey(), accountNo, parsed, inserted, deduped, upload.getUploadTime());
    }

    /**
     * Process batches sequentially (single-threaded)
     * This is the default and safer mode
     */
    private ProcessingResult processBatchesSequential(List<ParserEngine.ParsedRow> rows,
                                                      BankStatementUpload upload,
                                                      String accountNo) {
        int inserted = 0;
        int deduped = 0;
        List<BankStatement> publishedStatements = new ArrayList<>();

        // Split into batches
        List<List<ParserEngine.ParsedRow>> batches = splitIntoBatches(rows, uploadConfig.getBatchSize());
        log.info("Processing {} batches sequentially (batch size: {})", batches.size(), uploadConfig.getBatchSize());

        for (int i = 0; i < batches.size(); i++) {
            List<ParserEngine.ParsedRow> batch = batches.get(i);
            log.debug("Processing batch {}/{} with {} rows", i + 1, batches.size(), batch.size());

            var batchResult = processBatch(batch, upload, accountNo);
            inserted += batchResult.inserted;
            deduped += batchResult.deduped;
            publishedStatements.addAll(batchResult.publishedStatements);

            log.debug("Batch {}/{} complete - Inserted: {}, Deduped: {}",
                    i + 1, batches.size(), batchResult.inserted, batchResult.deduped);
        }

        return new ProcessingResult(inserted, deduped, publishedStatements);
    }

    /**
     * Process batches in parallel (multi-threaded)
     * Only used for large files when enabled in configuration
     */
    private ProcessingResult processBatchesParallel(List<ParserEngine.ParsedRow> rows,
                                                    BankStatementUpload upload,
                                                    String accountNo) {
        // Split into batches
        List<List<ParserEngine.ParsedRow>> batches = splitIntoBatches(rows, uploadConfig.getBatchSize());
        log.info("Processing {} batches in parallel (batch size: {}, threads: {})",
                batches.size(), uploadConfig.getBatchSize(), uploadConfig.getParallelThreads());

        ExecutorService executor = Executors.newFixedThreadPool(uploadConfig.getParallelThreads());
        List<Future<BatchResult>> futures = new ArrayList<>();

        // Submit all batches for processing
        for (int i = 0; i < batches.size(); i++) {
            final int batchIndex = i;
            final List<ParserEngine.ParsedRow> batch = batches.get(i);

            Future<BatchResult> future = executor.submit(() -> {
                log.debug("Thread {} processing batch {}/{}", Thread.currentThread().getName(), batchIndex + 1, batches.size());
                return processBatch(batch, upload, accountNo);
            });

            futures.add(future);
        }

        // Collect results
        int inserted = 0;
        int deduped = 0;
        List<BankStatement> publishedStatements = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            try {
                BatchResult result = futures.get(i).get();
                inserted += result.inserted;
                deduped += result.deduped;
                publishedStatements.addAll(result.publishedStatements);
                log.debug("Batch {}/{} result collected - Inserted: {}, Deduped: {}",
                        i + 1, futures.size(), result.inserted, result.deduped);
            } catch (InterruptedException | ExecutionException e) {
                log.error("Error processing batch {}/{}", i + 1, futures.size(), e);
                throw new RuntimeException("Parallel batch processing failed", e);
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        return new ProcessingResult(inserted, deduped, publishedStatements);
    }

    /**
     * Process a single batch of rows
     * Handles deduplication and batch insert
     */
    private BatchResult processBatch(List<ParserEngine.ParsedRow> batch,
                                     BankStatementUpload upload,
                                     String accountNo) {
        int inserted = 0;
        int deduped = 0;
        List<BankStatement> publishedStatements = new ArrayList<>();

        // Group rows by account number for efficient batch dedup checking
        Map<Long, List<ParserEngine.ParsedRow>> rowsByAccount = batch.stream()
                .filter(r -> r.getAccountNo() != null && !r.getAccountNo().isBlank())
                .collect(Collectors.groupingBy(r -> Long.parseLong(r.getAccountNo())));

        // Process each account group
        for (Map.Entry<Long, List<ParserEngine.ParsedRow>> entry : rowsByAccount.entrySet()) {
            Long acctNo = entry.getKey();
            List<ParserEngine.ParsedRow> accountRows = entry.getValue();

            // STEP 1: Collect all UTRs in this batch for this account
            Set<String> batchUtrs = accountRows.stream()
                    .map(ParserEngine.ParsedRow::getUtr)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // STEP 2: Batch check against database - find which UTRs already exist
            Set<String> existingUtrs = stmtRepo.findExistingUtrsByAccountNo(acctNo, batchUtrs);

            // STEP 3: Also track within-batch duplicates
            Set<String> seenInBatch = new HashSet<>();

            // STEP 4: Filter out duplicates and prepare statements for batch insert
            List<BankStatement> statementsToInsert = new ArrayList<>();

            for (ParserEngine.ParsedRow r : accountRows) {
                // Skip if amount is null or non-positive
                if (r.getAmount() == null || r.getAmount().signum() <= 0) {
                    continue;
                }

                String utr = r.getUtr();

                // Check if duplicate in database
                if (existingUtrs.contains(utr)) {
                    deduped++;
                    log.debug("Skipped DB duplicate: AccountNo={}, UTR={}", acctNo, utr);
                    continue;
                }

                // Check if duplicate within batch
                if (seenInBatch.contains(utr)) {
                    deduped++;
                    log.debug("Skipped within-batch duplicate: AccountNo={}, UTR={}", acctNo, utr);
                    continue;
                }

                // Mark as seen in this batch
                seenInBatch.add(utr);

                // Create statement entity
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
                s.setType(r.getOrderId());
                s.setUploadTimestamp(Instant.now());
                s.setAccountNo(acctNo);

                statementsToInsert.add(s);
            }

            // STEP 5: Batch insert all non-duplicate statements
            if (!statementsToInsert.isEmpty()) {
                List<BankStatement> savedStatements = stmtRepo.saveAll(statementsToInsert);
                inserted += savedStatements.size();
                publishedStatements.addAll(savedStatements);

                // If saveAll returned fewer records than we tried to insert,
                // it means some were duplicates (caught by DB constraint)
                int dbDuplicates = statementsToInsert.size() - savedStatements.size();
                if (dbDuplicates > 0) {
                    deduped += dbDuplicates;
                    log.debug("Batch insert detected {} DB constraint duplicates", dbDuplicates);
                }

                log.debug("Batch inserted {} statements for account {}", savedStatements.size(), acctNo);
            }
        }

        return new BatchResult(inserted, deduped, publishedStatements);
    }

    /**
     * Split rows into batches
     */
    private List<List<ParserEngine.ParsedRow>> splitIntoBatches(List<ParserEngine.ParsedRow> rows, int batchSize) {
        List<List<ParserEngine.ParsedRow>> batches = new ArrayList<>();

        for (int i = 0; i < rows.size(); i += batchSize) {
            int end = Math.min(i + batchSize, rows.size());
            batches.add(rows.subList(i, end));
        }

        return batches;
    }

    /**
     * Publish statements to Kafka after successful DB insertion
     */
    private void publishStatementsToKafka(List<BankStatement> statements) {
        try {
            eventPublisher.publishStatementsUploaded(statements);
            log.info("Successfully published {} statements to Kafka", statements.size());
        } catch (Exception e) {
            log.error("Failed to publish statements to Kafka - Count: {}, Error: {}",
                    statements.size(), e.getMessage());
            // Don't throw - statements are already saved to DB
        }
    }

    /**
     * Log processing statistics
     */
    private void logStatistics(int parsed, int inserted, int deduped, long durationMs) {
        double insertRate = durationMs > 0 ? (inserted * 1000.0 / durationMs) : 0;
        double dedupRate = parsed > 0 ? (deduped * 100.0 / parsed) : 0;

        log.info("=== UPLOAD STATISTICS ===");
        log.info("Total Rows Parsed: {}", parsed);
        log.info("Rows Inserted: {} ({} rows/sec)", inserted, String.format("%.2f", insertRate));
        log.info("Rows Deduped: {} ({}%)", deduped, String.format("%.2f", dedupRate));
        log.info("Processing Time: {}ms", durationMs);
        log.info("========================");
    }

    // Helper classes for returning multiple values
    private static class BatchResult {
        int inserted;
        int deduped;
        List<BankStatement> publishedStatements;

        BatchResult(int inserted, int deduped, List<BankStatement> publishedStatements) {
            this.inserted = inserted;
            this.deduped = deduped;
            this.publishedStatements = publishedStatements;
        }
    }

    private static class ProcessingResult {
        int inserted;
        int deduped;
        List<BankStatement> publishedStatements;

        ProcessingResult(int inserted, int deduped, List<BankStatement> publishedStatements) {
            this.inserted = inserted;
            this.deduped = deduped;
            this.publishedStatements = publishedStatements;
        }
    }
}