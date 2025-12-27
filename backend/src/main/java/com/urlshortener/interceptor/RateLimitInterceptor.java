package com.urlshortener.interceptor;

import com.urlshortener.exception.RateLimitExceededException;
import com.urlshortener.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rate limiting interceptor for redirect endpoints.
 * Uses sliding window algorithm with Redis backend.
 * Limit: 100 requests per minute per IP.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;

    @Autowired
    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientIp = getClientIp(request);

        if (!rateLimitService.isAllowed(clientIp)) {
            // Add rate limit headers
            int remaining = rateLimitService.getRemainingRequests(clientIp);
            long resetTime = rateLimitService.getResetTimeSeconds(clientIp);

            response.setHeader("X-RateLimit-Limit", "100");
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));
            response.setHeader("Retry-After", String.valueOf(resetTime));

            throw new RateLimitExceededException(clientIp);
        }

        // Add rate limit headers for successful requests
        int remaining = rateLimitService.getRemainingRequests(clientIp);
        long resetTime = rateLimitService.getResetTimeSeconds(clientIp);

        response.setHeader("X-RateLimit-Limit", "100");
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        if (resetTime > 0) {
            response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));
        }

        return true;
    }

    /**
     * Extract client IP, handling proxies and load balancers.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}

