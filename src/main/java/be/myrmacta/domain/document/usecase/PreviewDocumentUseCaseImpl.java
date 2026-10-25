package be.myrmacta.domain.document.usecase;

import be.myrmacta.domain.document.DocumentTemplateSupport;
import be.myrmacta.domain.document.DocumentVariableFilter;
import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateResolutionRequest;
import be.myrmacta.domain.document.model.TemplateValidationException;
import be.myrmacta.domain.document.port.in.PreviewDocumentUseCase;
import be.myrmacta.domain.document.port.in.ResolveDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.out.HtmlTemplateProcessor;
import be.myrmacta.domain.document.port.out.PdfRenderer;
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
