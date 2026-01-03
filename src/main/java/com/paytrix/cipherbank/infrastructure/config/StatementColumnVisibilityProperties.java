package com.paytrix.cipherbank.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for role-based column visibility in bank statements
 *
 * Controls which columns are visible to different user roles
 * Loaded from application.yml under the prefix "statement.columns"
 *
 * Example configuration:
 * statement:
 *   columns:
 *     admin:
 *       - id
 *       - transactionDateTime
 *       - amount
 *       - ... (all columns)
 *     user:
 *       - transactionDateTime
 *       - amount
 *       - orderId
 *       - ... (limited columns)
 */
@Data
@ConfigurationProperties(prefix = "statement.columns")
@Validated
public class StatementColumnVisibilityProperties {

    /**
     * Map of role to list of visible column names
     * Role names should be without "ROLE_" prefix (e.g., "admin", "user")
     * Column names must match BankStatementResponse field names
     *
     * Example:
     * {
     *   "admin": ["id", "transactionDateTime", "amount", "balance", ...],
     *   "user": ["transactionDateTime", "amount", "orderId", ...]
     * }
     */
    @NotEmpty(message = "Column visibility configuration must not be empty")
    private Map<String, List<String>> visibility;

    /**
     * Get visible columns for a specific role
     * Role name should be without "ROLE_" prefix
     *
     * @param role Role name (e.g., "admin", "user")
     * @return List of visible column names, or empty list if role not configured
     */
    public List<String> getVisibleColumnsForRole(String role) {
        if (visibility == null) {
            return List.of();
        }

        // Try exact match first
        List<String> columns = visibility.get(role.toLowerCase());
        if (columns != null) {
            return columns;
        }

        // Try with "ROLE_" prefix removed if present
        String normalizedRole = role.replace("ROLE_", "").toLowerCase();
        columns = visibility.get(normalizedRole);

        return columns != null ? columns : List.of();
    }

    /**
     * Check if a specific column is visible for a role
     *
     * @param role Role name (with or without "ROLE_" prefix)
     * @param columnName Column name to check
     * @return true if column is visible for this role, false otherwise
     */
    public boolean isColumnVisible(String role, String columnName) {
        List<String> visibleColumns = getVisibleColumnsForRole(role);
        return visibleColumns.contains(columnName);
    }
}