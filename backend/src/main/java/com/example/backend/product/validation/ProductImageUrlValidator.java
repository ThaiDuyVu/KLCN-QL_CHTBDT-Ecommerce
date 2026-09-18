package com.example.backend.product.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;

public class ProductImageUrlValidator implements ConstraintValidator<ValidProductImageUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Required/length constraints are handled separately. Do not fetch or check image availability.
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            URI uri = new URI(value);
            if (value.startsWith("/") && !value.startsWith("//")) {
                return !uri.isAbsolute() && uri.getRawAuthority() == null
                        && uri.getPath() != null && uri.getPath().length() > 1;
            }
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
