package com.meicrypt.identity.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validator implementation for @ValidSlug annotation.
 * Ensures slugs are URL-friendly and follow proper naming conventions.
 */
public class SlugValidator implements ConstraintValidator<ValidSlug, String> {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private int minLength;
    private int maxLength;

    @Override
    public void initialize(ValidSlug constraintAnnotation) {
        this.minLength = constraintAnnotation.minLength();
        this.maxLength = constraintAnnotation.maxLength();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Use @NotNull for null checks
        }

        if (value.length() < minLength || value.length() > maxLength) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Slug must be between %d and %d characters", minLength, maxLength)
            ).addConstraintViolation();
            return false;
        }

        if (!SLUG_PATTERN.matcher(value).matches()) {
            return false;
        }

        // Additional checks
        if (value.startsWith("-") || value.endsWith("-")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Slug cannot start or end with a hyphen"
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
