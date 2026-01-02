package com.paytrix.cipherbank.infrastructure.adapter.out.persistence.specification;

import com.paytrix.cipherbank.domain.model.StatementSearchRequest;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.ApprovalStatus;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.business.BankStatement;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification builder for BankStatement dynamic filtering
 * Converts StatementSearchRequest into JPA Criteria API predicates
 */
public class BankStatementSpecification {

    /**
     * Build complete specification from search request
     * All filters use AND logic between different types
     * Within same type (e.g., multiple bank parser keys), uses OR logic
     */
    public static Specification<BankStatement> buildSpecification(StatementSearchRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter: Bank parser keys (OR within list)
            // NOTE: request.getBankNames() actually contains parser keys, not bank names
            if (request.getBankNames() != null && !request.getBankNames().isEmpty()) {
                Join<Object, Object> upload = root.join("upload", JoinType.INNER);
                Join<Object, Object> bank = upload.join("bank", JoinType.INNER);
                // CHANGE: Filter by parserKey column instead of name column
                predicates.add(bank.get("parserKey").in(request.getBankNames()));
            }

            // Filter: Account numbers (OR within list)
            if (request.getAccountNos() != null && !request.getAccountNos().isEmpty()) {
                predicates.add(root.get("accountNo").in(request.getAccountNos()));
            }

            // Filter: Approval statuses (OR within list)
            if (request.getApprovalStatuses() != null && !request.getApprovalStatuses().isEmpty()) {
                List<ApprovalStatus> statuses = request.getApprovalStatuses().stream()
                        .map(ApprovalStatus::valueOf)
                        .toList();
                predicates.add(root.get("approvalStatus").in(statuses));
            }

            // Filter: Gateway transaction IDs (OR within list)
            if (request.getGatewayTransactionIds() != null && !request.getGatewayTransactionIds().isEmpty()) {
                predicates.add(root.get("gatewayTransactionId").in(request.getGatewayTransactionIds()));
            }

            // Filter: Order IDs (OR within list)
            if (request.getOrderIds() != null && !request.getOrderIds().isEmpty()) {
                predicates.add(root.get("orderId").in(request.getOrderIds()));
            }

            // Filter: Processed status (single value)
            if (request.getProcessed() != null) {
                predicates.add(criteriaBuilder.equal(root.get("processed"), request.getProcessed()));
            }

            // Filter: Transaction date/time range
            if (request.getTransactionDateTimeRange() != null) {
                addDateTimeRangePredicate(
                        predicates,
                        criteriaBuilder,
                        root.get("transactionDateTime"),
                        request.getTransactionDateTimeRange()
                );
            }

            // Filter: UTRs (OR within list)
            if (request.getUtrs() != null && !request.getUtrs().isEmpty()) {
                predicates.add(root.get("utr").in(request.getUtrs()));
            }

            // Filter: Upload timestamp range
            if (request.getUploadTimestampRange() != null) {
                addInstantRangePredicate(
                        predicates,
                        criteriaBuilder,
                        root.get("uploadTimestamp"),
                        request.getUploadTimestampRange()
                );
            }

            // Filter: Amount range
            if (request.getAmountRange() != null) {
                StatementSearchRequest.AmountRange range = request.getAmountRange();
                if (range.getMin() != null) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                            root.get("amount"), range.getMin()
                    ));
                }
                if (range.getMax() != null) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(
                            root.get("amount"), range.getMax()
                    ));
                }
            }

            // Filter: Usernames (OR within list)
            if (request.getUsernames() != null && !request.getUsernames().isEmpty()) {
                Join<Object, Object> upload = root.join("upload", JoinType.INNER);
                predicates.add(upload.get("username").in(request.getUsernames()));
            }

            // Combine all predicates with AND
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Add LocalDateTime range predicate with whole-day handling
     * If time not specified in start, assumes 00:00:00
     * If time not specified in end, assumes 23:59:59
     */
    private static void addDateTimeRangePredicate(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Path<LocalDateTime> field,
            StatementSearchRequest.DateTimeRange range) {

        if (range.getStart() != null) {
            LocalDateTime start = normalizeStartDateTime(range.getStart());
            predicates.add(cb.greaterThanOrEqualTo(field, start));
        }

        if (range.getEnd() != null) {
            LocalDateTime end = normalizeEndDateTime(range.getEnd());
            predicates.add(cb.lessThanOrEqualTo(field, end));
        }
    }

    /**
     * Add Instant range predicate (for uploadTimestamp)
     * Converts LocalDateTime range to Instant
     */
    private static void addInstantRangePredicate(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Path field,
            StatementSearchRequest.DateTimeRange range) {

        if (range.getStart() != null) {
            LocalDateTime start = normalizeStartDateTime(range.getStart());
            predicates.add(cb.greaterThanOrEqualTo(
                    field,
                    start.atZone(java.time.ZoneId.systemDefault()).toInstant()
            ));
        }

        if (range.getEnd() != null) {
            LocalDateTime end = normalizeEndDateTime(range.getEnd());
            predicates.add(cb.lessThanOrEqualTo(
                    field,
                    end.atZone(java.time.ZoneId.systemDefault()).toInstant()
            ));
        }
    }

    /**
     * Normalize start date/time
     * If time is midnight (00:00:00), keep as is
     * Otherwise, use provided time
     */
    private static LocalDateTime normalizeStartDateTime(LocalDateTime dateTime) {
        if (dateTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            // Already at start of day
            return dateTime;
        }
        // Use provided time
        return dateTime;
    }

    /**
     * Normalize end date/time
     * If time is midnight (00:00:00), assume whole day → set to 23:59:59
     * Otherwise, use provided time
     */
    private static LocalDateTime normalizeEndDateTime(LocalDateTime dateTime) {
        if (dateTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            // Midnight means whole day, so set to end of day
            return dateTime.with(LocalTime.of(23, 59, 59));
        }
        // Use provided time
        return dateTime;
    }
}