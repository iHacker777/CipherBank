package com.paytrix.cipherbank.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated response for bank statement search
 * Includes pagination metadata, role information, and column visibility
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedStatementResponse {

    /**
     * User's role that made this request
     * Example: "ROLE_ADMIN", "ROLE_USER"
     */
    private String userRole;

    /**
     * List of columns visible to this user's role
     * Only these columns will have non-null values in the statements
     * Example: ["transactionDateTime", "amount", "orderId", "reference"]
     */
    private List<String> visibleColumns;

    /**
     * Current page number (0-indexed)
     */
    private Integer pageNo;

    /**
     * Number of records per page
     */
    private Integer pageSize;

    /**
     * Total number of records across all pages
     */
    private Long totalRecords;

    /**
     * Total number of pages
     */
    private Integer totalPages;

    /**
     * True if this is the first page
     */
    private Boolean first;

    /**
     * True if this is the last page
     */
    private Boolean last;

    /**
     * True if there is a next page
     */
    private Boolean hasNext;

    /**
     * True if there is a previous page
     */
    private Boolean hasPrevious;

    /**
     * Sort criteria applied (e.g., "transactionDateTime: DESC")
     */
    private String sort;

    /**
     * List of bank statements for current page
     * Fields will be null for columns not visible to the user's role
     */
    private List<BankStatementResponse> statements;
}