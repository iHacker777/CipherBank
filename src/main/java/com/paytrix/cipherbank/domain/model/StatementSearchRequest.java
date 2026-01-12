package com.paytrix.cipherbank.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.paytrix.cipherbank.infrastructure.util.FlexibleLocalDateTimeDeserializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO for advanced bank statement search
 * Supports multiple filters with AND logic between different filter types
 * and OR logic within same filter type (e.g., multiple bank parser keys)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementSearchRequest {

    // ============================================================
    // FILTERS - All optional
    // ============================================================

    /**
     * Filter by bank parser keys (OR logic - matches any)
     *
     * IMPORTANT: Despite the field name "bankNames", this actually filters by
     * the parser_key column in bank_profiles table, NOT the name column.
     *
     * Example: ["iob", "kgb", "indianbank"]
     *
     * Valid parser keys:
     * - "iob" = Indian Overseas Bank
     * - "kgb" = Kerala Gramin Bank
     * - "indianbank" = Indian Bank
     */
    private List<String> bankNames;

    /**
     * Filter by account numbers (OR logic - matches any)
     * Example: [35702000000858, 40130111000733]
     */
    private List<Long> accountNos;

    /**
     * Filter by approval statuses (OR logic - matches any)
     * Values: PENDING, APPROVED, REJECTED
     * Example: ["PENDING", "APPROVED"]
     */
    private List<String> approvalStatuses;

    /**
     * Filter by gateway transaction IDs (OR logic - matches any)
     * Example: [123, 456, 789]
     */
    private List<Long> gatewayTransactionIds;

    /**
     * Filter by order IDs (OR logic - matches any)
     * Example: ["ORDER123", "ORDER456"]
     */
    private List<String> orderIds;

    /**
     * Filter by processed status (single value)
     * true = processed, false = unprocessed, null = both
     */
    private Boolean processed;

    /**
     * Filter by transaction date/time range
     * Accepts both date-only and date-with-time formats
     */
    private DateTimeRange transactionDateTimeRange;

    /**
     * Filter by UTRs (OR logic - matches any)
     * Example: ["116252212691", "123456789012"]
     */
    private List<String> utrs;

    /**
     * Filter by upload timestamp range
     * Accepts both date-only and date-with-time formats
     */
    private DateTimeRange uploadTimestampRange;

    /**
     * Filter by amount range
     * Can provide min only, max only, or both
     */
    private AmountRange amountRange;

    /**
     * Filter by upload usernames (OR logic - matches any)
     * Example: ["admin", "user1", "operator2"]
     */
    private List<String> usernames;

    // ============================================================
    // PAGINATION & SORTING
    // ============================================================

    /**
     * Page number (0-indexed)
     * Default: 0
     */
    private Integer page = 0;

    /**
     * Page size (max 100)
     * Default: 20
     */
    private Integer size = 20;

    /**
     * Sort criteria as array of "column,direction"
     * Example: ["transactionDateTime,desc", "amount,asc"]
     * Default: ["transactionDateTime,desc"]
     */
    private List<String> sort;

    // ============================================================
    // NESTED CLASSES FOR RANGES
    // ============================================================

    /**
     * Date/Time range with flexible format support
     *
     * Supports both:
     * 1. Date only: "2025-01-01" → Automatically expands to whole day (00:00:00 to 23:59:59)
     * 2. Date with time: "2025-01-01T10:30:00" → Uses exact time
     *
     * Examples:
     *
     * WHOLE DAY FILTERING (Date only):
     * {
     *   "start": "2025-01-01",      // → 2025-01-01T00:00:00
     *   "end": "2025-01-31"          // → 2025-01-31T23:59:59
     * }
     *
     * PRECISE TIME FILTERING (Date with time):
     * {
     *   "start": "2025-01-01T10:30:00",  // → Exact time
     *   "end": "2025-01-31T18:45:00"      // → Exact time
     * }
     *
     * MIXED (Date and datetime):
     * {
     *   "start": "2025-01-01",           // → 2025-01-01T00:00:00
     *   "end": "2025-01-31T18:45:00"      // → 2025-01-31T18:45:00
     * }
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DateTimeRange {
        /**
         * Start date/time (inclusive)
         *
         * Formats accepted:
         * - "2025-01-01" → 2025-01-01T00:00:00 (start of day)
         * - "2025-01-01T10:30:00" → 2025-01-01T10:30:00 (exact time)
         */
        @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
        private LocalDateTime start;

        /**
         * End date/time (inclusive)
         *
         * Formats accepted:
         * - "2025-01-31" → 2025-01-31T23:59:59 (end of day)
         * - "2025-01-31T18:30:00" → 2025-01-31T18:30:00 (exact time)
         */
        @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
        private LocalDateTime end;
    }

    /**
     * Amount range
     * Can specify min only, max only, or both
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AmountRange {
        /**
         * Minimum amount (inclusive)
         * null = no minimum
         */
        private BigDecimal min;

        /**
         * Maximum amount (inclusive)
         * null = no maximum
         */
        private BigDecimal max;
    }
}