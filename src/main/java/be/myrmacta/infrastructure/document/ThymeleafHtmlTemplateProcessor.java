package be.myrmacta.infrastructure.document;

import be.myrmacta.domain.document.model.TemplateProcessingException;
import be.myrmacta.domain.document.port.out.HtmlTemplateProcessor;
import be.myrmacta.infrastructure.config.DocumentTemplateCostProperties;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.unbescape.html.HtmlEscape;

@Component
public class ThymeleafHtmlTemplateProcessor implements HtmlTemplateProcessor {

    private final TemplateEngine templateEngine;
    private final ExpressionLimits limits;

    public ThymeleafHtmlTemplateProcessor() {
        this(DocumentTemplateCostProperties.defaults());
    }

    @Autowired
    public ThymeleafHtmlTemplateProcessor(DocumentTemplateCostProperties properties) {
        Objects.requireNonNull(properties, "properties");
        this.limits = new ExpressionLimits(
                properties.utilityMaxSize(),
                properties.stringMaxChars(),
                properties.renderBudget(),
                properties.renderTimeoutMillis());
        this.templateEngine = SafeHtmlTemplateEngineFactory.htmlStringEngine(limits);
    }

    @Override
    public String process(String htmlContent, Map<String, Object> variables) {
        Objects.requireNonNull(htmlContent, "htmlContent");
        if (ThymeleafPreprocessing.containsExecutable(HtmlEscape.unescapeHtml(htmlContent))) {
            throw new TemplateProcessingException(
                    "Failed to process Thymeleaf template: the Thymeleaf pre-processing sequence '__' is not allowed");
        }
        try (ExpressionLimits.Scope ignored = limits.begin()) {
            Context context = new Context();
            if (variables != null) {
                context.setVariables(variables);
            }
            StringWriter out = new StringWriter();
            templateEngine.process(htmlContent, context, new ResetOnWrite(out));
            return out.toString();
        } catch (RuntimeException ex) {
            throw new TemplateProcessingException(
                    "Failed to process Thymeleaf template: " + ex.getMessage(), ex);
        } catch (StackOverflowError | OutOfMemoryError ex) {
            throw new TemplateProcessingException(
                    "Failed to process Thymeleaf template: the template is too expensive to render", ex);
        }
    }

    /** Une écriture sépare deux expressions : le {@code +} suivant ne reprend pas le total de la précédente. */
    private static final class ResetOnWrite extends Writer {
        private final Writer delegate;

        private ResetOnWrite(Writer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            ExpressionLimits.resetMaterialized();
            delegate.write(buffer, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
