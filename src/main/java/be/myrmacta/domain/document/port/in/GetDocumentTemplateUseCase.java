package be.myrmacta.domain.document.port.in;

import be.myrmacta.domain.document.model.DocumentTemplate;
import java.util.UUID;

public interface GetDocumentTemplateUseCase {

    DocumentTemplate get(UUID id, String tenantId);
}
