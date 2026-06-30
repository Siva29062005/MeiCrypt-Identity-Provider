package com.meicrypt.identity.user.exception;

import java.time.LocalDateTime;

/**
 * Exception thrown when attempting to authenticate with a locked user account.
 */
public class UserLockedException extends RuntimeException {
    
    private final LocalDateTime lockedUntil;
    
    public UserLockedException(String message, LocalDateTime lockedUntil) {
        super(message);
        this.lockedUntil = lockedUntil;
    }
    
    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
}
