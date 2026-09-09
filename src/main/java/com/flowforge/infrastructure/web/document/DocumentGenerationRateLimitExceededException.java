package com.flowforge.infrastructure.web.document;

public class DocumentGenerationRateLimitExceededException extends RuntimeException {

    public DocumentGenerationRateLimitExceededException(String principal) {
        super("Document generation rate limit exceeded for: " + principal);
    }
}
