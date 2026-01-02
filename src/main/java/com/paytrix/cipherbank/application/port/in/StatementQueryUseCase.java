package com.paytrix.cipherbank.application.port.in;

import com.paytrix.cipherbank.domain.model.PagedStatementResponse;
import com.paytrix.cipherbank.domain.model.StatementSearchRequest;

/**
 * Use case for querying bank statements with advanced search
 *
 * All querying is done through the searchStatements method which supports:
 * - Empty filters (returns all records)
 * - Simple filters (single field filtering)
 * - Complex filters (multiple fields with arrays and ranges)
 * - Pagination and sorting
 */
public interface StatementQueryUseCase {

    /**
     * Advanced search with multiple complex filters
     *
     * Supports filtering by:
     * - Bank parser keys (multiple)
     * - Account numbers (multiple)
     * - Approval statuses (multiple)
     * - Gateway transaction IDs (multiple)
     * - Order IDs (multiple)
     * - Processed status (single)
     * - Transaction date/time range (flexible format)
     * - UTRs (multiple)
     * - Upload timestamp range (flexible format)
     * - Amount range (min/max)
     * - Usernames (multiple)
     *
     * Plus pagination and sorting on any column.
     *
     * USAGE:
     * - Empty body: Returns all records
     * - Single filter: {"processed": false}
     * - Multiple filters: {"bankNames": ["iob"], "processed": false, "accountNos": [123]}
     * - Date ranges: Accepts both "2025-01-01" (whole day) and "2025-01-01T10:30:00" (exact time)
     *
     * @param searchRequest Search criteria with all filters, pagination, and sorting
     * @return Paginated response with statements and metadata
     */
    PagedStatementResponse searchStatements(StatementSearchRequest searchRequest);
}