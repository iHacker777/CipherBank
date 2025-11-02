package com.paytrix.cipherbank.infrastructure.config.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;

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

    public ParserConfig.BankConfig getBankConfig(String parserKey) {
        if (config.getBanks() == null || !config.getBanks().containsKey(parserKey)) {
            throw new IllegalArgumentException("No parser config found for key: " + parserKey);
        }
        return config.getBanks().get(parserKey);
    }
}
