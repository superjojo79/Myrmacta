package com.flowforge.domain.document.model;

public class InvalidTemplateSyntaxException extends RuntimeException {

    public InvalidTemplateSyntaxException(String message) {
        super(message);
    }

    public InvalidTemplateSyntaxException(String message, Throwable cause) {
        super(message, cause);
    }
}
