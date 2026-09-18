package com.example.backend.auth.repository;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.entity.UserRole;
import com.example.backend.auth.entity.UserStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;

public final class UserSpecifications {
    private UserSpecifications() {}
    public static Specification<User> filter(String keyword, UserStatus status, UUID roleId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String escaped = keyword.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("username")), pattern, '\\'),
                        cb.like(cb.lower(root.get("email")), pattern, '\\'),
                        cb.like(cb.lower(root.get("displayName")), pattern, '\\')));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status.name()));
            if (roleId != null) {
                var roles = query.subquery(UUID.class);
                var link = roles.from(UserRole.class);
                roles.select(link.get("user").get("userId")).where(cb.equal(link.get("role").get("roleId"), roleId));
                predicates.add(root.get("userId").in(roles));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
