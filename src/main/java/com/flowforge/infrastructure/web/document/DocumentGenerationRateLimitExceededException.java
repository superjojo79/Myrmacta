package com.flowforge.infrastructure.web.document;

class DocumentGenerationRateLimitExceededException extends RuntimeException {

    DocumentGenerationRateLimitExceededException(String principal) {
        super("Document generation rate limit exceeded for: " + principal);
    }
}
