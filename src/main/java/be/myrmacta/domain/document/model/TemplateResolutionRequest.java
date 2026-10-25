package be.myrmacta.domain.document.model;

import java.util.Objects;

public record TemplateResolutionRequest(
        String templateKey,
        String variantKey,
        String locale
) {
    public TemplateResolutionRequest {
        Objects.requireNonNull(templateKey, "templateKey");
    }
}
