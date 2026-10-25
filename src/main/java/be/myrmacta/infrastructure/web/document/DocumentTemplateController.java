package be.myrmacta.infrastructure.web.document;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateResolutionRequest;
import be.myrmacta.domain.document.port.in.ActivateDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.ArchiveDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.CreateDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.GetDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.ListDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.NewDocumentTemplateVersionUseCase;
import be.myrmacta.domain.document.port.in.ResolveDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.UpdateDocumentTemplateUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.web.ListSorts;
import be.myrmacta.infrastructure.web.PageQueryParser;
import be.myrmacta.infrastructure.web.PageResponse;
import be.myrmacta.infrastructure.web.document.DocumentTemplateDtos.CreateRequest;
import be.myrmacta.infrastructure.web.document.DocumentTemplateDtos.TemplateResponse;
import be.myrmacta.infrastructure.web.document.DocumentTemplateDtos.UpdateRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/document-templates")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class DocumentTemplateController {

    private final TenantProperties tenantProperties;
    private final CreateDocumentTemplateUseCase createUseCase;
    private final UpdateDocumentTemplateUseCase updateUseCase;
    private final ActivateDocumentTemplateUseCase activateUseCase;
    private final ArchiveDocumentTemplateUseCase archiveUseCase;
    private final NewDocumentTemplateVersionUseCase newVersionUseCase;
    private final ListDocumentTemplateUseCase listUseCase;
    private final GetDocumentTemplateUseCase getUseCase;
    private final ResolveDocumentTemplateUseCase resolveUseCase;

    public DocumentTemplateController(
            TenantProperties tenantProperties,
            CreateDocumentTemplateUseCase createUseCase,
            UpdateDocumentTemplateUseCase updateUseCase,
            ActivateDocumentTemplateUseCase activateUseCase,
            ArchiveDocumentTemplateUseCase archiveUseCase,
            NewDocumentTemplateVersionUseCase newVersionUseCase,
            ListDocumentTemplateUseCase listUseCase,
            GetDocumentTemplateUseCase getUseCase,
            ResolveDocumentTemplateUseCase resolveUseCase) {
        this.tenantProperties = tenantProperties;
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.activateUseCase = activateUseCase;
        this.archiveUseCase = archiveUseCase;
        this.newVersionUseCase = newVersionUseCase;
        this.listUseCase = listUseCase;
        this.getUseCase = getUseCase;
        this.resolveUseCase = resolveUseCase;
    }

    @GetMapping
    public PageResponse<TemplateResponse> list(
            @RequestParam(required = false) String templateKey,
            @RequestParam(defaultValue = "false") boolean includeContent,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return PageResponse.from(listUseCase
                .list(
                        tenantProperties.id(),
                        templateKey,
                        includeContent,
                        PageQueryParser.parse(page, size, sort, ListSorts.DOCUMENT_TEMPLATES))
                .map(TemplateResponse::from));
    }

    @GetMapping("/resolve")
    public TemplateResponse resolve(
            @RequestParam String templateKey,
            @RequestParam(required = false) String variantKey,
            @RequestParam(required = false) String locale) {
        DocumentTemplate resolved =
                resolveUseCase.resolve(new TemplateResolutionRequest(templateKey, variantKey, locale));
        return TemplateResponse.from(resolved);
    }

    @GetMapping("/{id}")
    public TemplateResponse get(@PathVariable UUID id) {
        return TemplateResponse.from(getUseCase.get(id, tenantProperties.id()));
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> create(
            @RequestBody CreateRequest request, Authentication authentication) {
        DocumentTemplate created = createUseCase.create(new CreateDocumentTemplateUseCase.CreateCommand(
                request.templateKey(),
                request.variantKey(),
                request.locale(),
                request.label(),
                request.htmlContent(),
                principalName(authentication)));
        return ResponseEntity
                .created(URI.create("/api/document-templates/" + created.id()))
                .body(TemplateResponse.from(created));
    }

    @PatchMapping("/{id}")
    public TemplateResponse update(
            @PathVariable UUID id, @RequestBody UpdateRequest request, Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        DocumentTemplate updated = updateUseCase.update(new UpdateDocumentTemplateUseCase.UpdateCommand(
                id, request.label(), request.htmlContent(), principalName(authentication)));
        return TemplateResponse.from(updated);
    }

    @PostMapping("/{id}/activate")
    public TemplateResponse activate(@PathVariable UUID id, Authentication authentication) {
        return TemplateResponse.from(
                activateUseCase.activate(id, tenantProperties.id(), principalName(authentication)));
    }

    @PostMapping("/{id}/archive")
    public TemplateResponse archive(@PathVariable UUID id, Authentication authentication) {
        return TemplateResponse.from(
                archiveUseCase.archive(id, tenantProperties.id(), principalName(authentication)));
    }

    @PostMapping("/{id}/new-version")
    public ResponseEntity<TemplateResponse> newVersion(
            @PathVariable UUID id, Authentication authentication) {
        DocumentTemplate draft =
                newVersionUseCase.newVersion(id, tenantProperties.id(), principalName(authentication));
        return ResponseEntity
                .created(URI.create("/api/document-templates/" + draft.id()))
                .body(TemplateResponse.from(draft));
    }

    private static String principalName(Authentication authentication) {
        return authentication == null || authentication.getName() == null || authentication.getName().isBlank()
                ? "system"
                : authentication.getName();
    }
}
