package be.myrmacta.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Content-type allowlist for BPMN diagram uploads. Size is deliberately not duplicated here — the
 * single source of truth is {@code spring.servlet.multipart.max-file-size}/{@code max-request-size}
 * (see application.yml), same convention as {@link AttachmentUploadProperties}.
 */
@ConfigurationProperties(prefix = "app.workflow.bpmn-upload")
public record BpmnUploadProperties(List<String> allowedContentTypes) {

    private static final List<String> DEFAULT_ALLOWED_CONTENT_TYPES = List.of("application/xml", "text/xml");

    public BpmnUploadProperties {
        allowedContentTypes = allowedContentTypes == null || allowedContentTypes.isEmpty()
                ? DEFAULT_ALLOWED_CONTENT_TYPES
                : List.copyOf(allowedContentTypes);
    }

    public boolean isAllowed(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType);
    }
}
