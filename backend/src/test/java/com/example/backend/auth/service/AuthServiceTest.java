package com.example.backend.auth.service;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.exception.InvalidCredentialsException;
import com.example.backend.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setPassword("$2a$10$hashedPassword");
        user.setEmail("test@example.com");
        user.setStatus("ACTIVE");
    }

    @Test
    void authenticate_shouldReturnUser_whenCredentialsAreValid() {
        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("correctPassword", user.getPassword()))
                .thenReturn(true);

        User result = authService.authenticate("testuser", "correctPassword");

        assertSame(user, result);

        verify(userRepository).findByUsername("testuser");
        verify(passwordEncoder).matches("correctPassword", user.getPassword());
    }

    @Test
    void authenticate_shouldThrowException_whenUsernameDoesNotExist() {
        when(userRepository.findByUsername("unknown"))
                .thenReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.authenticate("unknown", "password")
        );

        assertEquals("Invalid username or password", exception.getMessage());

        verify(userRepository).findByUsername("unknown");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void authenticate_shouldThrowException_whenPasswordIsIncorrect() {
        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("wrongPassword", user.getPassword()))
                .thenReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.authenticate("testuser", "wrongPassword")
        );

        assertEquals("Invalid username or password", exception.getMessage());

        verify(userRepository).findByUsername("testuser");
        verify(passwordEncoder).matches("wrongPassword", user.getPassword());
    }
}

