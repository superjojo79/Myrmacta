package be.myrmacta.domain.document.port.in;

import be.myrmacta.domain.document.model.DocumentTemplate;
import java.util.UUID;

public interface UpdateDocumentTemplateUseCase {

    DocumentTemplate update(UpdateCommand command);

    record UpdateCommand(UUID id, String label, String htmlContent, String updatedBy) {}
}
