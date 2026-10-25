package be.myrmacta.domain.document.port.in;

import be.myrmacta.domain.document.model.DocumentTemplate;

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
