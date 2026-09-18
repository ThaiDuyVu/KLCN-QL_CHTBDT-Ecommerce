package com.example.backend.auth.service;

import com.example.backend.auth.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AuthenticatedUserPrincipal implements UserDetails {

    private final UUID userId;
    private final String username;
    private final String password;
    private final String displayName;
    private final String roleName;
    private final String status;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthenticatedUserPrincipal(User user, String roleName) {
        this(user, roleName, List.of());
    }

    public AuthenticatedUserPrincipal(User user, String roleName, Collection<String> permissions) {
        this.userId = user.getUserId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.displayName = user.getDisplayName();
        this.roleName = roleName;
        this.status = user.getStatus();
        java.util.Set<String> names = new java.util.HashSet<>(permissions);
        names.add(roleName);
        this.authorities = names.stream().map(SimpleGrantedAuthority::new).toList();
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public boolean isEnabled() { return "ACTIVE".equals(status); }
    @Override
    public boolean isAccountNonLocked() { return !"LOCKED".equals(status); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
