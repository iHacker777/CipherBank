package com.paytrix.cipherbank.application.service;

import com.paytrix.cipherbank.domain.model.BankStatementResponse;
import com.paytrix.cipherbank.infrastructure.config.StatementColumnVisibilityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to filter bank statement response columns based on user role
 *
 * Applies role-based column visibility rules to hide sensitive fields
 * from users who don't have permission to view them
 */
@Service
public class StatementResponseFilterService {

    private static final Logger log = LoggerFactory.getLogger(StatementResponseFilterService.class);

    private final StatementColumnVisibilityProperties columnVisibility;

    public StatementResponseFilterService(StatementColumnVisibilityProperties columnVisibility) {
        this.columnVisibility = columnVisibility;
    }

    /**
     * Filter a single statement response based on role visibility
     * Sets non-visible fields to null
     *
     * @param statement Original statement response
     * @param role User's role (e.g., "ROLE_ADMIN", "admin")
     * @return Filtered statement with only visible columns
     */
    public BankStatementResponse filterResponse(BankStatementResponse statement, String role) {
        if (statement == null) {
            return null;
        }

        String normalizedRole = normalizeRole(role);
        List<String> visibleColumns = columnVisibility.getVisibleColumnsForRole(normalizedRole);

        log.debug("Filtering statement for role '{}', visible columns: {}", normalizedRole, visibleColumns.size());

        // Create a copy and set non-visible fields to null
        BankStatementResponse filtered = statement.toBuilder().build();

        // Apply visibility rules
        if (!isVisible(visibleColumns, "id")) filtered.setId(null);
        if (!isVisible(visibleColumns, "transactionDateTime")) filtered.setTransactionDateTime(null);
        if (!isVisible(visibleColumns, "amount")) filtered.setAmount(null);
        if (!isVisible(visibleColumns, "balance")) filtered.setBalance(null);
        if (!isVisible(visibleColumns, "orderId")) filtered.setOrderId(null);
        if (!isVisible(visibleColumns, "reference")) filtered.setReference(null);
        if (!isVisible(visibleColumns, "payIn")) filtered.setPayIn(null);
        if (!isVisible(visibleColumns, "accountNo")) filtered.setAccountNo(null);
        if (!isVisible(visibleColumns, "utr")) filtered.setUtr(null);
        if (!isVisible(visibleColumns, "gatewayTransactionId")) filtered.setGatewayTransactionId(null);
        if (!isVisible(visibleColumns, "uploadTimestamp")) filtered.setUploadTimestamp(null);
        if (!isVisible(visibleColumns, "approvalStatus")) filtered.setApprovalStatus(null);
        if (!isVisible(visibleColumns, "processed")) filtered.setProcessed(null);
        if (!isVisible(visibleColumns, "type")) filtered.setType(null);
        if (!isVisible(visibleColumns, "uploadId")) filtered.setUploadId(null);
        if (!isVisible(visibleColumns, "uploadUsername")) filtered.setUploadUsername(null);
        if (!isVisible(visibleColumns, "bankName")) filtered.setBankName(null);
        if (!isVisible(visibleColumns, "bankParserKey")) filtered.setBankParserKey(null);

        return filtered;
    }

    /**
     * Filter a list of statement responses
     *
     * @param statements List of original statements
     * @param role User's role
     * @return List of filtered statements
     */
    public List<BankStatementResponse> filterResponses(List<BankStatementResponse> statements, String role) {
        if (statements == null || statements.isEmpty()) {
            return statements;
        }

        return statements.stream()
                .map(statement -> filterResponse(statement, role))
                .toList();
    }

    /**
     * Get list of visible columns for a role
     *
     * @param role User's role
     * @return List of column names visible to this role
     */
    public List<String> getVisibleColumns(String role) {
        String normalizedRole = normalizeRole(role);
        return columnVisibility.getVisibleColumnsForRole(normalizedRole);
    }

    /**
     * Check if a column is visible
     */
    private boolean isVisible(List<String> visibleColumns, String columnName) {
        return visibleColumns.contains(columnName);
    }

    /**
     * Normalize role name by removing "ROLE_" prefix and converting to lowercase
     */
    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        return role.replace("ROLE_", "").toLowerCase();
    }
}