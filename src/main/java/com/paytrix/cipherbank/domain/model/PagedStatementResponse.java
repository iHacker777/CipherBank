package com.paytrix.cipherbank.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated response for bank statements
 * Includes pagination metadata and the actual data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedStatementResponse {

    /**
     * Current page number (0-indexed)
     */
    private int pageNo;

    /**
     * Number of items per page
     */
    private int pageSize;

    /**
     * Total number of records in database
     */
    private long totalRecords;

    /**
     * Total number of pages
     */
    private int totalPages;

    /**
     * Is this the first page?
     */
    private boolean first;

    /**
     * Is this the last page?
     */
    private boolean last;

    /**
     * Does a next page exist?
     */
    private boolean hasNext;

    /**
     * Does a previous page exist?
     */
    private boolean hasPrevious;

    /**
     * Sort column and direction (e.g., "transactionDateTime,desc")
     */
    private String sort;

    /**
     * The actual statement data for this page
     */
    private List<BankStatementResponse> statements;
}