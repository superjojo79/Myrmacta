package be.myrmacta.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;
import org.springframework.web.util.HtmlUtils;

/** A-SPA-FALLBACK — routes Vue non-API → index.html ; routes /portal/** → portal/index.html (TODO §68). */
@Configuration
public class SpaFallbackConfig implements WebMvcConfigurer {

    private final String contextPath;

    public SpaFallbackConfig(Environment environment) {
        this.contextPath = environment.getProperty("server.servlet.context-path", "");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaResourceResolver())
                .addTransformer(new SpaBaseHrefTransformer(contextPath));
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

    /**
     * DD-BASE — un seul build pour toutes les instances ({@code base: './'}) : le {@code <base href="/">}
     * d'index.html devient {@code <base href="{context-path}/">}. Les assets relatifs, le routeur et le
     * préfixe API du SPA se résolvent alors contre la racine de l'instance, jamais contre la route
     * demandée (un rechargement de {@code /tenant_a/admin/types} chargeait sinon
     * {@code /tenant_a/admin/assets/…}, repli index.html : page blanche). Le context-path est celui de
     * l'instance (décision n° 14), fixe : compatible avec le cache de la resource chain. Seul index.html
     * portant la balise est modifié (le portail n'en a pas).
     */
    static final class SpaBaseHrefTransformer implements ResourceTransformer {

        private static final Pattern ROOT_BASE = Pattern.compile("<base href=\"/\"(\\s*/?>)");

        private final String baseHref;

        SpaBaseHrefTransformer(String contextPath) {
            String path = contextPath == null ? "" : contextPath.strip();
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            this.baseHref = path.isEmpty() ? null : HtmlUtils.htmlEscape(path) + "/";
        }

        @Override
        public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
                throws IOException {
            Resource transformed = chain.transform(request, resource);
            if (baseHref == null || !"index.html".equals(transformed.getFilename())) {
                return transformed;
            }
            String html = transformed.getContentAsString(StandardCharsets.UTF_8);
            Matcher matcher = ROOT_BASE.matcher(html);
            if (!matcher.find()) {
                return transformed;
            }
            String rewritten = matcher.replaceFirst(
                    Matcher.quoteReplacement("<base href=\"" + baseHref + "\"" + matcher.group(1)));
            return new TransformedResource(transformed, rewritten.getBytes(StandardCharsets.UTF_8));
        }
    }
}
