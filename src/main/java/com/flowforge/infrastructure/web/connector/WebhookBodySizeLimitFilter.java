package com.flowforge.infrastructure.web.connector;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects oversized webhook requests via {@code Content-Length} before Spring reads the body
 * into memory. {@code WebhookController} also checks the materialized {@code byte[]} length,
 * but by then an attacker-controlled payload has already been fully buffered — on this
 * anonymous, internet-facing chain that alone is a memory-exhaustion vector. This filter closes
 * the common case (an honest, oversized {@code Content-Length}); a request that lies about its
 * length via chunked transfer-encoding still reaches the app-level check.
 */
public class WebhookBodySizeLimitFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;

    public WebhookBodySizeLimitFilter(long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > maxBodyBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
