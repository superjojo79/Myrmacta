package com.flowforge.domain.document.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.document.model.DocumentTemplate;
import java.util.List;

/**
 * TODO §52 (formulaires-dynamiques-taches) — templates {@code ACTIVE} allowed for the
 * {@code document-generation} task form kind on a given dossier type, restricted to
 * {@link com.flowforge.domain.dossier.model.DossierType#documentTemplateKeys()}. Unlike
 * {@link ListDocumentTemplateUseCase} (admin-only, exposes {@code htmlContent}), this use case is
 * meant for any user who can view the dossier type (same {@code VIEW} check as
 * {@link GetDossierTypeUseCase}), so the caller must never expose template content from its
 * result.
 */
public interface ListDocumentTemplatesForDossierTypeUseCase {

    List<DocumentTemplate> list(String tenantId, String dossierTypeCode, AccessContext ctx);
}
