package be.myrmacta.infrastructure.web.portal;

import be.myrmacta.domain.workflow.portal.model.PortalCredential;
import be.myrmacta.domain.workflow.portal.port.in.CompletePortalIdentityUseCase;
import be.myrmacta.domain.workflow.portal.port.in.StartPortalIdentityUseCase;
import be.myrmacta.infrastructure.config.PortalProperties;
import be.myrmacta.infrastructure.portal.identity.PortalIdentityKeys;
import be.myrmacta.infrastructure.security.ClientIpResolver;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §104 (issue #210, D-BEAUTH-PORTAL-CLIENT, D-BEAUTH-PROOF) — authentification forte d'un tiers, dans la chaîne
 * publique du portail (sans session ni cookie) :
 * <ul>
 *   <li>{@code POST /start} : jeton du lien en en-tête, fournisseur choisi ; renvoie l'URL d'autorisation ;</li>
 *   <li>{@code GET /callback} : retour du fournisseur ; redirige vers la page du portail avec la preuve d'identité dans
 *       le fragment ({@code #identity:<preuve>}, jamais envoyé au serveur ni dans le {@code Referer}), ou vers
 *       {@code #identity-refused}. Le motif réel ne va qu'au journal, sans NRN ;</li>
 *   <li>{@code GET /jwks} : clés publiques de l'instance, enregistrées chez le fournisseur ({@code private_key_jwt}).</li>
 * </ul>
 * Présent seulement si l'authentification forte est activée ; limite par IP de {@link PortalPreAuthInterceptor}.
 */
@Hidden
@RestController
@RequestMapping("/api/portal/identity")
@ConditionalOnProperty(prefix = "app.portal.strong-auth", name = "enabled", havingValue = "true")
class PortalIdentityController {

    static final String REFUSED_METRIC = "flowforge.portal.identity.refused";
    private static final Logger log = LoggerFactory.getLogger(PortalIdentityController.class);
    private static final MediaType JWK_SET = MediaType.parseMediaType("application/jwk-set+json");

    private final StartPortalIdentityUseCase startUseCase;
    private final CompletePortalIdentityUseCase completeUseCase;
    private final PortalIdentityKeys keys;
    private final PortalProperties portalProperties;
    private final MeterRegistry meterRegistry;
    private final ClientIpResolver clientIpResolver;

    PortalIdentityController(
            StartPortalIdentityUseCase startUseCase,
            CompletePortalIdentityUseCase completeUseCase,
            PortalIdentityKeys keys,
            PortalProperties portalProperties,
            MeterRegistry meterRegistry,
            ClientIpResolver clientIpResolver) {
        this.startUseCase = Objects.requireNonNull(startUseCase, "startUseCase");
        this.completeUseCase = Objects.requireNonNull(completeUseCase, "completeUseCase");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.portalProperties = Objects.requireNonNull(portalProperties, "portalProperties");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver");
    }

    @PostMapping("/start")
    @Transactional
    ResponseEntity<StartResponse> start(
            @RequestHeader(name = PortalController.TOKEN_HEADER, required = false) String token,
            @RequestBody StartRequest body) {
        URI authorization = startUseCase.start(PortalCredential.of(token, null), body == null ? null : body.provider());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new StartResponse(authorization.toString()));
    }

    @GetMapping("/callback")
    @Transactional
    ResponseEntity<Void> callback(
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "error", required = false) String error,
            HttpServletRequest request) {
        CompletePortalIdentityUseCase.Outcome outcome = completeUseCase.complete(state, code, error);
        String fragment;
        if (outcome.isVerified()) {
            fragment = "#identity:" + outcome.proof();
        } else {
            meterRegistry.counter(REFUSED_METRIC).increment();
            log.info("Portal identity refused from {}: {}", sanitize(clientIpResolver.resolve(request)),
                    sanitize(outcome.refusal()));
            fragment = "#identity-refused";
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, portalProperties.publicBaseUrl() + "/portal/" + fragment)
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @GetMapping("/jwks")
    ResponseEntity<String> jwks() {
        return ResponseEntity.ok().contentType(JWK_SET).body(keys.publicJwkSetJson());
    }

    record StartRequest(String provider) {}

    record StartResponse(String authorizationUrl) {}

    private static String sanitize(String value) {
        return value == null ? "unknown" : value.replaceAll("[\\r\\n]", "_");
    }
}
