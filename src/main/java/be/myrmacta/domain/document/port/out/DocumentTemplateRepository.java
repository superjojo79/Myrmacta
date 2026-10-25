package be.myrmacta.domain.document.port.out;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTemplateRepository {

    DocumentTemplate save(DocumentTemplate template);

    Optional<DocumentTemplate> findByIdAndTenantId(UUID id, String tenantId);

    /**
     * @param templateKey optional filter; {@code null} returns all for tenant
     */
    List<DocumentTemplate> listByTenantId(String tenantId, String templateKey);

    default PageResult<DocumentTemplate> pageByTenantId(String tenantId, String templateKey, PageQuery query) {
        return PageResult.slice(listByTenantId(tenantId, templateKey), query);
    }

    /** Next version number for the resolution combination (max+1, or 1). */
    int nextVersion(String tenantId, String templateKey, String variantKey, String locale);

    /** Ranked ACTIVE resolve (scores 1–4). */
    Optional<DocumentTemplate> findBestActive(
            String tenantId, String templateKey, String variantKey, String locale);

    /** Exact ACTIVE match for a combination (used when archiving current ACTIVE). */
    Optional<DocumentTemplate> findActiveExact(
            String tenantId, String templateKey, String variantKey, String locale);

    /**
     * TODO §52 -- ACTIVE templates whose {@code templateKey} is in {@code templateKeys}, scoped to
     * {@code tenantId}. Empty input returns an empty list (caller's responsibility: this method
     * itself does not special-case it, ListDocumentTemplatesForDossierTypeUseCaseImpl does).
     */
    List<DocumentTemplate> findActiveByTemplateKeysIn(String tenantId, List<String> templateKeys);
}
