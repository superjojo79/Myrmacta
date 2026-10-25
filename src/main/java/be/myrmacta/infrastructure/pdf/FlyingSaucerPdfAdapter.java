package be.myrmacta.infrastructure.pdf;

import be.myrmacta.domain.document.model.DocumentRenderException;
import be.myrmacta.domain.document.port.out.PdfRenderer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.ByteArrayOutputStream;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Component;
import org.xhtmlrenderer.pdf.ITextRenderer;

@Component
public class FlyingSaucerPdfAdapter implements PdfRenderer {

    public static final String PDF_RENDER_TIMER = "flowforge.pdf.render";

    private static final ClasspathFontRegistrar CLASSPATH_FONTS = new ClasspathFontRegistrar();

    private final ThreadLocal<ITextRenderer> renderers = ThreadLocal.withInitial(FlyingSaucerPdfAdapter::newRenderer);
    private final MeterRegistry meterRegistry;
    private final Timer pdfRenderTimer;

    public FlyingSaucerPdfAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.pdfRenderTimer = Timer.builder(PDF_RENDER_TIMER).register(meterRegistry);
    }

    @Override
    public byte[] render(String html) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Objects.requireNonNull(html, "html");
            return doRender(html);
        } finally {
            sample.stop(pdfRenderTimer);
        }
    }

    private byte[] doRender(String html) {
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
        CLASSPATH_FONTS.registerInto(renderer);
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
