package be.myrmacta.domain.document.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateNotFoundException;
import be.myrmacta.domain.document.model.TemplateResolutionRequest;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.document.model.TemplateValidationException;
import be.myrmacta.domain.document.port.in.PreviewDocumentUseCase.PreviewCommand;
import be.myrmacta.domain.document.port.in.ResolveDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.out.HtmlTemplateProcessor;
import be.myrmacta.domain.document.port.out.PdfRenderer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreviewDocumentUseCaseImplTest {

    private static final int MAX_HTML_BYTES = 1_024;

    @Mock
    ResolveDocumentTemplateUseCase resolveUseCase;

    @Mock
    HtmlTemplateProcessor htmlTemplateProcessor;

    @Mock
    PdfRenderer pdfRenderer;

    private PreviewDocumentUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new PreviewDocumentUseCaseImpl(
                resolveUseCase, htmlTemplateProcessor, pdfRenderer, MAX_HTML_BYTES);
    }

    @Test
    void isLive_whenHtmlContentNonBlank() {
        assertThat(new PreviewCommand("<p>x</p>", "key", null, null, Map.of()).isLive()).isTrue();
        assertThat(new PreviewCommand("   ", "key", null, null, Map.of()).isLive()).isFalse();
        assertThat(new PreviewCommand(null, "key", null, null, Map.of()).isLive()).isFalse();
    }

    @Test
    void previewHtml_live_processesWithoutResolve() {
        when(htmlTemplateProcessor.process(eq("<p th:text=\"${title}\">x</p>"), anyMap()))
                .thenReturn("<p>Hello</p>");

        String html = useCase.previewHtml(liveCommand("<p th:text=\"${title}\">x</p>", Map.of("title", "Hello")));

        assertThat(html).isEqualTo("<p>Hello</p>");
        verify(resolveUseCase, never()).resolve(any());
    }

    @Test
    void previewHtml_liveWinsOverTemplateKey_doesNotResolve() {
        when(htmlTemplateProcessor.process(anyString(), anyMap())).thenReturn("<p>live</p>");

        useCase.previewHtml(new PreviewCommand("<p>live</p>", "contrat", null, null, Map.of()));

        verify(resolveUseCase, never()).resolve(any());
    }

    @Test
    void previewHtml_templateKeyOnly_resolvesAndProcessesTemplateHtml() {
        DocumentTemplate template = template("<p>from-store</p>");
        when(resolveUseCase.resolve(any())).thenReturn(template);
        when(htmlTemplateProcessor.process(eq("<p>from-store</p>"), anyMap())).thenReturn("<p>merged</p>");

        String html = useCase.previewHtml(keyCommand("contrat", Map.of("title", "Hello")));

        assertThat(html).isEqualTo("<p>merged</p>");
        verify(resolveUseCase).resolve(new TemplateResolutionRequest("contrat", null, null));
    }

    @Test
    void previewHtml_bothBlank_throwsValidation() {
        assertThatThrownBy(() -> useCase.previewHtml(new PreviewCommand("  ", "  ", null, null, Map.of())))
                .isInstanceOf(TemplateValidationException.class)
                .hasMessageContaining("htmlContent or templateKey");
        verify(resolveUseCase, never()).resolve(any());
        verify(htmlTemplateProcessor, never()).process(anyString(), anyMap());
    }

    @Test
    void previewHtml_missingTemplate_propagates() {
        when(resolveUseCase.resolve(any()))
                .thenThrow(new TemplateNotFoundException(new TemplateResolutionRequest("x", null, null)));

        assertThatThrownBy(() -> useCase.previewHtml(keyCommand("x", Map.of())))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void previewPdf_rendersEvaluatedHtml_notCssWrapper() {
        when(htmlTemplateProcessor.process(anyString(), anyMap())).thenReturn("<p>Hello</p>");
        when(pdfRenderer.render("<p>Hello</p>")).thenReturn("%PDF".getBytes());

        byte[] pdf = useCase.previewPdf(liveCommand("<p th:text=\"${title}\">x</p>", Map.of("title", "Hello")));

        assertThat(pdf).startsWith("%PDF".getBytes());
        verify(pdfRenderer).render("<p>Hello</p>");
        verify(pdfRenderer, never()).render(org.mockito.ArgumentMatchers.contains("210mm"));
    }

    @Test
    void previewHtml_filtersPlatformKeys() {
        when(htmlTemplateProcessor.process(anyString(), anyMap())).thenReturn("<p>ok</p>");

        useCase.previewHtml(liveCommand("<p>x</p>", Map.of("title", "Hello", "dossierId", "should-strip")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(htmlTemplateProcessor).process(anyString(), vars.capture());
        assertThat(vars.getValue()).containsKey("title").doesNotContainKey("dossierId");
    }

    @Test
    void previewHtml_nullVariables_processesEmptyMap() {
        when(htmlTemplateProcessor.process(anyString(), anyMap())).thenReturn("<p>ok</p>");

        useCase.previewHtml(liveCommand("<p>x</p>", null));

        verify(htmlTemplateProcessor).process(anyString(), eq(Map.of()));
    }

    @Test
    void previewHtml_liveHtmlExceedsMaxBytes_throwsValidation() {
        String oversized = "x".repeat(MAX_HTML_BYTES + 1);

        assertThatThrownBy(() -> useCase.previewHtml(liveCommand(oversized, Map.of())))
                .isInstanceOf(TemplateValidationException.class)
                .hasMessageContaining("htmlContent exceeds");
        verify(htmlTemplateProcessor, never()).process(anyString(), anyMap());
        verify(resolveUseCase, never()).resolve(any());
    }

    @Test
    void previewHtml_blankHtmlWithTemplateKey_usesResolve() {
        DocumentTemplate template = template("<p>stored</p>");
        when(resolveUseCase.resolve(any())).thenReturn(template);
        when(htmlTemplateProcessor.process(eq("<p>stored</p>"), anyMap())).thenReturn("<p>ok</p>");

        String html = useCase.previewHtml(new PreviewCommand("   ", "contrat", null, null, Map.of()));

        assertThat(html).isEqualTo("<p>ok</p>");
        verify(resolveUseCase).resolve(any());
    }

    private static PreviewCommand liveCommand(String html, Map<String, Object> variables) {
        return new PreviewCommand(html, null, null, null, variables);
    }

    private static PreviewCommand keyCommand(String templateKey, Map<String, Object> variables) {
        return new PreviewCommand(null, templateKey, null, null, variables);
    }

    private static DocumentTemplate template(String html) {
        Instant now = Instant.parse("2026-08-17T12:00:00Z");
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
