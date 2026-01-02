package com.paytrix.cipherbank;

import com.paytrix.cipherbank.infrastructure.config.JwtProperties;
import com.paytrix.cipherbank.infrastructure.config.StatementQueryConfigProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, StatementQueryConfigProperties.class})
public class CipherBankApplication {

    public static void main(String[] args) {
        SpringApplication.run(CipherBankApplication.class, args);
    }
}