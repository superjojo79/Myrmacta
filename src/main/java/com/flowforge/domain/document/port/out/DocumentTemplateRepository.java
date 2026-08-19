package com.flowforge.domain.document.port.out;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
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
}
