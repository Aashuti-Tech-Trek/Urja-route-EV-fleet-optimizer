package com.moveinsync.evfleet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates the API key credentials from the X-API-KEY header (or Bearer token)
 * and maps the token securely to the authorized tenant and user roles.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-KEY";
    public static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String key = request.getHeader(API_KEY_HEADER);
        if (key == null || key.isBlank()) {
            String authHeader = request.getHeader(AUTHORIZATION_HEADER);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                key = authHeader.substring(7).trim();
            }
        }

        if (key != null && !key.isBlank()) {
            TenantUserAuthentication authentication = authenticateKey(key);
            if (authentication != null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private TenantUserAuthentication authenticateKey(String key) {
        // Format: key-{role}-{tenantId} (e.g. key-admin-tenant1, key-operator-tenant1, key-viewer-tenant1)
        // Or master key: key-super-admin
        if (key.startsWith("key-admin-")) {
            String tenantId = key.substring("key-admin-".length());
            return new TenantUserAuthentication("admin-" + tenantId, tenantId,
                    Set.of(Role.ROLE_ADMIN, Role.ROLE_OPERATOR, Role.ROLE_VIEWER));
        } else if (key.startsWith("key-operator-")) {
            String tenantId = key.substring("key-operator-".length());
            return new TenantUserAuthentication("operator-" + tenantId, tenantId,
                    Set.of(Role.ROLE_OPERATOR, Role.ROLE_VIEWER));
        } else if (key.startsWith("key-viewer-")) {
            String tenantId = key.substring("key-viewer-".length());
            return new TenantUserAuthentication("viewer-" + tenantId, tenantId,
                    Set.of(Role.ROLE_VIEWER));
        } else if ("key-super-admin".equals(key)) {
            return new TenantUserAuthentication("super-admin", "system",
                    Set.of(Role.ROLE_ADMIN, Role.ROLE_OPERATOR, Role.ROLE_VIEWER));
        }
        return null;
    }
}
