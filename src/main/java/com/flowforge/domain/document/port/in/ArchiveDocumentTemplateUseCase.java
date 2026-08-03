package com.flowforge.domain.document.port.in;

import com.flowforge.domain.document.model.DocumentTemplate;
import java.util.UUID;

public interface ArchiveDocumentTemplateUseCase {

    DocumentTemplate archive(UUID id, String tenantId, String updatedBy);
}
