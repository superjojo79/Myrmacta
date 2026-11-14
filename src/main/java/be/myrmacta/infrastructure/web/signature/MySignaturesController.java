package be.myrmacta.infrastructure.web.signature;

import be.myrmacta.domain.signature.port.in.DecideSignatureUseCase;
import be.myrmacta.domain.signature.port.in.GetSignatureUseCase;
import be.myrmacta.domain.signature.port.in.MySignaturesUseCase;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * TODO §72 US-10 — « Mes signatures » (chaîne BFF authentifiée, CSRF). L'identité vient de la session,
 * jamais du corps : un utilisateur ne voit ni ne signe que les demandes qui lui sont adressées.
 */
@RestController
@RequestMapping("/api/signatures/mine")
@ConditionalOnExpression("'${app.signature.provider:none}' != 'none'")
class MySignaturesController {

    private final MySignaturesUseCase useCase;
    private final AccessContextFactory accessContextFactory;

    MySignaturesController(MySignaturesUseCase useCase, AccessContextFactory accessContextFactory) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<MySignatureItemResponse> listMySignatures(Authentication authentication) {
        return useCase.list(accessContextFactory.user(authentication)).stream().map(MySignatureItemResponse::from).toList();
    }

    /** Lien demandé au clic, hors de la transaction de liste (revue SIG-3). */
    @GetMapping("/{signerId}/signing-url")
    SigningUrlResponse signingUrl(@PathVariable UUID signerId, Authentication authentication) {
        return new SigningUrlResponse(useCase.signingUrl(accessContextFactory.user(authentication), signerId));
    }

    @GetMapping("/{signerId}/document")
    @Transactional(readOnly = true)
    ResponseEntity<byte[]> document(@PathVariable UUID signerId, Authentication authentication) {
        GetSignatureUseCase.DocumentContent document =
                useCase.document(accessContextFactory.user(authentication), signerId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(document.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(document.content());
    }

    @PostMapping("/{signerId}/sign")
    @Transactional
    SignatureDecisionResponse sign(@PathVariable UUID signerId, Authentication authentication) {
        return SignatureDecisionResponse.from(useCase.decide(
                accessContextFactory.user(authentication), signerId, DecideSignatureUseCase.Choice.SIGN, null));
    }

    /**
     * TODO §138 — le signataire téléverse le PDF qu'il a signé hors de Myrmacta (multipart, partie {@code file}).
     * Pas de transaction ici : la vérification joint le réseau, le use case ouvre la sienne pour enregistrer.
     */
    @PostMapping(path = "/{signerId}/signed-document", consumes = "multipart/form-data")
    SignatureDecisionResponse signedDocument(
            @PathVariable UUID signerId, @RequestParam("file") MultipartFile file, Authentication authentication) {
        return SignatureDecisionResponse.from(useCase.submitSigned(
                accessContextFactory.user(authentication), signerId, new MultipartSignedUpload(file)));
    }

    /** Signature dans la page (TODO §138, D-EID-BRIDGE) : condensé à faire signer par la carte, aucune écriture. */
    @PostMapping(path = "/{signerId}/card-signature/prepare", consumes = "application/json")
    CardSignatureWire.PrepareResponse cardPrepare(
            @PathVariable UUID signerId,
            @RequestBody(required = false) CardSignatureWire.PrepareRequest body,
            Authentication authentication) {
        return CardSignatureWire.PrepareResponse.from(useCase.prepareCardSignature(
                accessContextFactory.user(authentication), signerId, CardSignatureWire.input(body)));
    }

    /** Assemblage puis chemin du téléversement : pas de transaction ici, la vérification joint le réseau. */
    @PostMapping(path = "/{signerId}/card-signature/complete", consumes = "application/json")
    SignatureDecisionResponse cardComplete(
            @PathVariable UUID signerId,
            @RequestBody(required = false) CardSignatureWire.CompleteRequest body,
            Authentication authentication) {
        return SignatureDecisionResponse.from(useCase.completeCardSignature(
                accessContextFactory.user(authentication), signerId, CardSignatureWire.completion(body)));
    }

    @PostMapping("/{signerId}/decline")
    @Transactional
    SignatureDecisionResponse decline(
            @PathVariable UUID signerId,
            @RequestBody(required = false) SignatureDeclineRequest body,
            Authentication authentication) {
        return SignatureDecisionResponse.from(useCase.decide(accessContextFactory.user(authentication), signerId,
                DecideSignatureUseCase.Choice.DECLINE, body == null ? null : body.reason()));
    }

    record SigningUrlResponse(@Schema(nullable = true) String signingUrl) {}

    record SignatureDeclineRequest(String reason) {}

    record SignatureDecisionResponse(String status) {
        static SignatureDecisionResponse from(DecideSignatureUseCase.Result result) {
            return new SignatureDecisionResponse(result.signerStatus().name());
        }
    }

    record MySignatureItemResponse(
            UUID signerId,
            UUID dossierId,
            String dossierReference,
            String documentLabel,
            boolean required,
            boolean myTurn,
            Instant deadlineAt,
            boolean simulated,
            boolean hosted,
            boolean signedUpload,
            List<String> cardBridges,
            @Schema(nullable = true) String signingUrl) {
        static MySignatureItemResponse from(MySignaturesUseCase.Item item) {
            return new MySignatureItemResponse(item.signerId(), item.dossierId(), item.dossierReference(), item.documentLabel(),
                    item.required(), item.myTurn(), item.deadlineAt(), item.simulated(), item.hosted(),
                    item.signedUpload(), item.cardBridges(), item.signingUrl());
        }
    }
}
