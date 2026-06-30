package com.meicrypt.identity.common.exception;

/**
 * Exception thrown when attempting to create a resource that already exists.
 * Typically results in HTTP 409 Conflict response.
 */
public class DuplicateResourceException extends RuntimeException {

    private final String conflictingField;

    public DuplicateResourceException(String message, String conflictingField) {
        super(message);
        this.conflictingField = conflictingField;
    }

    public DuplicateResourceException(String resourceType, String fieldName, String value) {
        super(String.format("%s with %s '%s' already exists", resourceType, fieldName, value));
        this.conflictingField = fieldName;
    }

    public String getConflictingField() {
        return conflictingField;
    }
}
