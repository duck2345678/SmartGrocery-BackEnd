package com.smartgrocery.backend.security;

import com.smartgrocery.backend.entity.Role;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserDetailsService userDetailsService;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @Test
    void whenDeviceFingerprintMissing_thenReturnsUnauthorizedWithoutContinuing() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer abc");
        when(request.getHeader("X-Device-Fingerprint")).thenReturn(" ");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void whenSessionFingerprintMismatch_thenReturnsUnauthorized() throws Exception {
        Role role = Role.builder().name("STAFF").build();
        User user = User.builder().id(5L).email("staff@smartgrocery.com").role(role).status("ACTIVE").build();
        UserDetails userDetails = user;

        when(request.getHeader("Authorization")).thenReturn("Bearer abc");
        when(request.getHeader("X-Device-Fingerprint")).thenReturn("device-b");
        when(jwtService.extractUsername("abc")).thenReturn("staff@smartgrocery.com");
        when(userDetailsService.loadUserByUsername("staff@smartgrocery.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("abc", userDetails)).thenReturn(true);
        when(userSessionRepository.findByUser_IdAndDeviceFingerprintAndRevokedFalseAndExpiresAtAfter(eq(5L), eq("device-b"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
        assertFalse(SecurityContextHolder.getContext().getAuthentication() != null && SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
    }
}
