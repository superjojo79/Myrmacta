package be.myrmacta.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.ResourceTransformerChain;

/** TODO §68 T-SEC-5 — le repli d'une route /portal/** ne sert jamais le SPA interne. */
class SpaFallbackConfigTest {

    private final SpaFallbackConfig.SpaResourceResolver resolver = new SpaFallbackConfig.SpaResourceResolver();
    private final Resource staticRoot = new ClassPathResource("/does-not-exist/");

    private String fallbackFor(String path) throws IOException {
        Resource resource = resolver.getResource(path, staticRoot);
        return resource == null ? null : ((ClassPathResource) resource).getPath();
    }

    @Test
    void portalRoutes_fallBackToThePortalEntryPoint() throws IOException {
        assertThat(fallbackFor("portal")).isEqualTo("static/portal/index.html");
        assertThat(fallbackFor("portal/")).isEqualTo("static/portal/index.html");
        assertThat(fallbackFor("portal/some/deep/route")).isEqualTo("static/portal/index.html");
    }

    @Test
    void internalRoutes_fallBackToTheInternalSpa_andApiIsNeverFallenBack() throws IOException {
        assertThat(fallbackFor("dossiers/123")).isEqualTo("static/index.html");
        assertThat(fallbackFor("portalx/route")).isEqualTo("static/index.html");
        assertThat(fallbackFor("api/portal/unknown")).isNull();
    }

    // --- DD-BASE : <base href> du SPA = context-path de l'instance ---

    private static final String INDEX =
            "<!doctype html><html><head><meta charset=\"UTF-8\" /><base href=\"/\" />"
                    + "<script type=\"module\" src=\"./assets/index.js\"></script></head></html>";

    private static String served(String contextPath, String filename, String html) throws IOException {
        Resource index = new ByteArrayResource(html.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public long lastModified() {
                return 0L; // lu par TransformedResource, comme sur la ClassPathResource réelle
            }
        };
        ResourceTransformerChain identity = mock(ResourceTransformerChain.class);
        when(identity.transform(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        Resource out = new SpaFallbackConfig.SpaBaseHrefTransformer(contextPath)
                .transform(new MockHttpServletRequest(), index, identity);
        return out.getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void spaIndex_baseHrefIsTheContextPath() throws IOException {
        assertThat(served("/tenant_a", "index.html", INDEX)).contains("<base href=\"/tenant_a/\" />")
                .doesNotContain("<base href=\"/\"");
        assertThat(served("/tenant_a/", "index.html", INDEX)).contains("<base href=\"/tenant_a/\" />");
    }

    @Test
    void spaIndex_atTheRoot_keepsBaseHrefSlash() throws IOException {
        assertThat(served("", "index.html", INDEX)).isEqualTo(INDEX);
        assertThat(served("/", "index.html", INDEX)).isEqualTo(INDEX);
    }

    @Test
    void contextPathIsHtmlEscaped_andOtherResourcesAreUntouched() throws IOException {
        assertThat(served("/a\"b", "index.html", INDEX)).contains("<base href=\"/a&quot;b/\" />");
        assertThat(served("/tenant_a", "index.js", INDEX)).isEqualTo(INDEX);
    }
}
