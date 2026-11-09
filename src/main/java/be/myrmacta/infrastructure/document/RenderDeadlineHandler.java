package be.myrmacta.infrastructure.document;

import org.thymeleaf.engine.AbstractTemplateHandler;
import org.thymeleaf.model.ICDATASection;
import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IComment;
import org.thymeleaf.model.IDocType;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IProcessingInstruction;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.ITemplateEnd;
import org.thymeleaf.model.ITemplateStart;
import org.thymeleaf.model.IText;
import org.thymeleaf.model.IXMLDeclaration;

/**
 * Vérifie l'échéance du rendu à chaque événement du gabarit, y compris un {@code th:each} dont le corps n'évalue
 * rien (issue #43). Instancié par réflexion : constructeur public sans argument.
 */
public final class RenderDeadlineHandler extends AbstractTemplateHandler {

    public RenderDeadlineHandler() {}

    @Override
    public void handleTemplateStart(ITemplateStart model) {
        tick();
        super.handleTemplateStart(model);
    }

    @Override
    public void handleTemplateEnd(ITemplateEnd model) {
        tick();
        super.handleTemplateEnd(model);
    }

    @Override
    public void handleXMLDeclaration(IXMLDeclaration model) {
        tick();
        super.handleXMLDeclaration(model);
    }

    @Override
    public void handleDocType(IDocType model) {
        tick();
        super.handleDocType(model);
    }

    @Override
    public void handleCDATASection(ICDATASection model) {
        tick();
        super.handleCDATASection(model);
    }

    @Override
    public void handleComment(IComment model) {
        tick();
        super.handleComment(model);
    }

    @Override
    public void handleText(IText model) {
        tick();
        super.handleText(model);
    }

    @Override
    public void handleStandaloneElement(IStandaloneElementTag model) {
        tick();
        super.handleStandaloneElement(model);
    }

    @Override
    public void handleOpenElement(IOpenElementTag model) {
        tick();
        super.handleOpenElement(model);
    }

    @Override
    public void handleCloseElement(ICloseElementTag model) {
        tick();
        super.handleCloseElement(model);
    }

    @Override
    public void handleProcessingInstruction(IProcessingInstruction model) {
        tick();
        super.handleProcessingInstruction(model);
    }

    private static void tick() {
        ExpressionLimits.resetMaterialized();
        ExpressionLimits.checkDeadline();
    }
}
