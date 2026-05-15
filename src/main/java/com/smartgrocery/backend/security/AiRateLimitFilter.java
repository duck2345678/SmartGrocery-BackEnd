package com.smartgrocery.backend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiRateLimitFilter extends OncePerRequestFilter {

    private final ObjectProvider<ProxyManager<byte[]>> aiBucketProxyManagerProvider;

    @Value("${app.ai.rate-limit.window-seconds:60}")
    private long windowSeconds;

    @Value("${app.ai.rate-limit.max-requests:30}")
    private long maxRequests;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/ai/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ProxyManager<byte[]> aiBucketProxyManager = aiBucketProxyManagerProvider.getIfAvailable();
        if (aiBucketProxyManager == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = resolveClientKey(request);
        ConsumptionProbe probe;
        try {
            Bucket bucket = aiBucketProxyManager.builder()
                    .build(key.getBytes(StandardCharsets.UTF_8), () -> newBucketConfiguration(maxRequests, windowSeconds));
            probe = bucket.tryConsumeAndReturnRemaining(1);
        } catch (Exception ex) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!probe.isConsumed()) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            response.setHeader("X-Rate-Limit-Retry-After-Seconds",
                    String.valueOf(Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L)));
            response.getWriter().write("""
                    {"error":"RATE_LIMIT_EXCEEDED","message":"Bạn thao tác quá nhanh, vui lòng thử lại sau ít giây."}
                    """);
            return;
        }

        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            return "user:" + userId;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private BucketConfiguration newBucketConfiguration(long maxRequests, long windowSeconds) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(maxRequests)
                .refillIntervally(maxRequests, Duration.ofSeconds(windowSeconds))
                .build();
        return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
    }
}
