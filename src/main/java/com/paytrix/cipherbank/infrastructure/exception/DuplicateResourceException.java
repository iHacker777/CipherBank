package com.paytrix.cipherbank.infrastructure.exception;

/**
 * Exception thrown when attempting to create a duplicate resource
 * Results in HTTP 409 Conflict
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String resourceType, String identifier) {
        super(String.format("%s already exists with identifier: %s", resourceType, identifier));
    }
}