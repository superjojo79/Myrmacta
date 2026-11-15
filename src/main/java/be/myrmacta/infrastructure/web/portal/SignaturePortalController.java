package be.myrmacta.infrastructure.web.portal;

import be.myrmacta.domain.signature.port.in.CardSignatureUseCase;
import be.myrmacta.domain.signature.port.in.DecideSignatureUseCase;
import be.myrmacta.domain.signature.port.in.GetSignatureUseCase;
import be.myrmacta.domain.signature.port.in.SubmitSignedDocumentUseCase;
import be.myrmacta.infrastructure.web.signature.CardSignatureWire;
import be.myrmacta.infrastructure.web.signature.MultipartSignedUpload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * TODO §72 — API publique de signature, servie par la même chaîne anonyme que le portail
 * ({@code portalSecurityFilterChain}, {@code /api/portal/**}) : aucune route hors {@code /portal/**}.
 * Le jeton n'arrive que par {@value PortalController#TOKEN_HEADER} ; aucun identifiant de demande, de
 * signataire ni de dossier n'est accepté en entrée (anti-IDOR). La limite par IP passe avant toute
 * requête en base ({@link PortalPreAuthInterceptor}). Tout refus produit le même 404
 * ({@link PortalExceptionHandler}).
 *
 * <p>TODO §138 — {@code POST /signed-document} : le signataire téléverse le PDF signé hors de Myrmacta
 * (multipart, partie {@code file}). Comme le dépôt de pièce du portail ({@link PortalController}), il n'est
 * pas transactionnel (la vérification joint le réseau), et {@link PortalPreAuthInterceptor} exige un jeton
 * vivant avant que Spring ne lise le corps.
 */
@RestController
@RequestMapping("/api/portal/signature")
@ConditionalOnExpression(
        "'${app.portal.enabled:false}' == 'true' && '${app.signature.provider:none}' != 'none'")
class SignaturePortalController {

    private final GetSignatureUseCase getUseCase;
    private final DecideSignatureUseCase decideUseCase;
    private final SubmitSignedDocumentUseCase submitUseCase;
    private final CardSignatureUseCase cardUseCase;

    SignaturePortalController(
            GetSignatureUseCase getUseCase,
            DecideSignatureUseCase decideUseCase,
            SubmitSignedDocumentUseCase submitUseCase,
            CardSignatureUseCase cardUseCase) {
        this.cardUseCase = Objects.requireNonNull(cardUseCase, "cardUseCase");
        this.getUseCase = Objects.requireNonNull(getUseCase, "getUseCase");
        this.decideUseCase = Objects.requireNonNull(decideUseCase, "decideUseCase");
        this.submitUseCase = Objects.requireNonNull(submitUseCase, "submitUseCase");
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

    /** Aucune transaction : la vérification peut joindre le réseau, le use case ouvre la sienne pour enregistrer. */
    @TokenCheckedBeforeBody
    @PostMapping(path = "/signed-document", consumes = "multipart/form-data")
    SignatureDecisionResponse signedDocument(
            @RequestHeader(name = PortalController.TOKEN_HEADER, required = false) String token,
            @RequestParam("file") MultipartFile file) {
        return SignatureDecisionResponse.from(submitUseCase.submitByToken(token, new MultipartSignedUpload(file)));
    }

    /** Signature dans la page (TODO §138, D-EID-BRIDGE) : condensé à faire signer par la carte, aucune écriture. */
    @PostMapping(path = "/card-signature/prepare", consumes = "application/json")
    CardSignatureWire.PrepareResponse cardPrepare(
            @RequestHeader(name = PortalController.TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) CardSignatureWire.PrepareRequest body) {
        return CardSignatureWire.PrepareResponse.from(
                cardUseCase.prepareByToken(token, CardSignatureWire.input(body)));
    }

    /** Valeur de signature rendue par la carte : assemblage puis chemin du téléversement. Pas de transaction. */
    @PostMapping(path = "/card-signature/complete", consumes = "application/json")
    SignatureDecisionResponse cardComplete(
            @RequestHeader(name = PortalController.TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) CardSignatureWire.CompleteRequest body) {
        return SignatureDecisionResponse.from(
                cardUseCase.completeByToken(token, CardSignatureWire.completion(body)));
    }

    @PostMapping("/decline")
    @Transactional
    SignatureDecisionResponse decline(
            @RequestHeader(name = PortalController.TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) SignatureDeclineRequest body) {
        return SignatureDecisionResponse.from(decideUseCase.decideByToken(
                token, DecideSignatureUseCase.Choice.DECLINE, body == null ? null : body.reason()));
    }

    /**
     * TODO §138 — route dont le corps peut être volumineux : {@link PortalPreAuthInterceptor} exige un jeton de
     * signature vivant avant que Spring ne lise le corps (la limite par IP s'applique, elle, à toutes les routes).
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface TokenCheckedBeforeBody {}

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
            boolean simulated,
            boolean hosted,
            boolean signedUpload,
            List<String> cardBridges,
            @Schema(nullable = true) String signingUrl) {
        static SignatureContextResponse from(GetSignatureUseCase.Context context) {
            return new SignatureContextResponse(context.dossierReference(), context.documentLabel(), context.signerName(),
                    context.required(), context.deadlineAt(), context.simulated(), context.hosted(),
                    context.signedUpload(), context.cardBridges(), context.signingUrl());
        }
    }
}
