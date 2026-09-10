package com.example.backend.common.exception;

import com.example.backend.auth.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(
            UserNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<String> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(exception.getMessage());
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<String> handleRoleNotFound(
            RoleNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }

    @ExceptionHandler(PermissionNotFoundException.class)
    public ResponseEntity<String> handlePermissionNotFound(
            PermissionNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }

    @ExceptionHandler(ProtectedPermissionException.class)
    public ResponseEntity<String> handleProtectedPermission(
            ProtectedPermissionException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(exception.getMessage());
    }
    @ExceptionHandler(UserRoleNotFoundException.class)
    public ResponseEntity<String> handleUserRoleNotFound(
            UserRoleNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }

    @ExceptionHandler(InvalidJwtTokenException.class)
    public ResponseEntity<String> handleInvalidJwtToken(
            InvalidJwtTokenException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<String> handleInvalidCredentials(
            InvalidCredentialsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(exception.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<String> handleInvalidRefreshToken(
            InvalidRefreshTokenException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(exception.getMessage());
    }

    @ExceptionHandler(RefreshTokenReuseDetectedException.class)
    public ResponseEntity<String> handleRefreshTokenReuseDetected(
            RefreshTokenReuseDetectedException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(exception.getMessage());
    }
}
