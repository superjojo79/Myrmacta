package be.myrmacta.domain.document.usecase;

import be.myrmacta.domain.document.DocumentTemplateSupport;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.document.port.in.CreateDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.out.DocumentTemplateRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CreateDocumentTemplateUseCaseImpl implements CreateDocumentTemplateUseCase {

    private final String tenantId;
    private final DocumentTemplateRepository repository;
    private final int maxHtmlBytes;

    public CreateDocumentTemplateUseCaseImpl(
            String tenantId, DocumentTemplateRepository repository, int maxHtmlBytes) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.maxHtmlBytes = maxHtmlBytes;
    }

    @Override
    public DocumentTemplate create(CreateCommand command) {
        Objects.requireNonNull(command, "command");
        String key = DocumentTemplateSupport.requireText(command.templateKey(), "templateKey");
        String html = DocumentTemplateSupport.requireText(command.htmlContent(), "htmlContent");
        DocumentTemplateSupport.assertHtmlSize(html, maxHtmlBytes);
        String variant = DocumentTemplateSupport.normalizeOptional(command.variantKey());
        String locale = DocumentTemplateSupport.normalizeOptional(command.locale());
        int version = repository.nextVersion(tenantId, key, variant, locale);
        Instant now = Instant.now();
        String updatedBy = DocumentTemplateSupport.updatedByOrSystem(command.updatedBy());
        return repository.save(new DocumentTemplate(
                UUID.randomUUID(),
                key,
                variant,
                locale,
                command.label(),
                version,
                TemplateStatus.DRAFT,
                html,
                tenantId,
                now,
                now,
                updatedBy));
    }
}
