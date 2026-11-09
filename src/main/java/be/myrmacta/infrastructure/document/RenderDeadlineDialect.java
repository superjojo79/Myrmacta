package be.myrmacta.infrastructure.document;

import java.util.Set;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IPreProcessorDialect;
import org.thymeleaf.engine.ITemplateHandler;
import org.thymeleaf.preprocessor.IPreProcessor;
import org.thymeleaf.templatemode.TemplateMode;

/** Dialecte de pré-traitement branché seulement sur les moteurs bornés (issue #43). */
final class RenderDeadlineDialect extends AbstractDialect implements IPreProcessorDialect {

    RenderDeadlineDialect() {
        super("render-deadline");
    }

    @Override
    public int getDialectPreProcessorPrecedence() {
        return 1_000;
    }

    @Override
    public Set<IPreProcessor> getPreProcessors() {
        return Set.of(
                preprocessor(TemplateMode.HTML),
                preprocessor(TemplateMode.TEXT),
                preprocessor(TemplateMode.XML));
    }

    private static IPreProcessor preprocessor(TemplateMode mode) {
        return new IPreProcessor() {
            @Override
            public TemplateMode getTemplateMode() {
                return mode;
            }

            @Override
            public int getPrecedence() {
                return 1_000;
            }

            @Override
            public Class<? extends ITemplateHandler> getHandlerClass() {
                return RenderDeadlineHandler.class;
            }
        };
    }
}
