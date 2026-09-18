package com.example.backend.auth.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class ManagementAuthorization {
    private ManagementAuthorization() {}
    public static boolean has(String name) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> name.equals(a.getAuthority()));
    }
    public static void require(String permission) {
        if (!has("ADMIN") && !has(permission)) throw new AccessDeniedException("Thiếu quyền: " + permission);
    }
}
