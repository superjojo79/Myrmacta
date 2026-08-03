package com.flowforge.domain.document.port.in;

import com.flowforge.domain.document.model.DocumentTemplate;
import java.util.UUID;

public interface UpdateDocumentTemplateUseCase {

    DocumentTemplate update(UpdateCommand command);

    record UpdateCommand(UUID id, String label, String htmlContent, String updatedBy) {}
}
