package com.paytrix.cipherbank.infrastructure.adapter.in.controller;

import com.paytrix.cipherbank.application.port.in.StatementQueryUseCase;
import com.paytrix.cipherbank.domain.model.PagedStatementResponse;
import com.paytrix.cipherbank.domain.model.StatementSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for advanced bank statement search
 *
 * ENDPOINT:
 * POST /api/statements/search - Advanced filtering with complex criteria
 *
 * SECURITY: Protected by SecurityConfig - requires authentication
 *
 * WHY POST FOR SEARCH?
 * - Supports 11 complex filters with arrays and ranges
 * - Avoids URL length limitations (GET limited to ~2000 chars)
 * - Better type safety with JSON body
 * - Industry standard for complex searches (Elasticsearch, GraphQL)
 * - Cleaner frontend code
 */
@RestController
@RequestMapping("/api/statements")
@Tag(name = "Bank Statements Search", description = "Advanced search endpoint for bank statements")
public class StatementQueryController {

    private static final Logger log = LoggerFactory.getLogger(StatementQueryController.class);

    private final StatementQueryUseCase statementQueryUseCase;

    public StatementQueryController(StatementQueryUseCase statementQueryUseCase) {
        this.statementQueryUseCase = statementQueryUseCase;
    }

    /**
     * ADVANCED SEARCH: Search bank statements with complex filters
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
     * EMPTY BODY USAGE:
     * POST with empty body or just pagination returns all records:
     * {"page": 0, "size": 20}
     *
     * WHY POST?
     * - Supports complex JSON body with arrays and nested objects
     * - No URL length limitations
     * - Better type safety and validation
     * - Cleaner frontend code
     * - Industry standard (Elasticsearch, GraphQL)
     *
     * @param searchRequest Search criteria with all filters and pagination
     * @return Paginated response with statements matching all criteria
     */
    @PostMapping("/search")
    @Operation(
            summary = "Advanced search for bank statements",
            description = "Search with complex filters including multiple values, ranges, and nested conditions. " +
                    "Supports 11 different filter types with pagination and sorting. " +
                    "Empty body or just pagination params returns all records.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Success",
                            content = @Content(schema = @Schema(implementation = PagedStatementResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid search criteria"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized")
            }
    )
    public ResponseEntity<PagedStatementResponse> searchStatements(
            @Valid @RequestBody StatementSearchRequest searchRequest) {

        log.info("POST /api/statements/search - Filters: bankNames={}, accountNos={}, " +
                        "approvalStatuses={}, gatewayTxnIds={}, orderIds={}, processed={}, " +
                        "txnDateRange={}, utrs={}, uploadTsRange={}, amountRange={}, usernames={}, " +
                        "page={}, size={}",
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

        // Validate page size (done in service, but log here)
        if (searchRequest.getSize() != null && searchRequest.getSize() > 100) {
            log.warn("Page size {} exceeds maximum 100, will be capped", searchRequest.getSize());
        }

        // Execute advanced search
        PagedStatementResponse response = statementQueryUseCase.searchStatements(searchRequest);

        log.info("Search returned {} statements out of {} total (page {}/{})",
                response.getStatements().size(),
                response.getTotalRecords(),
                response.getPageNo() + 1,
                response.getTotalPages());

        return ResponseEntity.ok(response);
    }

    /**
     * Check if list has items (for logging)
     */
    private boolean hasItems(java.util.List<?> list) {
        return list != null && !list.isEmpty();
    }
}