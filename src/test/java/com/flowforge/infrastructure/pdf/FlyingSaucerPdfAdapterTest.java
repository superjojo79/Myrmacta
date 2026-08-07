package com.flowforge.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FlyingSaucerPdfAdapterTest {

    private final FlyingSaucerPdfAdapter adapter = new FlyingSaucerPdfAdapter();

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
}
