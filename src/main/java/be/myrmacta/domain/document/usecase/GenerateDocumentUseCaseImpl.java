package be.myrmacta.domain.document.usecase;

import be.myrmacta.domain.document.DocumentTemplateSupport;
import be.myrmacta.domain.document.DocumentVariableFilter;
import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateResolutionRequest;
import be.myrmacta.domain.document.port.in.GenerateDocumentUseCase;
import be.myrmacta.domain.document.port.in.ResolveDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.out.HtmlTemplateProcessor;
import be.myrmacta.domain.document.port.out.PdfRenderer;
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
