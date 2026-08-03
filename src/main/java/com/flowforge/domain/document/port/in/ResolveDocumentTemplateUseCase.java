package com.flowforge.domain.document.port.in;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateResolutionRequest;

public interface ResolveDocumentTemplateUseCase {

    DocumentTemplate resolve(TemplateResolutionRequest request);
}
