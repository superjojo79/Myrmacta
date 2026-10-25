package be.myrmacta.domain.document.port.in;

import be.myrmacta.domain.document.model.DocumentTemplate;
import java.util.UUID;

public interface ArchiveDocumentTemplateUseCase {

    DocumentTemplate archive(UUID id, String tenantId, String updatedBy);
}
