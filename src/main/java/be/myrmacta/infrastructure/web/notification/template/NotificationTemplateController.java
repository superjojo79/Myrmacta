package be.myrmacta.infrastructure.web.notification.template;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import be.myrmacta.domain.notification.template.model.TemplateResolutionKey;
import be.myrmacta.domain.notification.template.port.in.ActivateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.ArchiveNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.GetNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.ListNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.NewNotificationTemplateVersionUseCase;
import be.myrmacta.domain.notification.template.port.in.PreviewNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.ResolveNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.UpdateNotificationTemplateUseCase;
import be.myrmacta.infrastructure.config.NotificationTemplateProperties;
import be.myrmacta.infrastructure.web.ListSorts;
import be.myrmacta.infrastructure.web.PageQueryParser;
import be.myrmacta.infrastructure.web.PageResponse;
import be.myrmacta.infrastructure.web.notification.template.NotificationTemplateDtos.NotificationTemplateCreateRequest;
import be.myrmacta.infrastructure.web.notification.template.NotificationTemplateDtos.NotificationTemplatePreviewContent;
import be.myrmacta.infrastructure.web.notification.template.NotificationTemplateDtos.NotificationTemplatePreviewRequest;
import be.myrmacta.infrastructure.web.notification.template.NotificationTemplateDtos.NotificationTemplatePreviewResponse;
import be.myrmacta.infrastructure.web.notification.template.NotificationTemplateDtos.NotificationTemplateResponse;
import be.myrmacta.infrastructure.web.notification.template.NotificationTemplateDtos.NotificationTemplateUpdateRequest;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * API d'administration des gabarits de notification (TODO §123), réservée au {@code TENANT_ADMIN} comme
 * {@code /api/document-templates}.
 */
@RestController
@RequestMapping("/api/notification-templates")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class NotificationTemplateController {

    private final CreateNotificationTemplateUseCase createUseCase;
    private final UpdateNotificationTemplateUseCase updateUseCase;
    private final ActivateNotificationTemplateUseCase activateUseCase;
    private final ArchiveNotificationTemplateUseCase archiveUseCase;
    private final NewNotificationTemplateVersionUseCase newVersionUseCase;
    private final ListNotificationTemplateUseCase listUseCase;
    private final GetNotificationTemplateUseCase getUseCase;
    private final ResolveNotificationTemplateUseCase resolveUseCase;
    private final PreviewNotificationTemplateUseCase previewUseCase;
    private final NotificationTemplateProperties properties;
    private final ObjectMapper objectMapper;

    public NotificationTemplateController(
            CreateNotificationTemplateUseCase createUseCase,
            UpdateNotificationTemplateUseCase updateUseCase,
            ActivateNotificationTemplateUseCase activateUseCase,
            ArchiveNotificationTemplateUseCase archiveUseCase,
            NewNotificationTemplateVersionUseCase newVersionUseCase,
            ListNotificationTemplateUseCase listUseCase,
            GetNotificationTemplateUseCase getUseCase,
            ResolveNotificationTemplateUseCase resolveUseCase,
            PreviewNotificationTemplateUseCase previewUseCase,
            NotificationTemplateProperties properties,
            ObjectMapper objectMapper) {
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.activateUseCase = activateUseCase;
        this.archiveUseCase = archiveUseCase;
        this.newVersionUseCase = newVersionUseCase;
        this.listUseCase = listUseCase;
        this.getUseCase = getUseCase;
        this.resolveUseCase = resolveUseCase;
        this.previewUseCase = previewUseCase;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Liste paginée, sans les corps (le détail les porte). */
    @GetMapping
    public PageResponse<NotificationTemplateResponse> listNotificationTemplates(
            @RequestParam(required = false) String templateKey,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return PageResponse.from(listUseCase
                .list(templateKey, PageQueryParser.parse(page, size, sort, ListSorts.NOTIFICATION_TEMPLATES))
                .map(NotificationTemplateResponse::from));
    }

    @GetMapping("/resolve")
    public NotificationTemplateResponse resolveNotificationTemplate(
            @RequestParam String templateKey,
            @RequestParam(required = false) String variantKey,
            @RequestParam(required = false) String locale) {
        return NotificationTemplateResponse.from(
                resolveUseCase.resolve(new TemplateResolutionKey(templateKey, variantKey, locale)));
    }

    @GetMapping("/{id}")
    public NotificationTemplateResponse getNotificationTemplate(@PathVariable UUID id) {
        return NotificationTemplateResponse.from(getUseCase.get(id));
    }

    @PostMapping
    public ResponseEntity<NotificationTemplateResponse> createNotificationTemplate(
            @RequestBody NotificationTemplateCreateRequest request, Authentication authentication) {
        requireBody(request);
        NotificationTemplate created = createUseCase.create(new CreateNotificationTemplateUseCase.CreateCommand(
                request.templateKey(),
                request.variantKey(),
                request.locale(),
                request.label(),
                request.subject(),
                request.textBody(),
                request.htmlBody(),
                principalName(authentication)));
        return ResponseEntity.created(URI.create("/api/notification-templates/" + created.id()))
                .body(NotificationTemplateResponse.from(created));
    }

    @PutMapping("/{id}")
    public NotificationTemplateResponse updateNotificationTemplate(
            @PathVariable UUID id, @RequestBody NotificationTemplateUpdateRequest request, Authentication authentication) {
        requireBody(request);
        return NotificationTemplateResponse.from(updateUseCase.update(new UpdateNotificationTemplateUseCase.UpdateCommand(
                id,
                request.label(),
                request.subject(),
                request.textBody(),
                request.htmlBody(),
                principalName(authentication))));
    }

    @PostMapping("/{id}/activate")
    public NotificationTemplateResponse activateNotificationTemplate(
            @PathVariable UUID id, Authentication authentication) {
        return NotificationTemplateResponse.from(activateUseCase.activate(id, principalName(authentication)));
    }

    @PostMapping("/{id}/archive")
    public NotificationTemplateResponse archiveNotificationTemplate(
            @PathVariable UUID id, Authentication authentication) {
        return NotificationTemplateResponse.from(archiveUseCase.archive(id, principalName(authentication)));
    }

    @PostMapping("/{id}/new-version")
    public ResponseEntity<NotificationTemplateResponse> newNotificationTemplateVersion(
            @PathVariable UUID id, Authentication authentication) {
        NotificationTemplate draft = newVersionUseCase.newVersion(id, principalName(authentication));
        return ResponseEntity.created(URI.create("/api/notification-templates/" + draft.id()))
                .body(NotificationTemplateResponse.from(draft));
    }

    /**
     * Rend un gabarit avec un modèle d'exemple : par identifiant (brouillon compris) ou par contenu. Le modèle est
     * borné ; le rendu est strict comme à l'envoi.
     */
    @PostMapping("/preview")
    public NotificationTemplatePreviewResponse previewNotificationTemplate(
            @RequestBody NotificationTemplatePreviewRequest request) {
        requireBody(request);
        Map<String, Object> model = request.model() == null ? Map.of() : request.model();
        assertModelSize(model);
        NotificationTemplatePreviewContent content = request.template();
        return NotificationTemplatePreviewResponse.from(previewUseCase.preview(new PreviewNotificationTemplateUseCase.PreviewCommand(
                request.id(), subjectOf(content), textOf(content), htmlOf(content), model)));
    }

    private void assertModelSize(Map<String, Object> model) {
        int size;
        try {
            size = objectMapper.writeValueAsBytes(model).length;
        } catch (JacksonException ex) {
            throw new NotificationTemplateValidationException("model", "must be valid JSON");
        }
        if (size > properties.previewModelMaxBytes()) {
            throw new NotificationTemplateValidationException(
                    "model", "exceeds " + properties.previewModelMaxBytes() + " bytes");
        }
    }

    private static String subjectOf(NotificationTemplatePreviewContent content) {
        return content == null ? null : content.subject();
    }

    private static String textOf(NotificationTemplatePreviewContent content) {
        return content == null ? null : content.textBody();
    }

    private static String htmlOf(NotificationTemplatePreviewContent content) {
        return content == null ? null : content.htmlBody();
    }

    private static void requireBody(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
    }

    private static String principalName(Authentication authentication) {
        return authentication == null || authentication.getName() == null || authentication.getName().isBlank()
                ? "system"
                : authentication.getName();
    }
}
