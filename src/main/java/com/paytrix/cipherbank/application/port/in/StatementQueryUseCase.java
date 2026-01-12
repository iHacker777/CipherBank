package com.paytrix.cipherbank.application.port.in;

import com.paytrix.cipherbank.domain.model.PagedStatementResponse;
import com.paytrix.cipherbank.domain.model.StatementSearchRequest;

/**
 * Use case interface for querying bank statements
 * Provides advanced search with role-based column visibility
 */
public interface StatementQueryUseCase {

    /**
     * Search bank statements with advanced filters and role-based column visibility
     *
     * @param request Search criteria with filters, pagination, and sorting
     * @param userRole User's role (e.g., "ROLE_ADMIN", "ROLE_USER") for column filtering
     * @return Paginated response with filtered statements based on user's role
     */
    PagedStatementResponse searchStatements(StatementSearchRequest request, String userRole);
}