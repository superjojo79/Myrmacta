package com.flowforge.domain.document.model;

public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(TemplateResolutionRequest request) {
        super("No ACTIVE template for key=%s variant=%s locale=%s"
                .formatted(request.templateKey(), request.variantKey(), request.locale()));
    }
}
