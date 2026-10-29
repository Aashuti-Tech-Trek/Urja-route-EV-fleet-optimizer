package com.moveinsync.evfleet.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Resolves the verified, authorized tenant ID from the authenticated SecurityContext.
     * Prevents trusting any unauthenticated client-supplied header.
     */
    public static String getAuthenticatedTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof TenantUserAuthentication tenantAuth) {
            return tenantAuth.getTenantId();
        }
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        throw new IllegalStateException("No authenticated tenant found in security context");
    }

    public static String getAuthenticatedActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "ANONYMOUS";
    }
}
