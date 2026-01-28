package com.paytrix.cipherbank.infrastructure.config.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class ParserConfigLoader {

    private final ParserConfig config;

    public ParserConfigLoader() {
        try {
            var resource = new ClassPathResource("parser/parser-config.yml");
            ObjectMapper om = new ObjectMapper(new YAMLFactory());
            this.config = om.readValue(resource.getInputStream(), ParserConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load parser-config.yml", e);
        }
    }

    /**
     * Get bank configuration for a specific parser key
     *
     * @param parserKey The parser key (e.g., "iob", "kgb", "indianbank")
     * @return BankConfig for the parser key
     * @throws IllegalArgumentException if parser key is not found
     */
    public ParserConfig.BankConfig getBankConfig(String parserKey) {
        if (config.getBanks() == null || !config.getBanks().containsKey(parserKey)) {
            throw new IllegalArgumentException("No parser config found for key: " + parserKey);
        }
        return config.getBanks().get(parserKey);
    }

    /**
     * Get all valid parser keys from parser-config.yml
     * Returns the keys from the "banks:" section
     *
     * Example return: ["iob", "kgb", "indianbank"]
     *
     * @return Set of valid parser keys
     */
    public Set<String> getValidParserKeys() {
        if (config.getBanks() == null) {
            return Collections.emptySet();
        }
        return config.getBanks().keySet();
    }

    /**
     * Check if a parser key is valid and enabled
     * Returns true if the parser key exists in parser-config.yml AND is enabled
     * Disabled parsers are considered invalid
     *
     * @param parserKey The parser key to check
     * @return true if valid and enabled, false otherwise
     */
    public boolean isValidParserKey(String parserKey) {
        if (parserKey == null || parserKey.trim().isEmpty()) {
            return false;
        }

        // Check if parser exists
        if (config.getBanks() == null || !config.getBanks().containsKey(parserKey.trim().toLowerCase())) {
            return false;
        }

        // Check if parser is enabled (null defaults to enabled)
        ParserConfig.BankConfig bankConfig = config.getBanks().get(parserKey.trim().toLowerCase());
        return bankConfig.getEnabled() == null || bankConfig.getEnabled();
    }

    /**
     * Get comma-separated list of valid parser keys
     * Used for error messages
     *
     * Example return: "iob, kgb, indianbank"
     *
     * @return Comma-separated string of valid parser keys
     */
    public String getValidParserKeysDisplay() {
        return String.join(", ", getValidParserKeys());
    }

    /**
     * Get the complete parser configuration
     * Use with caution - typically you should use getBankConfig() instead
     *
     * @return Complete ParserConfig object
     */
    public ParserConfig getConfig() {
        return config;
    }

    /**
     * Get supported file extensions for a specific parser key
     * Determines extensions by checking which formats are configured in
     * parser-config.yml
     *
     * Example: If parser has "csv" and "xls" configured, returns [".csv", ".xls"]
     *
     * @param parserKey The parser key (e.g., "iob", "kgb", "uco")
     * @return Set of supported extensions with dots (e.g., [".csv", ".xls",
     *         ".xlsx"])
     * @throws IllegalArgumentException if parser key is not found
     */
    public Set<String> getSupportedExtensions(String parserKey) {

        ParserConfig.BankConfig bankConfig = getBankConfig(parserKey);
        Set<String> extensions = new HashSet<>();

        // Check which formats are configured AND enabled (non-null and enabled)
        if (bankConfig.getCsv() != null && isFormatEnabled(bankConfig.getCsv())) {
            extensions.add(".csv");
        }
        if (bankConfig.getXls() != null && isFormatEnabled(bankConfig.getXls())) {
            extensions.add(".xls");
        }
        if (bankConfig.getXlsx() != null && isFormatEnabled(bankConfig.getXlsx())) {
            extensions.add(".xlsx");
        }
        if (bankConfig.getPdf() != null && isFormatEnabled(bankConfig.getPdf())) {
            extensions.add(".pdf");
        }

        return extensions;
    }

    /**
     * Get comma-separated list of supported extensions for display
     * Used for error messages
     *
     * Example return: ".csv, .xls, .xlsx"
     *
     * @param parserKey The parser key
     * @return Comma-separated string of supported extensions
     * @throws IllegalArgumentException if parser key is not found
     */
    public String getSupportedExtensionsDisplay(String parserKey) {
        Set<String> extensions = getSupportedExtensions(parserKey);
        if (extensions.isEmpty()) {
            return "none";
        }
        return String.join(", ", extensions);
    }

    /**
     * Check if a parser is enabled
     * Returns true if enabled field is null (default) or true
     *
     * @param parserKey The parser key to check
     * @return true if enabled or not specified, false if explicitly disabled
     * @throws IllegalArgumentException if parser key is not found
     */
    public boolean isParserEnabled(String parserKey) {
        ParserConfig.BankConfig bankConfig = getBankConfig(parserKey);
        // Default to enabled if not specified (null means enabled)
        return bankConfig.getEnabled() == null || bankConfig.getEnabled();
    }

    /**
     * Check if a file type config is enabled
     * Returns true if enabled field is null (default) or true
     *
     * @param config The FileTypeConfig to check
     * @return true if enabled or not specified, false if explicitly disabled
     */
    private boolean isFormatEnabled(ParserConfig.FileTypeConfig config) {
        // Default to enabled if not specified (null means enabled)
        return config.getEnabled() == null || config.getEnabled();
    }

    /**
     * Check if a PDF config is enabled
     * Returns true if enabled field is null (default) or true
     *
     * @param config The PdfConfig to check
     * @return true if enabled or not specified, false if explicitly disabled
     */
    private boolean isFormatEnabled(ParserConfig.PdfConfig config) {
        // Default to enabled if not specified (null means enabled)
        return config.getEnabled() == null || config.getEnabled();
    }
}