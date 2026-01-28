package com.paytrix.cipherbank.infrastructure.exception;

/**
 * Exception thrown when an invalid or disabled parser key is used
 * Results in HTTP 422 Unprocessable Entity
 */
public class InvalidParserKeyException extends RuntimeException {
    private final String parserKey;

    public InvalidParserKeyException(String parserKey, String message) {
        super(message);
        this.parserKey = parserKey;
    }

    public String getParserKey() {
        return parserKey;
    }
}
