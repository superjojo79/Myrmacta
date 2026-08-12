package com.flowforge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

class SpaFallbackConfigTest {

    @Test
    void registersCatchAllStaticResourceHandler() {
        SpaFallbackConfig config = new SpaFallbackConfig();
        ResourceHandlerRegistry registry =
                new ResourceHandlerRegistry(new StaticWebApplicationContext(), new MockServletContext());
        assertThatCode(() -> config.addResourceHandlers(registry)).doesNotThrowAnyException();
    }
}
