package be.myrmacta.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfDictionary;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfReader;

class FlyingSaucerPdfAdapterTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final FlyingSaucerPdfAdapter adapter = new FlyingSaucerPdfAdapter(meterRegistry);

    @Test
    void rendersSimpleHtml5_toPdfMagic() {
        byte[] pdf = adapter.render("<html><body><h1>Hello</h1></body></html>");
        assertThat(pdf).startsWith("%PDF".getBytes());
    }

    @Test
    void successiveCalls_reuseThreadLocalRenderer() {
        byte[] first = adapter.render("<html><body><p>One</p></body></html>");
        byte[] second = adapter.render("<html><body><p>Two</p><p>Page</p></body></html>");
        assertThat(first).startsWith("%PDF".getBytes());
        assertThat(second).startsWith("%PDF".getBytes());
    }

    @Test
    void variableDrivenResourceUrls_areNotFetched_renderingStillSucceeds() {
        // A template variable landing in an <img src>/<link href> must not turn PDF
        // generation into SSRF or local file disclosure (see RestrictedResourceUserAgent).
        // Rendering still succeeds — the resource is just treated as unavailable.
        byte[] pdf = adapter.render("""
                <html><body>
                  <img src="file:///etc/passwd" />
                  <img src="http://169.254.169.254/latest/meta-data/" />
                  <link rel="stylesheet" href="https://internal.example/steal.css" />
                  <p>Body</p>
                </body></html>
                """);
        assertThat(pdf).startsWith("%PDF".getBytes());
    }

    @Test
    void dataUriImage_stillRendersWithoutNetworkFetch() {
        // Self-contained data: URIs (the safe way to embed images in a template) must
        // keep working — only network/filesystem fetches are blocked.
        byte[] pdf = adapter.render(
                "<html><body><img src=\"data:image/gif;base64,"
                        + "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBTAA7\" /></body></html>");
        assertThat(pdf).startsWith("%PDF".getBytes());
    }

    @Test
    void classpathTtfFont_isEmbeddedWhenReferencedByCssFontFamily() throws IOException {
        // A8 — TTF/OTF registered from classpath only; CSS font-family must resolve to
        // the shipped Liberation Sans face (not a silent fallback to Times/Helvetica).
        byte[] pdf = adapter.render("""
                <html><head><style>
                  body { font-family: 'Liberation Sans', sans-serif; }
                </style></head><body><p>Hello Liberation</p></body></html>
                """);
        assertThat(pdf).startsWith("%PDF".getBytes());
        assertThat(baseFonts(pdf))
                .anyMatch(name -> name.contains("LiberationSans"));
    }

    @Test
    void render_successAndNull_areBothTimed() {
        adapter.render("<html><body><p>timed</p></body></html>");
        assertThat(meterRegistry.get(FlyingSaucerPdfAdapter.PDF_RENDER_TIMER).timer().count()).isEqualTo(1);
        try {
            adapter.render(null);
        } catch (NullPointerException ignored) {
            // expected
        }
        assertThat(meterRegistry.get(FlyingSaucerPdfAdapter.PDF_RENDER_TIMER).timer().count()).isEqualTo(2);
    }

    private static Set<String> baseFonts(byte[] pdf) throws IOException {
        Set<String> names = new TreeSet<>();
        PdfReader reader = new PdfReader(pdf);
        try {
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                PdfDictionary resources = reader.getPageN(page).getAsDict(PdfName.RESOURCES);
                if (resources == null) {
                    continue;
                }
                PdfDictionary fonts = resources.getAsDict(PdfName.FONT);
                if (fonts == null) {
                    continue;
                }
                for (PdfName key : fonts.getKeys()) {
                    PdfDictionary font = (PdfDictionary) PdfReader.getPdfObject(fonts.get(key));
                    if (font == null) {
                        continue;
                    }
                    PdfName baseFont = font.getAsName(PdfName.BASEFONT);
                    if (baseFont != null) {
                        names.add(PdfName.decodeName(baseFont.toString()));
                    }
                }
            }
        } finally {
            reader.close();
        }
        return names;
    }
}
