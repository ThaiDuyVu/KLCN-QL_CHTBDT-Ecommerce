package com.example.backend.common.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Component
@Validated
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    @NotEmpty
    private List<@NotBlank String> allowedOrigins;

    @AssertTrue(message = "Wildcard CORS origins are not allowed")
    public boolean doesNotContainWildcardOrigin() {
        return allowedOrigins == null
                || allowedOrigins.stream().noneMatch(
                        origin -> origin != null && origin.contains("*")
                );
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
