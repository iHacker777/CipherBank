package com.paytrix.cipherbank.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for file upload constraints
 *
 * Loads file upload settings from application.yml under the prefix "file.upload"
 *
 * This class is registered as a bean via @EnableConfigurationProperties in CipherBankApplication
 *
 * Example configuration:
 * file:
 *   upload:
 *     max-file-size-mb: 10
 *     allowed-extensions:
 *       - .csv
 *       - .xls
 *       - .xlsx
 *       - .pdf
 */
@Data
@ConfigurationProperties(prefix = "file.upload")
@Validated
public class FileUploadConfigProperties {

    /**
     * Maximum file size in megabytes (MB)
     * Default: 10 MB
     *
     * Note: This should match spring.servlet.multipart.max-file-size setting
     */
    @NotNull
    @Min(1)
    private Integer maxFileSizeMb = 10;

    /**
     * Allowed file extensions (including the dot)
     * Default: [.csv, .xls, .xlsx, .pdf]
     */
    private String[] allowedExtensions = {".csv", ".xls", ".xlsx", ".pdf"};

    /**
     * Get max file size in bytes
     * Converts MB to bytes for comparison with file.getSize()
     *
     * @return Max file size in bytes
     */
    public long getMaxFileSizeBytes() {
        return maxFileSizeMb * 1024L * 1024L;
    }

    /**
     * Get human-readable max file size string
     * Used for error messages
     *
     * @return String like "10 MB"
     */
    public String getMaxFileSizeDisplay() {
        return maxFileSizeMb + " MB";
    }

    /**
     * Get comma-separated list of allowed extensions
     * Used for error messages
     *
     * @return String like ".csv, .xls, .xlsx, .pdf"
     */
    public String getAllowedExtensionsDisplay() {
        return String.join(", ", allowedExtensions);
    }
}