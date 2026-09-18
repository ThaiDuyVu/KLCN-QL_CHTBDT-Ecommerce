package com.example.backend.auth.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Synthetic local-development accounts. Disabled unless both the dev profile and
 * app.seed.auth.enabled=true are selected. Never resets existing accounts.
 * Uses the existing schema defaults; this is not a Flyway migration.
 */
@Component
@Order(5)
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.auth.enabled", havingValue = "true")
public class AuthSeedData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthSeedData.class);

    // username, display name, existing role name
    private static final String[][] ACCOUNTS = {
            {"seed.admin", "Quản trị viên mẫu", "ADMIN"},
            {"seed.manager", "Quản lý cửa hàng mẫu", "MANAGER"},
            {"seed.staff", "Nhân viên cửa hàng mẫu", "STAFF"},
            {"seed.customer1", "Khách hàng mẫu 01", "CUSTOMER"},
            {"seed.customer2", "Khách hàng mẫu 02", "CUSTOMER"}
    };

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AuthSeedData(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        for (String[] account : ACCOUNTS) {
            UUID roleId = jdbcTemplate.queryForObject(
                    "SELECT role_id FROM roles WHERE role_name = ?",
                    UUID.class,
                    account[2]
            );

            // ON CONFLICT only skips an existing username; other constraint errors
            // fail and roll back the entire seed instead of overwriting real data.
            int inserted = jdbcTemplate.update("""
                    INSERT INTO users (username, password, email, display_name, status, updated_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)
                    ON CONFLICT (username) DO NOTHING
                    """,
                    account[0],
                    passwordEncoder.encode("123"),
                    account[0] + "@example.test",
                    account[1]
            );

            UUID userId = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM users WHERE username = ?",
                    UUID.class,
                    account[0]
            );
            if (inserted != 0) {
                jdbcTemplate.update(
                        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                        userId,
                        roleId
                );
                created++;
            }

            // Only complete profiles belonging to the expected synthetic account and role.
            // A username collision or a role changed by the developer must remain untouched.
            Boolean seedAccount = jdbcTemplate.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM users u
                        JOIN user_roles ur ON ur.user_id = u.user_id
                        WHERE u.user_id = ? AND u.email = ? AND ur.role_id = ?
                    )
                    """, Boolean.class, userId, account[0] + "@example.test", roleId);
            if (!Boolean.TRUE.equals(seedAccount)) {
                log.warn("Development auth profile skipped: account '{}' does not match seed identity/role", account[0]);
                continue;
            }
            if ("CUSTOMER".equals(account[2])) {
                jdbcTemplate.update("""
                        INSERT INTO customers (user_id, full_name, loyalty_point)
                        VALUES (?, ?, 0)
                        ON CONFLICT (user_id) DO NOTHING
                        """, userId, account[1]);
            } else {
                jdbcTemplate.update("""
                        INSERT INTO employees (user_id, employee_code, full_name)
                        VALUES (?, ?, ?)
                        ON CONFLICT (user_id) DO NOTHING
                        """, userId, "DEV-" + account[2], account[1]);
            }
        }

        // Never log credentials or password hashes.
        log.info("Development auth seed finished: {} created, {} existing accounts skipped",
                created, ACCOUNTS.length - created);
    }
}
