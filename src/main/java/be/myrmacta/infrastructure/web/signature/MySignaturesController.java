package be.myrmacta.infrastructure.web.signature;

import be.myrmacta.domain.signature.port.in.DecideSignatureUseCase;
import be.myrmacta.domain.signature.port.in.GetSignatureUseCase;
import be.myrmacta.domain.signature.port.in.MySignaturesUseCase;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
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
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/{signerId}/decline")
    @Transactional
    SignatureDecisionResponse decline(
            @PathVariable UUID signerId,
            @RequestBody(required = false) SignatureDeclineRequest body,
            Authentication authentication) {
        return SignatureDecisionResponse.from(useCase.decide(accessContextFactory.user(authentication), signerId,
                DecideSignatureUseCase.Choice.DECLINE, body == null ? null : body.reason()));
    }

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
            boolean simulated) {
        static MySignatureItemResponse from(MySignaturesUseCase.Item item) {
            return new MySignatureItemResponse(item.signerId(), item.dossierId(), item.dossierReference(), item.documentLabel(),
                    item.required(), item.myTurn(), item.deadlineAt(), item.simulated());
        }
    }
}
