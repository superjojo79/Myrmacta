package com.flowforge.infrastructure.pdf;

import com.flowforge.domain.document.model.DocumentRenderException;
import com.flowforge.domain.document.port.out.PdfRenderer;
import java.io.ByteArrayOutputStream;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Component;
import org.xhtmlrenderer.pdf.ITextRenderer;

@Component
public class FlyingSaucerPdfAdapter implements PdfRenderer {

    private final ThreadLocal<ITextRenderer> renderers = ThreadLocal.withInitial(FlyingSaucerPdfAdapter::newRenderer);

    @Override
    public byte[] render(String html) {
        Objects.requireNonNull(html, "html");
        try {
            String xhtml = toXhtml(html);
            ITextRenderer renderer = renderers.get();
            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (DocumentRenderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DocumentRenderException("PDF rendering failed: " + ex.getMessage(), ex);
        }
    }

    private static ITextRenderer newRenderer() {
        ITextRenderer renderer = new ITextRenderer();
        renderer.getSharedContext()
                .setUserAgentCallback(new RestrictedResourceUserAgent(renderer.getOutputDevice()));
        return renderer;
    }

    static String toXhtml(String html) {
        Document document = Jsoup.parse(html);
        document.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml);
        return document.html();
    }
}
