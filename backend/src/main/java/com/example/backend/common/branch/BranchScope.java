package com.example.backend.common.branch;

import com.example.backend.auth.repository.EmployeeRepository;
import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Branch the signed-in employee is limited to (employees.warehouse_id, V12).
 * Empty = no limit: ADMIN, employees without a branch, customers, and internal calls without a signed-in user.
 */
@Component
public class BranchScope {
    private final EmployeeRepository employees;

    public BranchScope(EmployeeRepository employees) { this.employees = employees; }

    public Optional<UUID> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUserPrincipal principal)) return Optional.empty();
        if (auth.getAuthorities().stream().anyMatch(a -> "ADMIN".equals(a.getAuthority()))) return Optional.empty();
        return employees.findByUser_UserId(principal.getUserId()).map(e -> e.getWarehouseId());
    }

    /** True when the signed-in employee may work on data of this branch. */
    public boolean allows(UUID warehouseId) {
        return current().map(branch -> branch.equals(warehouseId)).orElse(true);
    }
}
