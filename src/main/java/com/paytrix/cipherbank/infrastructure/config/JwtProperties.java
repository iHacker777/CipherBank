package com.paytrix.cipherbank.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT Configuration Properties
 *
 * Loads JWT settings from application.yml under the prefix "jwt"
 *
 * This class is registered as a bean via @EnableConfigurationProperties in CipherBankApplication
 * Do NOT add @Configuration annotation - it would create duplicate beans
 *
 * Example configuration:
 * jwt:
 *   secret: your-secret-key-here
 *   encryption-secret: your-encryption-secret-here
 *   expiration: 900000
 */
@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT signing secret key
     * Should be at least 32 characters for HS256
     */
    private String secret;

    /**
     * JWT encryption secret key
     * 128-bit (32 hex characters) for AES encryption
     */
    private String encryptionSecret;

    /**
     * JWT token expiration time in milliseconds
     * Default: 900000 (15 minutes)
     */
    private long expiration;
}