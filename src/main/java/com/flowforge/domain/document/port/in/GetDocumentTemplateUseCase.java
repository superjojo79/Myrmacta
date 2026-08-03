package com.flowforge.domain.document.port.in;

import com.flowforge.domain.document.model.DocumentTemplate;
import java.util.UUID;

public interface GetDocumentTemplateUseCase {

    DocumentTemplate get(UUID id, String tenantId);
}
