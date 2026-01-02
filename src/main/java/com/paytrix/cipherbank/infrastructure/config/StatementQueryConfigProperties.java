package com.paytrix.cipherbank.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for bank statement query constraints
 *
 * All values are loaded from application.yml under the prefix "statement.query"
 *
 * This class is registered as a bean via @EnableConfigurationProperties in CipherBankApplication
 *
 * Example configuration:
 * statement:
 *   query:
 *     max-page-size: 100
 *     default-page-size: 20
 *     default-sort-column: transactionDateTime
 *     default-sort-direction: desc
 */
@Data
@ConfigurationProperties(prefix = "statement.query")
@Validated
public class StatementQueryConfigProperties {

    /**
     * Maximum number of records per page
     * Prevents excessive data retrieval
     * Default: 100
     */
    @NotNull
    @Min(1)
    @Max(1000)
    private Integer maxPageSize = 100;

    /**
     * Default page size when not specified in request
     * Default: 20
     */
    @NotNull
    @Min(1)
    @Max(100)
    private Integer defaultPageSize = 20;

    /**
     * Default column to sort by when not specified
     * Should be a valid BankStatement entity field name
     * Default: transactionDateTime
     */
    @NotBlank
    private String defaultSortColumn = "transactionDateTime";

    /**
     * Default sort direction when not specified
     * Values: asc or desc
     * Default: desc
     */
    @NotBlank
    private String defaultSortDirection = "desc";

    /**
     * Validate sort direction on startup
     */
    public void setDefaultSortDirection(String direction) {
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException(
                    "Invalid default sort direction: " + direction + ". Must be 'asc' or 'desc'"
            );
        }
        this.defaultSortDirection = direction.toLowerCase();
    }
}