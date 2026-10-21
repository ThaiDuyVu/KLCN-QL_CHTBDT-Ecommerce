package com.example.backend.auth.service;

import com.example.backend.auth.dto.LoginRequest;
import com.example.backend.cart.repository.CustomerCartRepository;
import com.example.backend.auth.dto.LoginSessionResponse;
import com.example.backend.auth.dto.RegisterRequest;
import com.example.backend.auth.entity.Customer;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.entity.UserRole;
import com.example.backend.auth.entity.UserStatus;
import com.example.backend.auth.exception.InvalidCredentialsException;
import com.example.backend.auth.exception.InvalidRegistrationException;
import com.example.backend.auth.exception.InvalidRefreshTokenException;
import com.example.backend.auth.exception.RefreshTokenReuseDetectedException;
import com.example.backend.auth.exception.UsernameAlreadyExistsException;
import com.example.backend.auth.repository.RoleRepository;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.auth.repository.UserRoleRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final String CUSTOMER_ROLE = "CUSTOMER";
    // BCrypt only hashes the first 72 bytes and the encoder rejects longer input.
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final String USERNAME_TAKEN = "Tên đăng nhập đã được sử dụng";

    private final UserRepository userRepository;
    private final CustomerCartRepository customers;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CustomUserDetailsService customUserDetailsService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            CustomerCartRepository customers,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.userRepository = userRepository;
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        this.customUserDetailsService = customUserDetailsService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    public User authenticate(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return user;
    }

    @Override
    @Transactional
    public LoginAuthenticationResult login(LoginRequest request) {
        User user = authenticate(
                request.getUsername(),
                request.getPassword()
        );

        return startSession(user);
    }

    @Override
    @Transactional
    public LoginAuthenticationResult register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String fullName = request.getFullName().trim();
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidRegistrationException("Mật khẩu quá dài, vui lòng dùng tối đa 72 byte");
        }
        // Case-insensitive so "KhachA" cannot be registered next to "khacha".
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new UsernameAlreadyExistsException(USERNAME_TAKEN);
        }
        Role customerRole = roleRepository.findByRoleName(CUSTOMER_ROLE)
                .orElseThrow(() -> new IllegalStateException("Missing CUSTOMER role"));

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // users.email is NOT NULL UNIQUE but registration does not collect an email yet:
        // store a unique address under the reserved .invalid TLD that can never be delivered.
        user.setEmail("noemail-" + UUID.randomUUID() + "@customer.invalid");
        user.setDisplayName(fullName);
        user.setStatus(UserStatus.ACTIVE.name());
        user.setUpdatedAt(OffsetDateTime.now());
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Two concurrent registrations can both pass the existence check.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uq_users_username".equals(violation.getConstraintName())) {
                    throw new UsernameAlreadyExistsException(USERNAME_TAKEN);
                }
            }
            throw exception;
        }

        userRoleRepository.saveAndFlush(new UserRole(user, customerRole));
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setFullName(fullName);
        customers.saveAndFlush(customer);

        return startSession(user);
    }

    private LoginAuthenticationResult startSession(User user) {
        AuthenticatedUserPrincipal principal = loadPrincipal(user);

        requireActiveUser(principal);

        String accessToken = jwtService.generateAccessToken(
                principal.getUserId(),
                principal.getRoleName()
        );
        String refreshToken = jwtService.generateRefreshToken(
                principal.getUserId()
        );

        refreshTokenService.createRefreshToken(user, refreshToken);

        LoginSessionResponse response = getCurrentSession(principal);

        return new LoginAuthenticationResult(
                response,
                accessToken,
                refreshToken
        );
    }

    @Override
    @Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
    public LoginAuthenticationResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        var userId = jwtService.extractUserId(rawRefreshToken, "refresh");
        AuthenticatedUserPrincipal principal = loadPrincipal(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        requireActiveUser(principal);

        String refreshToken = jwtService.generateRefreshToken(
                principal.getUserId()
        );

        refreshTokenService.rotateRefreshToken(
                rawRefreshToken,
                refreshToken,
                user
        );

        String accessToken = jwtService.generateAccessToken(
                principal.getUserId(),
                principal.getRoleName()
        );

        LoginSessionResponse response = getCurrentSession(principal);

        return new LoginAuthenticationResult(
                response,
                accessToken,
                refreshToken
        );
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeRefreshToken(rawRefreshToken);
    }

    @Override
    public LoginSessionResponse getCurrentSession(
            AuthenticatedUserPrincipal principal
    ) {
        var response = new LoginSessionResponse(
                principal.getUserId(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getRoleName()
        );
        if ("CUSTOMER".equals(principal.getRoleName())) {
            customers.findByUser_UserId(principal.getUserId())
                    .ifPresent(customer -> response.setCustomerId(customer.getCustomerId()));
        }
        return response;
    }

    private void requireActiveUser(AuthenticatedUserPrincipal principal) {
        if (!"ACTIVE".equals(principal.getStatus())) {
            throw new InvalidCredentialsException();
        }
    }

    private AuthenticatedUserPrincipal loadPrincipal(User user) {
        return loadPrincipal(user.getUserId());
    }

    private AuthenticatedUserPrincipal loadPrincipal(java.util.UUID userId) {
        try {
            return customUserDetailsService.loadUserByUserId(
                    userId
            );
        } catch (UsernameNotFoundException exception) {
            throw new InvalidCredentialsException();
        }
    }
}
