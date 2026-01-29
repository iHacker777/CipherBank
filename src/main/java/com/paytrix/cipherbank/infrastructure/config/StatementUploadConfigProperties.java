package com.paytrix.cipherbank.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for statement upload performance optimization
 *
 * Loads settings from application.yml under the prefix "statement.upload"
 *
 * This class is registered as a bean via @EnableConfigurationProperties in CipherBankApplication
 *
 * Example configuration:
 * statement:
 *   upload:
 *     batch-size: 100
 *     enable-parallel-processing: false
 *     parallel-threads: 4
 */
@Data
@ConfigurationProperties(prefix = "statement.upload")
@Validated
public class StatementUploadConfigProperties {

    /**
     * Number of rows to process in each batch
     * Higher values = fewer DB round trips but more memory usage
     * Lower values = more DB round trips but less memory usage
     *
     * Recommended: 50-200 for most use cases
     * Default: 100
     */
    @NotNull
    @Min(10)
    @Max(1000)
    private Integer batchSize = 100;

    /**
     * Enable parallel processing of batches using multiple threads
     *
     * Benefits:
     * - Faster processing for large files
     * - Better CPU utilization
     *
     * Considerations:
     * - Requires more database connections
     * - May increase memory usage
     * - Best for files with 500+ rows
     *
     * Default: false (single-threaded for safety and simplicity)
     */
    @NotNull
    private Boolean enableParallelProcessing = false;

    /**
     * Number of threads to use for parallel batch processing
     * Only used if enableParallelProcessing = true
     *
     * Recommended: 2-8 threads depending on CPU cores and DB connection pool size
     * Must be less than database connection pool size
     *
     * Default: 4
     */
    @NotNull
    @Min(1)
    @Max(20)
    private Integer parallelThreads = 4;

    /**
     * Log detailed batch processing statistics
     * Useful for debugging and performance tuning
     *
     * Default: true
     */
    @NotNull
    private Boolean logBatchStatistics = true;
}