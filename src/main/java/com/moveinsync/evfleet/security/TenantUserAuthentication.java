package com.moveinsync.evfleet.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public final class TenantUserAuthentication implements Authentication {

    private final String username;
    private final String tenantId;
    private final Set<GrantedAuthority> authorities;
    private boolean authenticated = true;

    public TenantUserAuthentication(String username, String tenantId, Set<GrantedAuthority> authorities) {
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.authorities = authorities != null ? Set.copyOf(authorities) : Set.of();
    }

    public String getTenantId() {
        return tenantId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return "[PROTECTED]";
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return username;
    }
}
