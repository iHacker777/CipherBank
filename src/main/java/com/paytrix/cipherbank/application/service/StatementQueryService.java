package com.paytrix.cipherbank.application.service;

import com.paytrix.cipherbank.application.port.in.StatementQueryUseCase;
import com.paytrix.cipherbank.application.port.out.business.BankStatementRepositoryPort;
import com.paytrix.cipherbank.domain.model.BankStatementResponse;
import com.paytrix.cipherbank.domain.model.PagedStatementResponse;
import com.paytrix.cipherbank.domain.model.StatementSearchRequest;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.BankStatement;
import com.paytrix.cipherbank.infrastructure.config.StatementQueryConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for querying bank statements with advanced search
 * All queries go through searchStatements which supports empty filters (returns all)
 *
 * Configuration values (max page size, default sort, etc.) are loaded from application.yml
 */
@Service
public class StatementQueryService implements StatementQueryUseCase {

    private static final Logger log = LoggerFactory.getLogger(StatementQueryService.class);

    private final BankStatementRepositoryPort statementRepository;
    private final StatementQueryConfigProperties config;

    public StatementQueryService(
            BankStatementRepositoryPort statementRepository,
            StatementQueryConfigProperties config) {
        this.statementRepository = statementRepository;
        this.config = config;

        log.info("StatementQueryService initialized with config: maxPageSize={}, defaultPageSize={}, " +
                        "defaultSortColumn={}, defaultSortDirection={}",
                config.getMaxPageSize(), config.getDefaultPageSize(),
                config.getDefaultSortColumn(), config.getDefaultSortDirection());
    }

    @Override
    @Transactional(readOnly = true)
    public PagedStatementResponse searchStatements(StatementSearchRequest searchRequest) {
        log.info("Advanced search request - Filters: bankNames={}, accountNos={}, approvalStatuses={}, " +
                        "gatewayTxnIds={}, orderIds={}, processed={}, txnDateRange={}, utrs={}, " +
                        "uploadTsRange={}, amountRange={}, usernames={} - Page: {}, Size: {}",
                countItems(searchRequest.getBankNames()),
                countItems(searchRequest.getAccountNos()),
                countItems(searchRequest.getApprovalStatuses()),
                countItems(searchRequest.getGatewayTransactionIds()),
                countItems(searchRequest.getOrderIds()),
                searchRequest.getProcessed(),
                searchRequest.getTransactionDateTimeRange() != null ? "YES" : "NO",
                countItems(searchRequest.getUtrs()),
                searchRequest.getUploadTimestampRange() != null ? "YES" : "NO",
                searchRequest.getAmountRange() != null ? "YES" : "NO",
                countItems(searchRequest.getUsernames()),
                searchRequest.getPage(),
                searchRequest.getSize());

        // Validate and build pageable
        Pageable pageable = buildPageable(searchRequest);

        // Execute search
        Page<BankStatement> page = statementRepository.searchStatements(searchRequest, pageable);

        log.info("Search returned {} of {} total records",
                page.getNumberOfElements(), page.getTotalElements());

        return buildPagedResponse(page, pageable);
    }

    /**
     * Build Pageable from search request using configuration values
     */
    private Pageable buildPageable(StatementSearchRequest request) {
        // Use defaults from config if not provided
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : config.getDefaultPageSize();

        // Cap at max from config
        size = Math.min(size, config.getMaxPageSize());

        if (request.getSize() != null && request.getSize() > config.getMaxPageSize()) {
            log.warn("Requested page size {} exceeds maximum {}, capping to maximum",
                    request.getSize(), config.getMaxPageSize());
        }

        // Build sort
        Sort sort = buildSort(request.getSort());

        log.debug("Built pageable - page: {}, size: {}, sort: {}", page, size, sort);

        return PageRequest.of(page, size, sort);
    }

    /**
     * Build Sort from list of sort strings, using configuration defaults if not provided
     */
    private Sort buildSort(List<String> sortList) {
        if (sortList == null || sortList.isEmpty()) {
            // Use default from config
            log.debug("No sort specified, using default: {},{}",
                    config.getDefaultSortColumn(), config.getDefaultSortDirection());

            Sort.Direction direction = "asc".equalsIgnoreCase(config.getDefaultSortDirection())
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;

            return Sort.by(direction, config.getDefaultSortColumn());
        }

        Sort sort = Sort.unsorted();
        for (String sortParam : sortList) {
            String[] parts = sortParam.split(",");
            String column = parts[0].trim();
            String direction = parts.length > 1 ? parts[1].trim() : config.getDefaultSortDirection();

            Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;

            sort = sort.and(Sort.by(sortDirection, column));
        }

        return sort;
    }

    /**
     * Convert Page<BankStatement> to PagedStatementResponse
     */
    private PagedStatementResponse buildPagedResponse(Page<BankStatement> page, Pageable pageable) {
        // Convert entities to DTOs
        List<BankStatementResponse> statements = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        // Build sort string for response
        String sortString = pageable.getSort().toString();
        if (sortString.equals("UNSORTED")) {
            sortString = null;
        }

        log.debug("Returning {} statements out of {} total (Page {}/{})",
                statements.size(), page.getTotalElements(),
                page.getNumber() + 1, page.getTotalPages());

        return PagedStatementResponse.builder()
                .pageNo(page.getNumber())
                .pageSize(page.getSize())
                .totalRecords(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .sort(sortString)
                .statements(statements)
                .build();
    }

    /**
     * Convert BankStatement entity to BankStatementResponse DTO
     */
    private BankStatementResponse toResponse(BankStatement statement) {
        return BankStatementResponse.builder()
                .id(statement.getId())
                .transactionDateTime(statement.getTransactionDateTime())
                .amount(statement.getAmount())
                .balance(statement.getBalance())
                .orderId(statement.getOrderId())
                .reference(statement.getReference())
                .payIn(statement.isPayIn())
                .accountNo(statement.getAccountNo())
                .utr(statement.getUtr())
                .gatewayTransactionId(statement.getGatewayTransactionId())
                .uploadTimestamp(statement.getUploadTimestamp())
                .approvalStatus(statement.getApprovalStatus() != null ? statement.getApprovalStatus().name() : null)
                .processed(statement.isProcessed())
                .type(statement.getType())
                // Upload metadata (if needed, can be null if lazy-loaded)
                .uploadId(statement.getUpload() != null ? statement.getUpload().getId() : null)
                .uploadUsername(statement.getUpload() != null ? statement.getUpload().getUsername() : null)
                .bankName(statement.getUpload() != null && statement.getUpload().getBank() != null
                        ? statement.getUpload().getBank().getName() : null)
                .bankParserKey(statement.getUpload() != null && statement.getUpload().getBank() != null
                        ? statement.getUpload().getBank().getParserKey() : null)
                .build();
    }

    /**
     * Count items in list (for logging)
     */
    private int countItems(List<?> list) {
        return list != null ? list.size() : 0;
    }
}