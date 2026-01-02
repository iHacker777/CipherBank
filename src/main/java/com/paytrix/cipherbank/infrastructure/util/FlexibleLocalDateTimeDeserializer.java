package com.paytrix.cipherbank.infrastructure.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Custom Jackson deserializer for LocalDateTime that accepts both:
 * 1. Date only: "2025-01-01" → 2025-01-01T00:00:00 (midnight)
 * 2. Date with time: "2025-01-01T10:30:00" → 2025-01-01T10:30:00 (exact time)
 *
 * This is used for flexible date/time range filtering where users can provide:
 * - Just dates for whole-day filtering
 * - Dates with times for precise filtering
 */
public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    // ISO date format: 2025-01-01
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    // ISO datetime format: 2025-01-01T10:30:00
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();

        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String trimmed = value.trim();

        try {
            // Try parsing as full datetime first (contains 'T')
            if (trimmed.contains("T")) {
                return LocalDateTime.parse(trimmed, DATETIME_FORMATTER);
            }

            // Parse as date only and set time to midnight (00:00:00)
            LocalDate date = LocalDate.parse(trimmed, DATE_FORMATTER);
            return LocalDateTime.of(date, LocalTime.MIDNIGHT);

        } catch (DateTimeParseException e) {
            throw new IOException(
                    String.format(
                            "Failed to parse '%s' as LocalDateTime. Expected formats: '2025-01-01' or '2025-01-01T10:30:00'",
                            trimmed
                    ),
                    e
            );
        }
    }
}