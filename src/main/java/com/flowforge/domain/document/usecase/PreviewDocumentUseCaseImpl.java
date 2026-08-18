package com.flowforge.domain.document.usecase;

import com.flowforge.domain.document.DocumentTemplateSupport;
import com.flowforge.domain.document.DocumentVariableFilter;
import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateResolutionRequest;
import com.flowforge.domain.document.model.TemplateValidationException;
import com.flowforge.domain.document.port.in.PreviewDocumentUseCase;
import com.flowforge.domain.document.port.in.ResolveDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.HtmlTemplateProcessor;
import com.flowforge.domain.document.port.out.PdfRenderer;
import java.util.Map;
import java.util.Objects;

public final class PreviewDocumentUseCaseImpl implements PreviewDocumentUseCase {

    private final ResolveDocumentTemplateUseCase resolveUseCase;
    private final HtmlTemplateProcessor htmlTemplateProcessor;
    private final PdfRenderer pdfRenderer;
    private final int maxHtmlBytes;

    public PreviewDocumentUseCaseImpl(
            ResolveDocumentTemplateUseCase resolveUseCase,
            HtmlTemplateProcessor htmlTemplateProcessor,
            PdfRenderer pdfRenderer,
            int maxHtmlBytes) {
        this.resolveUseCase = Objects.requireNonNull(resolveUseCase, "resolveUseCase");
        this.htmlTemplateProcessor = Objects.requireNonNull(htmlTemplateProcessor, "htmlTemplateProcessor");
        this.pdfRenderer = Objects.requireNonNull(pdfRenderer, "pdfRenderer");
        this.maxHtmlBytes = maxHtmlBytes;
    }

    @Override
    public String previewHtml(PreviewCommand command) {
        return process(command);
    }

    @Override
    public byte[] previewPdf(PreviewCommand command) {
        return pdfRenderer.render(process(command));
    }

    private String process(PreviewCommand command) {
        Objects.requireNonNull(command, "command");
        Map<String, Object> variables = DocumentVariableFilter.forTemplateMerge(command.variables());
        return htmlTemplateProcessor.process(resolveHtml(command), variables);
    }

    private String resolveHtml(PreviewCommand command) {
        if (command.isLive()) {
            DocumentTemplateSupport.assertHtmlSize(command.htmlContent(), maxHtmlBytes);
            return command.htmlContent();
        }
        if (command.templateKey() == null || command.templateKey().isBlank()) {
            throw new TemplateValidationException("htmlContent or templateKey must not be blank");
        }
        String key = DocumentTemplateSupport.requireText(command.templateKey(), "templateKey");
        String variant = DocumentTemplateSupport.normalizeOptional(command.variantKey());
        String locale = DocumentTemplateSupport.normalizeOptional(command.locale());
        DocumentTemplate template = resolveUseCase.resolve(new TemplateResolutionRequest(key, variant, locale));
        return template.htmlContent();
    }
}
