package com.flowforge.domain.document.usecase;

import com.flowforge.domain.document.DocumentTemplateSupport;
import com.flowforge.domain.document.DocumentVariableFilter;
import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateResolutionRequest;
import com.flowforge.domain.document.port.in.GenerateDocumentUseCase;
import com.flowforge.domain.document.port.in.ResolveDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.HtmlTemplateProcessor;
import com.flowforge.domain.document.port.out.PdfRenderer;
import java.util.Map;
import java.util.Objects;

public final class GenerateDocumentUseCaseImpl implements GenerateDocumentUseCase {

    private final ResolveDocumentTemplateUseCase resolveUseCase;
    private final HtmlTemplateProcessor htmlTemplateProcessor;
    private final PdfRenderer pdfRenderer;

    public GenerateDocumentUseCaseImpl(
            ResolveDocumentTemplateUseCase resolveUseCase,
            HtmlTemplateProcessor htmlTemplateProcessor,
            PdfRenderer pdfRenderer) {
        this.resolveUseCase = Objects.requireNonNull(resolveUseCase, "resolveUseCase");
        this.htmlTemplateProcessor = Objects.requireNonNull(htmlTemplateProcessor, "htmlTemplateProcessor");
        this.pdfRenderer = Objects.requireNonNull(pdfRenderer, "pdfRenderer");
    }

    @Override
    public byte[] generate(GenerateDocumentCommand command) {
        Objects.requireNonNull(command, "command");
        String key = DocumentTemplateSupport.requireText(command.templateKey(), "templateKey");
        String variant = DocumentTemplateSupport.normalizeOptional(command.variantKey());
        String locale = DocumentTemplateSupport.normalizeOptional(command.locale());
        DocumentTemplate template =
                resolveUseCase.resolve(new TemplateResolutionRequest(key, variant, locale));
        Map<String, Object> variables = DocumentVariableFilter.forTemplateMerge(command.variables());
        String html = htmlTemplateProcessor.process(template.htmlContent(), variables);
        return pdfRenderer.render(html);
    }
}
