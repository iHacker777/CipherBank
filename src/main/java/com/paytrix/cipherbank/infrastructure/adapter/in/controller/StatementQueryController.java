package com.paytrix.cipherbank.infrastructure.adapter.in.controller;

import com.paytrix.cipherbank.application.port.in.StatementQueryUseCase;
import com.paytrix.cipherbank.domain.model.PagedStatementResponse;
import com.paytrix.cipherbank.domain.model.StatementSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

/**
 * REST Controller for advanced bank statement search
 *
 * ENDPOINT:
 * POST /api/statements/search - Advanced filtering with role-based column visibility
 *
 * SECURITY:
 * - Requires authentication
 * - Only ROLE_ADMIN and ROLE_USER can access
 * - Column visibility based on role (configured in application.yml)
 *
 * ROLE_ADMIN: Gets all columns
 * ROLE_USER: Gets limited columns (configured in statement.columns.user)
 */
@RestController
@RequestMapping("/api/statements")
@Tag(name = "Bank Statements Search", description = "Advanced search API for bank statements with role-based access control")
public class StatementQueryController {

    private static final Logger log = LoggerFactory.getLogger(StatementQueryController.class);

    private final StatementQueryUseCase statementQueryUseCase;

    public StatementQueryController(StatementQueryUseCase statementQueryUseCase) {
        this.statementQueryUseCase = statementQueryUseCase;
    }

    /**
     * ADVANCED SEARCH: Search bank statements with complex filters and role-based column visibility
     *
     * SECURITY:
     * - Requires authentication
     * - Only users with ROLE_ADMIN or ROLE_USER can access
     * - Response columns filtered based on user's role
     *
     * ROLE-BASED COLUMNS:
     * - ROLE_ADMIN: All columns (id, balance, accountNo, gatewayTransactionId, approvalStatus, etc.)
     * - ROLE_USER: Limited columns (transactionDateTime, amount, orderId, reference, utr, etc.)
     *
     * This endpoint supports multiple complex filters:
     * - Bank parser keys (multiple: "iob", "kgb", "indianbank")
     * - Account numbers (multiple)
     * - Approval statuses (multiple: PENDING, APPROVED, REJECTED)
     * - Gateway transaction IDs (multiple)
     * - Order IDs (multiple)
     * - Processed status (single: true/false)
     * - Transaction date/time range (flexible: date-only or date-with-time)
     * - UTRs (multiple)
     * - Upload timestamp range (flexible: date-only or date-with-time)
     * - Amount range (min/max)
     * - Usernames (multiple)
     *
     * Plus pagination and sorting on any column.
     *
     * RESPONSE INCLUDES:
     * - userRole: The role of the user making the request
     * - visibleColumns: List of columns visible to this user's role
     * - statements: Filtered bank statements (non-visible columns set to null)
     * - Pagination metadata
     *
     * EMPTY BODY USAGE:
     * POST with empty body or just pagination returns all records:
     * {"page": 0, "size": 20}
     *
     * @param searchRequest Search criteria with all filters and pagination
     * @param authentication Spring Security authentication object (auto-injected)
     * @return Paginated response with filtered statements and role information
     */
    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_USER')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Advanced search for bank statements with role-based access",
            description = "Search with complex filters and role-based column visibility. " +
                    "ROLE_ADMIN sees all columns, ROLE_USER sees limited columns. " +
                    "Response includes user's role and list of visible columns. " +
                    "Empty body returns all records with pagination.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Success",
                            content = @Content(schema = @Schema(implementation = PagedStatementResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid search criteria"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
            }
    )
    public ResponseEntity<PagedStatementResponse> searchStatements(
            @Valid @RequestBody StatementSearchRequest searchRequest,
            Authentication authentication) {

        // Extract user's role
        String userRole = extractPrimaryRole(authentication);
        String username = authentication.getName();

        log.info("POST /api/statements/search - User: {}, Role: {}, Filters: bankNames={}, accountNos={}, " +
                        "approvalStatuses={}, gatewayTxnIds={}, orderIds={}, processed={}, " +
                        "txnDateRange={}, utrs={}, uploadTsRange={}, amountRange={}, usernames={}, " +
                        "page={}, size={}",
                username, userRole,
                hasItems(searchRequest.getBankNames()),
                hasItems(searchRequest.getAccountNos()),
                hasItems(searchRequest.getApprovalStatuses()),
                hasItems(searchRequest.getGatewayTransactionIds()),
                hasItems(searchRequest.getOrderIds()),
                searchRequest.getProcessed(),
                searchRequest.getTransactionDateTimeRange() != null,
                hasItems(searchRequest.getUtrs()),
                searchRequest.getUploadTimestampRange() != null,
                searchRequest.getAmountRange() != null,
                hasItems(searchRequest.getUsernames()),
                searchRequest.getPage(),
                searchRequest.getSize());

        // Validate page size (logged in service, but log here too)
        if (searchRequest.getSize() != null && searchRequest.getSize() > 100) {
            log.warn("Page size {} exceeds maximum 100, will be capped", searchRequest.getSize());
        }

        // Execute advanced search with role-based filtering
        PagedStatementResponse response = statementQueryUseCase.searchStatements(searchRequest, userRole);

        log.info("Search returned {} statements out of {} total (page {}/{}) for role {}",
                response.getStatements().size(),
                response.getTotalRecords(),
                response.getPageNo() + 1,
                response.getTotalPages(),
                userRole);

        return ResponseEntity.ok(response);
    }

    /**
     * Extract primary role from authentication
     * Returns first role found, preferring ROLE_ADMIN if present
     */
    private String extractPrimaryRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            log.warn("No authentication or authorities found, defaulting to empty role");
            return "";
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        // Check for ROLE_ADMIN first (highest priority)
        boolean hasAdmin = authorities.stream()
                .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));
        if (hasAdmin) {
            return "ROLE_ADMIN";
        }

        // Check for ROLE_USER
        boolean hasUser = authorities.stream()
                .anyMatch(auth -> "ROLE_USER".equals(auth.getAuthority()));
        if (hasUser) {
            return "ROLE_USER";
        }

        // Fallback: return first role found
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");
    }

    /**
     * Check if list has items (for logging)
     */
    private boolean hasItems(java.util.List<?> list) {
        return list != null && !list.isEmpty();
    }
}