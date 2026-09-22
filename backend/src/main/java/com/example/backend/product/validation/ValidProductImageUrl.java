package com.example.backend.product.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ProductImageUrlValidator.class)
public @interface ValidProductImageUrl {
    String message() default "Đường dẫn ảnh phải là URL HTTP/HTTPS hợp lệ hoặc đường dẫn bắt đầu bằng /";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
