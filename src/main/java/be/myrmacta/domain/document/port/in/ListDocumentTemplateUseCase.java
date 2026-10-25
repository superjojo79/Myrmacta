package be.myrmacta.domain.document.port.in;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;

public interface ListDocumentTemplateUseCase {

    PageResult<DocumentTemplate> list(
            String tenantId, String templateKey, boolean includeContent, PageQuery query);
}
