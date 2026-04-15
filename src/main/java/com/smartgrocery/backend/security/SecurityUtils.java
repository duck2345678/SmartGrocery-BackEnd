package com.smartgrocery.backend.security;

import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.exception.ResourceOwnershipException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user.getId();
        }
        return null;
    }

    public static boolean hasAnyRole(String... roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Set<String> roleSet = Set.of(roles);
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .anyMatch(a -> a.startsWith("ROLE_") && roleSet.contains(a.substring("ROLE_".length())));
    }

    public static void verifyOwnershipOrAdmin(Long targetUserId) {
        Long actorUserId = getCurrentUserId();
        if (actorUserId == null) {
            throw new AccessDeniedException("Access denied");
        }
        if (targetUserId == null) {
            throw new AccessDeniedException("Access denied");
        }
        if (actorUserId.equals(targetUserId)) {
            return;
        }
        if (hasAnyRole("ADMIN", "STAFF")) {
            return;
        }
        throw new ResourceOwnershipException(actorUserId, targetUserId, "User", targetUserId);
    }

    public static void verifyResourceOwnerOrAdmin(Long resourceOwnerUserId, String resourceType, Long resourceId) {
        Long actorUserId = getCurrentUserId();
        if (actorUserId == null) {
            throw new AccessDeniedException("Access denied");
        }
        if (resourceOwnerUserId == null) {
            throw new AccessDeniedException("Access denied");
        }
        if (actorUserId.equals(resourceOwnerUserId)) {
            return;
        }
        if (hasAnyRole("ADMIN", "STAFF")) {
            return;
        }
        throw new ResourceOwnershipException(actorUserId, resourceOwnerUserId, resourceType, resourceId);
    }
}
