package com.flowforge.infrastructure.web.document;

import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Sliding one-minute-window rate limiter for {@code POST /api/documents/generate},
 * {@code POST /api/documents/preview/pdf}, and the MCP {@code generate_document} tool
 * ({@code infrastructure.mcp.tool.GenerateDocumentTool}, A3 fix — same counter space as the REST
 * endpoint so an M2M client cannot bypass the limit by calling MCP instead of REST), keyed by the
 * authenticated caller.
 */
@Component
public class DocumentGenerationRateLimiter {

    private final SlidingWindowRateLimiter delegate;

    public DocumentGenerationRateLimiter() {
        this(Clock.systemUTC());
    }

    public DocumentGenerationRateLimiter(Clock clock) {
        this.delegate = new SlidingWindowRateLimiter(clock);
    }

    public boolean tryConsume(String principal, int limitPerMinute) {
        return delegate.tryConsume(principal, limitPerMinute);
    }
}
