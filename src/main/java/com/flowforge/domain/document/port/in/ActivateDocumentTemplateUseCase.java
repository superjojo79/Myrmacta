package com.flowforge.domain.document.port.in;

import com.flowforge.domain.document.model.DocumentTemplate;
import java.util.UUID;

public interface ActivateDocumentTemplateUseCase {

    DocumentTemplate activate(UUID id, String tenantId, String updatedBy);
}
