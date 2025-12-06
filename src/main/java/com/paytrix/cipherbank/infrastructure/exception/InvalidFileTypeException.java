package com.paytrix.cipherbank.infrastructure.exception;

/**
 * Exception thrown when an invalid file type is uploaded
 * Results in HTTP 415 Unsupported Media Type
 */
public class InvalidFileTypeException extends RuntimeException {
    public InvalidFileTypeException(String message) {
        super(message);
    }

    public InvalidFileTypeException(String filename, String allowedTypes) {
        super(String.format("Invalid file type for '%s'. Allowed types: %s", filename, allowedTypes));
    }
}