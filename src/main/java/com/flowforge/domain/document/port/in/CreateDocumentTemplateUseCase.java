package com.flowforge.domain.document.port.in;

import com.flowforge.domain.document.model.DocumentTemplate;

public interface CreateDocumentTemplateUseCase {

    DocumentTemplate create(CreateCommand command);

    record CreateCommand(
            String templateKey,
            String variantKey,
            String locale,
            String label,
            String htmlContent,
            String updatedBy) {}
}
