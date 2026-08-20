package com.flowforge.domain.document.port.in;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;

public interface ListDocumentTemplateUseCase {

    PageResult<DocumentTemplate> list(
            String tenantId, String templateKey, boolean includeContent, PageQuery query);
}
