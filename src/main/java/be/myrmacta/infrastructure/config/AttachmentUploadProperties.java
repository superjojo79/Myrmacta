package be.myrmacta.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MIME allowlist for {@code ATTACHMENT} uploads. Size is deliberately not duplicated here — the
 * single source of truth is {@code spring.servlet.multipart.max-file-size}/{@code max-request-size}
 * (see application.yml), enforced natively by Spring and mapped to 413 in DossierExceptionHandler.
 */
@ConfigurationProperties(prefix = "app.dossier.attachment")
public record AttachmentUploadProperties(List<String> allowedContentTypes) {

    private static final List<String> DEFAULT_ALLOWED_CONTENT_TYPES = List.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain");

    public AttachmentUploadProperties {
        allowedContentTypes = allowedContentTypes == null || allowedContentTypes.isEmpty()
                ? DEFAULT_ALLOWED_CONTENT_TYPES
                : List.copyOf(allowedContentTypes);
    }

    public boolean isAllowed(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType);
    }
}
