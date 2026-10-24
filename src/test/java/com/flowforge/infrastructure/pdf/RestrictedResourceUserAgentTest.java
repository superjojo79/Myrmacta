package com.flowforge.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.xhtmlrenderer.pdf.ITextRenderer;

class RestrictedResourceUserAgentTest {

    private final RestrictedResourceUserAgent agent =
            new RestrictedResourceUserAgent(new ITextRenderer().getOutputDevice());

    @Test
    void blocksHttpAndHttpsAndFileUris() {
        // data: URI image embedding is covered end-to-end by
        // FlyingSaucerPdfAdapterTest#dataUriImage_stillRendersWithoutNetworkFetch — it does
        // not go through resolveAndOpenStream, so it is not exercised here.
        assertThat(agent.resolveAndOpenStream("http://169.254.169.254/latest/meta-data/")).isNull();
        assertThat(agent.resolveAndOpenStream("https://internal.example/secret")).isNull();
        assertThat(agent.resolveAndOpenStream("file:///etc/passwd")).isNull();
    }

    /** Recette riverain (2026-10-23) : la feuille par défaut du moteur passe, à l'URL exacte ; tout autre jar/classpath reste bloqué. */
    @Test
    void onlyTheEngineDefaultStylesheetIsReadableFromTheClasspath() throws Exception {
        java.net.URL css = ITextRenderer.class.getResource("/resources/css/XhtmlNamespaceHandler.css");
        assertThat(css).isNotNull();
        try (java.io.InputStream in = agent.resolveAndOpenStream(css.toString())) {
            assertThat(in).isNotNull();
        }
        java.net.URL other = ITextRenderer.class.getResource("/application.yml");
        assertThat(other).isNotNull();
        assertThat(agent.resolveAndOpenStream(other.toString())).isNull();
        assertThat(agent.resolveAndOpenStream(css.toString() + "?x")).isNull();
        assertThat(agent.resolveAndOpenStream("classpath:/resources/css/XhtmlNamespaceHandler.css")).isNull();
    }
}
