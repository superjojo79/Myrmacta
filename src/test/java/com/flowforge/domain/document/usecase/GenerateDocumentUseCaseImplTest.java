package com.flowforge.domain.document.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateNotFoundException;
import com.flowforge.domain.document.model.TemplateResolutionRequest;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.in.GenerateDocumentUseCase;
import com.flowforge.domain.document.port.in.ResolveDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.HtmlTemplateProcessor;
import com.flowforge.domain.document.port.out.PdfRenderer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GenerateDocumentUseCaseImplTest {

    @Mock
    ResolveDocumentTemplateUseCase resolveUseCase;

    @Mock
    HtmlTemplateProcessor htmlTemplateProcessor;

    @Mock
    PdfRenderer pdfRenderer;

    @InjectMocks
    GenerateDocumentUseCaseImpl useCase;

    @Test
    void generate_resolvesProcessesAndRenders_filtersPlatformKeys() {
        DocumentTemplate template = template("<p th:text=\"${title}\">x</p>");
        when(resolveUseCase.resolve(any())).thenReturn(template);
        when(htmlTemplateProcessor.process(anyString(), anyMap())).thenReturn("<p>Hello</p>");
        when(pdfRenderer.render("<p>Hello</p>")).thenReturn("%PDF".getBytes());

        byte[] pdf = useCase.generate(new GenerateDocumentUseCase.GenerateDocumentCommand(
                "contrat", null, null, Map.of("title", "Hello", "dossierId", "should-strip")));

        assertThat(pdf).startsWith("%PDF".getBytes());
        verify(htmlTemplateProcessor).process(anyString(), org.mockito.ArgumentMatchers.argThat(map ->
                map.containsKey("title") && !map.containsKey("dossierId")));
    }

    @Test
    void generate_templateMissing_propagates() {
        when(resolveUseCase.resolve(any()))
                .thenThrow(new TemplateNotFoundException(new TemplateResolutionRequest("x", null, null)));
        assertThatThrownBy(() -> useCase.generate(
                        new GenerateDocumentUseCase.GenerateDocumentCommand("x", null, null, Map.of())))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    private static DocumentTemplate template(String html) {
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        return new DocumentTemplate(
                UUID.randomUUID(),
                "contrat",
                null,
                null,
                "label",
                1,
                TemplateStatus.ACTIVE,
                html,
                "tenant_dev",
                now,
                now,
                "admin");
    }
}
