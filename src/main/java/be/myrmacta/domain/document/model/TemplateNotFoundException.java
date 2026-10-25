package be.myrmacta.domain.document.model;

import java.util.UUID;

public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(TemplateResolutionRequest request) {
        super("No ACTIVE template for key=%s variant=%s locale=%s"
                .formatted(request.templateKey(), request.variantKey(), request.locale()));
    }

    public TemplateNotFoundException(UUID id) {
        super("Document template not found: " + id);
    }
}
