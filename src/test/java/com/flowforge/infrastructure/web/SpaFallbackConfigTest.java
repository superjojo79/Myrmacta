package com.flowforge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

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
}
