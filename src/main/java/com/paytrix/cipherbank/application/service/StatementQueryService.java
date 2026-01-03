package com.paytrix.cipherbank.application.service;

import com.paytrix.cipherbank.application.port.in.StatementQueryUseCase;
import com.paytrix.cipherbank.application.port.out.business.BankStatementRepositoryPort;
import com.paytrix.cipherbank.domain.model.BankStatementResponse;
import com.paytrix.cipherbank.domain.model.PagedStatementResponse;
import com.paytrix.cipherbank.domain.model.StatementSearchRequest;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.BankStatement;
import com.paytrix.cipherbank.infrastructure.config.StatementQueryConfigProperties;
import com.paytrix.cipherbank.infrastructure.specification.BankStatementSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for querying bank statements with advanced filters and role-based visibility
 * Applies role-based column filtering before returning results to users
 */
@Service
public class StatementQueryService implements StatementQueryUseCase {

    private static final Logger log = LoggerFactory.getLogger(StatementQueryService.class);

    private final BankStatementRepositoryPort statementRepository;
    private final StatementQueryConfigProperties config;
    private final StatementResponseFilterService filterService;

    public StatementQueryService(
            BankStatementRepositoryPort statementRepository,
            StatementQueryConfigProperties config,
            StatementResponseFilterService filterService) {
        this.statementRepository = statementRepository;
        this.config = config;
        this.filterService = filterService;

        log.info("StatementQueryService initialized with config: maxPageSize={}, defaultPageSize={}, " +
                        "defaultSortColumn={}, defaultSortDirection={}",
                config.getMaxPageSize(), config.getDefaultPageSize(),
                config.getDefaultSortColumn(), config.getDefaultSortDirection());
    }

    @Override
    @Transactional(readOnly = true)
    public PagedStatementResponse searchStatements(StatementSearchRequest request, String userRole) {
        log.info("Executing advanced search for role: {}", userRole);

        // Build specification from search criteria
        Specification<BankStatement> spec = BankStatementSpecification.buildSpecification(request);

        // Build pageable with pagination and sorting
        Pageable pageable = buildPageable(request);

        // Execute query
        Page<BankStatement> page = statementRepository.findAll(spec, pageable);

        log.info("Query returned {} records out of {} total (page {}/{})",
                page.getNumberOfElements(),
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getTotalPages());

        // Convert entities to response DTOs
        List<BankStatementResponse> responses = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        // Apply role-based column filtering
        List<BankStatementResponse> filteredResponses = filterService.filterResponses(responses, userRole);

        // Get visible columns for this role
        List<String> visibleColumns = filterService.getVisibleColumns(userRole);

        log.info("Filtered {} statements for role '{}', visible columns: {}",
                filteredResponses.size(), userRole, visibleColumns.size());

        // Build paginated response with role information
        return PagedStatementResponse.builder()
                // Role & Visibility Information
                .userRole(userRole)
                .visibleColumns(visibleColumns)
                // Pagination Metadata
                .pageNo(page.getNumber())
                .pageSize(page.getSize())
                .totalRecords(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .sort(buildSortString(page.getSort()))
                // Data (filtered)
                .statements(filteredResponses)
                .build();
    }

    /**
     * Build Pageable with pagination and sorting
     * Parses sort field in format ["column,direction", "column2,direction2"]
     */
    private Pageable buildPageable(StatementSearchRequest request) {
        // Page number (default 0)
        int page = request.getPage() != null ? request.getPage() : 0;

        // Page size (default from config, capped at max)
        int size = request.getSize() != null ? request.getSize() : config.getDefaultPageSize();
        if (size > config.getMaxPageSize()) {
            log.warn("Requested page size {} exceeds maximum {}, capping to maximum",
                    size, config.getMaxPageSize());
            size = config.getMaxPageSize();
        }

        // Build Sort from sort field
        Sort sort = buildSort(request);

        return PageRequest.of(page, size, sort);
    }

    /**
     * Build Sort object from request.sort field
     * Format: ["column,direction", "column2,direction2"]
     * Example: ["transactionDateTime,desc", "amount,asc"]
     *
     * If sort is null or empty, uses default from config
     */
    private Sort buildSort(StatementSearchRequest request) {
        List<String> sortList = request.getSort();

        // If no sort specified, use default from config
        if (sortList == null || sortList.isEmpty()) {
            String defaultColumn = config.getDefaultSortColumn();
            String defaultDirection = config.getDefaultSortDirection();

            return "asc".equalsIgnoreCase(defaultDirection)
                    ? Sort.by(defaultColumn).ascending()
                    : Sort.by(defaultColumn).descending();
        }

        // Parse sort list
        List<Sort.Order> orders = new ArrayList<>();

        for (String sortStr : sortList) {
            if (sortStr == null || sortStr.isBlank()) {
                continue;
            }

            // Parse "column,direction" format
            String[] parts = sortStr.split(",");
            String column = parts[0].trim();
            String direction = parts.length > 1 ? parts[1].trim() : "asc";

            Sort.Order order = "asc".equalsIgnoreCase(direction)
                    ? Sort.Order.asc(column)
                    : Sort.Order.desc(column);

            orders.add(order);
        }

        // If no valid orders parsed, use default
        if (orders.isEmpty()) {
            String defaultColumn = config.getDefaultSortColumn();
            String defaultDirection = config.getDefaultSortDirection();

            return "asc".equalsIgnoreCase(defaultDirection)
                    ? Sort.by(defaultColumn).ascending()
                    : Sort.by(defaultColumn).descending();
        }

        return Sort.by(orders);
    }

    /**
     * Convert entity to response DTO (with all fields)
     * Filtering will be applied later based on role
     */
    private BankStatementResponse toResponse(BankStatement entity) {
        return BankStatementResponse.builder()
                .id(entity.getId())
                .transactionDateTime(entity.getTransactionDateTime())
                .amount(entity.getAmount())
                .balance(entity.getBalance())
                .orderId(entity.getOrderId())
                .reference(entity.getReference())
                .payIn(entity.isPayIn())
                .accountNo(entity.getAccountNo())
                .utr(entity.getUtr())
                .gatewayTransactionId(entity.getGatewayTransactionId())
                .uploadTimestamp(entity.getUploadTimestamp())
                .approvalStatus(entity.getApprovalStatus() != null ? entity.getApprovalStatus().name() : null)
                .processed(entity.isProcessed())
                .type(entity.getType())
                .uploadId(entity.getUpload() != null ? entity.getUpload().getId() : null)
                .uploadUsername(entity.getUpload() != null ? entity.getUpload().getUsername() : null)
                .bankName(entity.getUpload() != null && entity.getUpload().getBank() != null
                        ? entity.getUpload().getBank().getName() : null)
                .bankParserKey(entity.getUpload() != null && entity.getUpload().getBank() != null
                        ? entity.getUpload().getBank().getParserKey() : null)
                .build();
    }

    /**
     * Build sort string for response (e.g., "transactionDateTime: DESC")
     */
    private String buildSortString(Sort sort) {
        if (sort.isUnsorted()) {
            return "unsorted";
        }

        return sort.stream()
                .map(order -> order.getProperty() + ": " + order.getDirection())
                .reduce((a, b) -> a + ", " + b)
                .orElse("unsorted");
    }
}