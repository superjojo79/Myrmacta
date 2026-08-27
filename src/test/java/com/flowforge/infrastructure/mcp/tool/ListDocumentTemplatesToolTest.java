package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.in.ListDocumentTemplateUseCase;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListDocumentTemplatesToolTest {

    private static final String TENANT = "tenant_dev";

    private ListDocumentTemplateUseCase listDocumentTemplateUseCase;
    private ListDocumentTemplatesTool tool;

    @BeforeEach
    void setUp() {
        listDocumentTemplateUseCase = mock(ListDocumentTemplateUseCase.class);
        tool = new ListDocumentTemplatesTool(listDocumentTemplateUseCase, new TenantProperties(TENANT));
    }

    @Test
    void includeContentTrue_isRejectedWithoutCallingUseCase() {
        assertThatThrownBy(() -> tool.listDocumentTemplates("tpl", true, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(listDocumentTemplateUseCase);
    }

    @Test
    void includeContentAbsent_defaultsToFalse() {
        DocumentTemplate template = template();
        when(listDocumentTemplateUseCase.list(eq(TENANT), eq("tpl"), eq(false), eq(PageQuery.of(0, 20))))
                .thenReturn(PageResult.of(List.of(template), 0, 20, 1));

        PageResult<DocumentTemplate> result = tool.listDocumentTemplates("tpl", null, null, null);

        assertThat(result.content()).containsExactly(template);
        verify(listDocumentTemplateUseCase).list(TENANT, "tpl", false, PageQuery.of(0, 20));
    }

    @Test
    void includeContentFalseExplicit_isAccepted() {
        when(listDocumentTemplateUseCase.list(eq(TENANT), eq(null), eq(false), eq(PageQuery.of(0, 20))))
                .thenReturn(PageResult.empty(0, 20));

        tool.listDocumentTemplates(null, false, null, null);

        verify(listDocumentTemplateUseCase).list(TENANT, null, false, PageQuery.of(0, 20));
    }

    private static DocumentTemplate template() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new DocumentTemplate(
                UUID.randomUUID(), "tpl", "default", "fr", "Label", 1, TemplateStatus.ACTIVE, "",
                TENANT, now, now, "creator");
    }
}
