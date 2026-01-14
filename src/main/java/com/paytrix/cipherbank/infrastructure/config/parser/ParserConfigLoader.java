package com.paytrix.cipherbank.infrastructure.config.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
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
     * Check if a parser key is valid
     * Returns true if the parser key exists in parser-config.yml
     *
     * @param parserKey The parser key to check
     * @return true if valid, false otherwise
     */
    public boolean isValidParserKey(String parserKey) {
        if (parserKey == null || parserKey.trim().isEmpty()) {
            return false;
        }
        return config.getBanks() != null &&
                config.getBanks().containsKey(parserKey.trim().toLowerCase());
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
}