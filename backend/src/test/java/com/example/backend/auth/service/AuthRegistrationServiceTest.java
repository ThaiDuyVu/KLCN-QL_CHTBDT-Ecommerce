package com.example.backend.auth.service;

import com.example.backend.auth.dto.RegisterRequest;
import com.example.backend.auth.entity.Customer;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.entity.UserRole;
import com.example.backend.auth.exception.InvalidRegistrationException;
import com.example.backend.auth.exception.UsernameAlreadyExistsException;
import com.example.backend.auth.repository.RoleRepository;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.auth.repository.UserRoleRepository;
import com.example.backend.cart.repository.CustomerCartRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthRegistrationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CustomUserDetailsService customUserDetailsService;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private CustomerCartRepository customers;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void register_createsActiveCustomerWithHashedPasswordAndSignsIn() {
        UUID userId = UUID.randomUUID();
        Role customerRole = role();
        when(userRepository.existsByUsernameIgnoreCase("tranthib")).thenReturn(false);
        when(roleRepository.findByRoleName("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode("matkhau123")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setUserId(userId);
            return saved;
        });
        when(customUserDetailsService.loadUserByUserId(userId)).thenAnswer(invocation -> {
            User user = new User();
            user.setUserId(userId);
            user.setUsername("tranthib");
            user.setDisplayName("Trần Thị B");
            user.setStatus("ACTIVE");
            return new AuthenticatedUserPrincipal(user, "CUSTOMER");
        });
        when(jwtService.generateAccessToken(userId, "CUSTOMER")).thenReturn("access");
        when(jwtService.generateRefreshToken(userId)).thenReturn("refresh");

        LoginAuthenticationResult result = authService.register(request("  tranthib ", " Trần Thị B ", "matkhau123"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getUsername()).isEqualTo("tranthib");
        assertThat(saved.getPassword()).isEqualTo("hashed");
        assertThat(saved.getDisplayName()).isEqualTo("Trần Thị B");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getEmail()).endsWith("@customer.invalid");

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).saveAndFlush(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRole()).isSameAs(customerRole);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customers).saveAndFlush(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getFullName()).isEqualTo("Trần Thị B");
        assertThat(customerCaptor.getValue().getUser()).isSameAs(saved);

        assertThat(result.getAccessToken()).isEqualTo("access");
        assertThat(result.getRefreshToken()).isEqualTo("refresh");
        assertThat(result.getResponse().getRoleName()).isEqualTo("CUSTOMER");
        verify(refreshTokenService).createRefreshToken(saved, "refresh");
    }

    @Test
    void register_rejectsUsernameThatDiffersOnlyByCase() {
        when(userRepository.existsByUsernameIgnoreCase("TranThiB")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request("TranThiB", "Trần Thị B", "matkhau123")))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(userRepository, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void register_mapsConcurrentUsernameConstraintViolationToConflict() {
        when(userRepository.existsByUsernameIgnoreCase("tranthib")).thenReturn(false);
        when(roleRepository.findByRoleName("CUSTOMER")).thenReturn(Optional.of(role()));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException("duplicate"), "uq_users_username")));

        assertThatThrownBy(() -> authService.register(request("tranthib", "Trần Thị B", "matkhau123")))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(customers, never()).saveAndFlush(any());
    }

    @Test
    void register_rejectsPasswordLongerThanBcryptLimitBeforeHashing() {
        String longPassword = "mật".repeat(20) + "1";

        assertThatThrownBy(() -> authService.register(request("tranthib", "Trần Thị B", longPassword)))
                .isInstanceOf(InvalidRegistrationException.class);

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).saveAndFlush(any());
    }

    private static Role role() {
        Role role = new Role();
        role.setRoleId(UUID.randomUUID());
        role.setRoleName("CUSTOMER");
        return role;
    }

    private static RegisterRequest request(String username, String fullName, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setFullName(fullName);
        request.setPassword(password);
        return request;
    }
}
