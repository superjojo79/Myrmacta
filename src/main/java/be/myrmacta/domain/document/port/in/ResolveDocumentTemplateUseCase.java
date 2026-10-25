package be.myrmacta.domain.document.port.in;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateResolutionRequest;

public interface ResolveDocumentTemplateUseCase {

    DocumentTemplate resolve(TemplateResolutionRequest request);
}
