package com.flowforge.domain.document.usecase;

import com.flowforge.domain.document.model.TemplateValidationException;

final class DocumentTemplateSupport {

    private DocumentTemplateSupport() {}

    static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new TemplateValidationException(field + " must not be blank");
        }
        return value.trim();
    }

    static void assertHtmlSize(String html, int maxBytes) {
        if (html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes) {
            throw new TemplateValidationException("htmlContent exceeds " + maxBytes + " bytes");
        }
    }

    static String updatedByOrSystem(String updatedBy) {
        return updatedBy == null || updatedBy.isBlank() ? "system" : updatedBy.trim();
    }
}
