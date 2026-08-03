package com.flowforge.domain.document.port.in;

import com.flowforge.domain.document.model.DocumentTemplate;
import java.util.List;

public interface ListDocumentTemplateUseCase {

    List<DocumentTemplate> list(String tenantId, String templateKey, boolean includeContent);
}
