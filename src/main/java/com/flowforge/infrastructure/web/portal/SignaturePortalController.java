package com.flowforge.infrastructure.web.portal;

import com.flowforge.domain.signature.port.in.DecideSignatureUseCase;
import com.flowforge.domain.signature.port.in.GetSignatureUseCase;
import java.time.Instant;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §72 — API publique de signature, servie par la même chaîne anonyme que le portail
 * ({@code portalSecurityFilterChain}, {@code /api/portal/**}) : aucune route hors {@code /portal/**}.
 * Le jeton n'arrive que par {@value PortalController#TOKEN_HEADER} ; aucun identifiant de demande, de
 * signataire ni de dossier n'est accepté en entrée (anti-IDOR). La limite par IP passe avant toute
 * requête en base ({@link PortalPreAuthInterceptor}). Tout refus produit le même 404
 * ({@link PortalExceptionHandler}).
 */
@RestController
@RequestMapping("/api/portal/signature")
@ConditionalOnExpression(
        "'${app.portal.enabled:false}' == 'true' && '${app.signature.provider:none}' != 'none'")
class SignaturePortalController {

    private final GetSignatureUseCase getUseCase;
    private final DecideSignatureUseCase decideUseCase;

    SignaturePortalController(GetSignatureUseCase getUseCase, DecideSignatureUseCase decideUseCase) {
        this.getUseCase = Objects.requireNonNull(getUseCase, "getUseCase");
        this.decideUseCase = Objects.requireNonNull(decideUseCase, "decideUseCase");
    }

    @GetMapping
    @Transactional(readOnly = true)
    SignatureContextResponse context(@RequestHeader(name = PortalController.TOKEN_HEADER, required = false) String token) {
        return SignatureContextResponse.from(getUseCase.context(token));
    }

    @GetMapping("/document")
    @Transactional(readOnly = true)
    ResponseEntity<byte[]> document(
            @RequestHeader(name = PortalController.TOKEN_HEADER, required = false) String token) {
        GetSignatureUseCase.DocumentContent document = getUseCase.document(token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(document.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(document.content());
    }

    @PostMapping("/sign")
    @Transactional
    SignatureDecisionResponse sign(@RequestHeader(name = PortalController.TOKEN_HEADER, required = false) String token) {
        return SignatureDecisionResponse.from(decideUseCase.decideByToken(token, DecideSignatureUseCase.Choice.SIGN, null));
    }

    @PostMapping("/decline")
    @Transactional
    SignatureDecisionResponse decline(
            @RequestHeader(name = PortalController.TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) SignatureDeclineRequest body) {
        return SignatureDecisionResponse.from(decideUseCase.decideByToken(
                token, DecideSignatureUseCase.Choice.DECLINE, body == null ? null : body.reason()));
    }

    record SignatureDeclineRequest(String reason) {}

    /** Le signataire ne voit que sa propre décision, jamais le statut des autres ni de la demande. */
    record SignatureDecisionResponse(String status) {
        static SignatureDecisionResponse from(DecideSignatureUseCase.Result result) {
            return new SignatureDecisionResponse(result.signerStatus().name());
        }
    }

    record SignatureContextResponse(
            String dossierReference,
            String documentLabel,
            String signerName,
            boolean required,
            Instant deadlineAt,
            boolean simulated) {
        static SignatureContextResponse from(GetSignatureUseCase.Context context) {
            return new SignatureContextResponse(context.dossierReference(), context.documentLabel(), context.signerName(),
                    context.required(), context.deadlineAt(), context.simulated());
        }
    }
}
