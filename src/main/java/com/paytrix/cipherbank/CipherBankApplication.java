package com.paytrix.cipherbank;

import com.paytrix.cipherbank.infrastructure.config.FileUploadConfigProperties;
import com.paytrix.cipherbank.infrastructure.config.JwtProperties;
import com.paytrix.cipherbank.infrastructure.config.StatementColumnVisibilityProperties;
import com.paytrix.cipherbank.infrastructure.config.StatementQueryConfigProperties;
import com.paytrix.cipherbank.infrastructure.config.StatementUploadConfigProperties;
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
 * - FileUploadConfigProperties: File upload size and type constraints
 * - StatementUploadConfigProperties: Statement upload batch processing config
 */
@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class,
        StatementQueryConfigProperties.class,
        StatementColumnVisibilityProperties.class,
        FileUploadConfigProperties.class,
        StatementUploadConfigProperties.class
})
public class CipherBankApplication {

    public static void main(String[] args) {
        SpringApplication.run(CipherBankApplication.class, args);
    }
}