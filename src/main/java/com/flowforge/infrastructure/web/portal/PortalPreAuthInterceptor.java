package com.flowforge.infrastructure.web.portal;

import com.flowforge.domain.workflow.portal.PortalAccessResolver;
import com.flowforge.infrastructure.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * F-PORTAL-1 (revue Phase 9) — contrôles pré-authentification du portail exécutés dans
 * {@code preHandle}, c'est-à-dire <strong>avant</strong> la résolution des arguments du
 * contrôleur. Placés dans le corps de la méthode, ils arrivaient trop tard : le paramètre
 * {@code MultipartFile} fait lire et parser tout le corps (jusqu'à la taille maximale d'upload,
 * {@code resolve-lazily} ne diffère que jusqu'à cette résolution), si bien qu'un anonyme sans jeton
 * faisait ingérer un upload complet à chaque requête avant d'obtenir 404 ou 429.
 *
 * <ul>
 *   <li>toutes les routes de {@link PortalController} : limite par IP (T-ACCESS-4), l'IP étant
 *       celle de {@link ClientIpResolver} (C2 : jamais un en-tête que le client contrôle) ;
 *   <li>dépôt de pièce : jeton vivant exigé avant lecture du corps, sans effet de bord
 *       ({@link PortalAccessResolver#assertUsableToken}) — le use case refait le contrôle complet.
 * </ul>
 *
 * <p>Les exceptions levées ici passent par {@link PortalExceptionHandler} (le handler est déjà
 * résolu) : même 404 neutre, même 429.
 */
final class PortalPreAuthInterceptor implements HandlerInterceptor {

    private final PortalPreAuthRateLimiter rateLimiter;
    private final PortalAccessResolver accessResolver;
    private final ClientIpResolver clientIpResolver;

    PortalPreAuthInterceptor(
            PortalPreAuthRateLimiter rateLimiter, PortalAccessResolver accessResolver, ClientIpResolver clientIpResolver) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !isPortalPublicController(method.getBeanType())) {
            return true;
        }
        rateLimiter.consumeOrThrow(clientIpResolver.resolve(request));
        if (method.hasMethodAnnotation(PortalController.TokenCheckedBeforeBody.class)) {
            accessResolver.assertUsableToken(request.getHeader(PortalController.TOKEN_HEADER));
        }
        return true;
    }

    /** Contrôleurs publics du portail : tâche externe et signature (TODO §72) partagent la limite par IP. */
    private static boolean isPortalPublicController(Class<?> beanType) {
        return PortalController.class.equals(beanType) || SignaturePortalController.class.equals(beanType);
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.portal", name = "enabled", havingValue = "true")
    static class Registration implements WebMvcConfigurer {

        private final PortalPreAuthRateLimiter rateLimiter;
        private final PortalAccessResolver accessResolver;
        private final ClientIpResolver clientIpResolver;

        Registration(
                PortalPreAuthRateLimiter rateLimiter,
                PortalAccessResolver accessResolver,
                ClientIpResolver clientIpResolver) {
            this.rateLimiter = rateLimiter;
            this.accessResolver = accessResolver;
            this.clientIpResolver = clientIpResolver;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new PortalPreAuthInterceptor(rateLimiter, accessResolver, clientIpResolver))
                    .addPathPatterns("/api/portal/**");
        }
    }
}
