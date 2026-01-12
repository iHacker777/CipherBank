package com.paytrix.cipherbank;

import com.paytrix.cipherbank.infrastructure.config.JwtProperties;
import com.paytrix.cipherbank.infrastructure.config.StatementColumnVisibilityProperties;
import com.paytrix.cipherbank.infrastructure.config.StatementQueryConfigProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main Spring Boot Application
 *
 * Configuration Properties Registered:
 * - JwtProperties: JWT authentication configuration
 * - StatementQueryConfigProperties: Statement search pagination/sorting config
 * - StatementColumnVisibilityProperties: Role-based column visibility config
 */
@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class,
        StatementQueryConfigProperties.class,
        StatementColumnVisibilityProperties.class
})
public class CipherBankApplication {

    public static void main(String[] args) {
        SpringApplication.run(CipherBankApplication.class, args);
    }
}