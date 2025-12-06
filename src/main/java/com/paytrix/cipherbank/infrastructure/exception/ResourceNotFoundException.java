package com.paytrix.cipherbank.infrastructure.exception;

/**
 * Exception thrown when a requested resource is not found
 * Results in HTTP 404
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(String.format("%s not found with identifier: %s", resourceType, identifier));
    }
}