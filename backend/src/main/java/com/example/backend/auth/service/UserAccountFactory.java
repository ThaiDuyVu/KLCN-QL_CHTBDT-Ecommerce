package com.example.backend.auth.service;

import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.entity.UserRole;
import com.example.backend.auth.entity.UserStatus;
import com.example.backend.auth.exception.EmailAlreadyExistsException;
import com.example.backend.auth.exception.InvalidRegistrationException;
import com.example.backend.auth.exception.UsernameAlreadyExistsException;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.auth.repository.UserRoleRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Creates a login account (users + user_roles) on behalf of staff, with the same rules as self-registration:
 * case-insensitive unique username, unique email, BCrypt password of at most 72 bytes.
 * The caller creates the matching customers/employees profile in the same transaction.
 */
@Component
public class UserAccountFactory {
    private static final int MAX_PASSWORD_BYTES = 72;
    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwordEncoder;

    public UserAccountFactory(UserRepository users, UserRoleRepository userRoles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.userRoles = userRoles;
        this.passwordEncoder = passwordEncoder;
    }

    /** email may be null: users.email is NOT NULL UNIQUE, so an undeliverable .invalid address is stored, as at registration. */
    @Transactional(propagation = Propagation.MANDATORY)
    public User create(String username, String rawPassword, String email, String displayName, String phone, Role role) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidRegistrationException("Mật khẩu quá dài, vui lòng dùng tối đa 72 byte");
        }
        if (users.existsByUsernameIgnoreCase(username)) throw new UsernameAlreadyExistsException("Tên đăng nhập đã tồn tại");
        String storedEmail = email == null || email.isBlank() ? "noemail-" + UUID.randomUUID() + "@customer.invalid" : email;
        if (users.existsByEmail(storedEmail)) throw new EmailAlreadyExistsException("Email đã được sử dụng: " + storedEmail);
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEmail(storedEmail);
        user.setDisplayName(displayName);
        user.setPhone(phone);
        user.setStatus(UserStatus.ACTIVE.name());
        user.setUpdatedAt(OffsetDateTime.now());
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Two concurrent requests can both pass the existence checks.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation) {
                    if ("uq_users_username".equals(violation.getConstraintName())) throw new UsernameAlreadyExistsException("Tên đăng nhập đã tồn tại");
                    if ("uq_users_email".equals(violation.getConstraintName())) throw new EmailAlreadyExistsException("Email đã được sử dụng: " + storedEmail);
                }
            }
            throw exception;
        }
        userRoles.saveAndFlush(new UserRole(user, role));
        return user;
    }
}
