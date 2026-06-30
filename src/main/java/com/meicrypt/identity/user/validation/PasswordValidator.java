package com.meicrypt.identity.user.validation;

import com.meicrypt.identity.organization.entity.OrganizationSettings;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Password validation utility following organization-specific password policies.
 */
@Component
public class PasswordValidator {

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");

    /**
     * Validate password against organization settings
     */
    public ValidationResult validate(String password, OrganizationSettings settings) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errors.add("Password cannot be empty");
            return new ValidationResult(false, errors);
        }

        // Check minimum length
        if (password.length() < settings.getPasswordMinLength()) {
            errors.add("Password must be at least " + settings.getPasswordMinLength() + " characters long");
        }

        // Check uppercase requirement
        if (settings.getPasswordRequireUppercase() && !UPPERCASE_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one uppercase letter");
        }

        // Check lowercase requirement
        if (settings.getPasswordRequireLowercase() && !LOWERCASE_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one lowercase letter");
        }

        // Check number requirement
        if (settings.getPasswordRequireNumbers() && !DIGIT_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one number");
        }

        // Check special character requirement
        if (settings.getPasswordRequireSpecialChars() && !SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one special character");
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Check if password contains common weak patterns
     */
    public boolean isCommonPassword(String password) {
        // Common weak passwords list (simplified)
        String[] commonPasswords = {
            "password", "123456", "12345678", "qwerty", "abc123",
            "monkey", "1234567", "letmein", "trustno1", "dragon",
            "baseball", "iloveyou", "master", "sunshine", "ashley"
        };

        String lowerPassword = password.toLowerCase();
        for (String common : commonPasswords) {
            if (lowerPassword.contains(common)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Password validation result
     */
    public record ValidationResult(boolean isValid, List<String> errors) {
        public String getErrorMessage() {
            return String.join("; ", errors);
        }
    }
}
