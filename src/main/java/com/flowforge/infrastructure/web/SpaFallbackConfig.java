package com.flowforge.infrastructure.web;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/** A-SPA-FALLBACK — routes Vue non-API → index.html ; routes /portal/** → portal/index.html (TODO §68). */
@Configuration
public class SpaFallbackConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    /**
     * Fichier statique s'il existe ; sinon repli sur le point d'entrée de l'application concernée.
     * {@code /portal/**} (TODO §68) a son propre build : ne jamais le replier sur le SPA interne
     * authentifié (D-PORTAL-UI-BUILD). {@code /api/**} n'est jamais replié (404 JSON).
     */
    static final class SpaResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (resourcePath.startsWith("api/")) {
                return null;
            }
            if (resourcePath.equals("portal") || resourcePath.startsWith("portal/")) {
                return new ClassPathResource("/static/portal/index.html");
            }
            return new ClassPathResource("/static/index.html");
        }
    }
}
