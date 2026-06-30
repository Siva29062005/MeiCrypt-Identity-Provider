package com.meicrypt.identity.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a string is a valid slug (lowercase alphanumeric with hyphens).
 * Used for organization slugs and other URL-friendly identifiers.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SlugValidator.class)
@Documented
public @interface ValidSlug {
    
    String message() default "Slug must contain only lowercase letters, numbers, and hyphens";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    int minLength() default 3;
    
    int maxLength() default 100;
}
