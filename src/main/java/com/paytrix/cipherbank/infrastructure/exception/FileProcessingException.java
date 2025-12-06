package com.paytrix.cipherbank.infrastructure.exception;

/**
 * Exception thrown when file processing fails
 * Results in HTTP 422 Unprocessable Entity
 */
public class FileProcessingException extends RuntimeException {
    public FileProcessingException(String message) {
        super(message);
    }

    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileProcessingException(String filename, String reason) {
        super(String.format("Failed to process file '%s': %s", filename, reason));
    }
}