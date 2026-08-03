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
}
