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
}
