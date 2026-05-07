package com.smartgrocery.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserSessionRepository userSessionRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String deviceFingerprint = request.getHeader("X-Device-Fingerprint");
        final String jwt;
        final String userEmail;
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        if (deviceFingerprint == null || deviceFingerprint.isBlank()) {
            sendSessionInvalidResponse(response, "Thiết bị đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
            return;
        }
        jwt = authHeader.substring(7);
        try {
            userEmail = jwtService.extractUsername(jwt);
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                String normalizedFingerprint = deviceFingerprint.trim();
                boolean hasMatchingSession = userSessionRepository
                        .findByUser_IdAndDeviceFingerprintAndRevokedFalseAndExpiresAtAfter(
                                ((com.smartgrocery.backend.entity.User) userDetails).getId(),
                                normalizedFingerprint,
                                LocalDateTime.now()
                        )
                        .isPresent();

                if (jwtService.isTokenValid(jwt, userDetails) && hasMatchingSession) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    sendSessionInvalidResponse(response, "Tài khoản của bạn đã đăng nhập ở thiết bị khác. Vui lòng đăng nhập lại.");
                    return;
                }
            }
        } catch (Exception e) {
            logger.error("Could not set user authentication in security context", e);
            sendSessionInvalidResponse(response, "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void sendSessionInvalidResponse(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        SecurityContextHolder.clearContext();
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        OBJECT_MAPPER.writeValue(response.getWriter(), java.util.Map.of(
                "message", message,
                "success", false
        ));
    }
}
