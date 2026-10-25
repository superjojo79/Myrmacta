package com.flowforge.infrastructure.web.portal;

import com.flowforge.domain.workflow.portal.model.PortalCredential;
import com.flowforge.domain.workflow.portal.port.in.AddPortalAttachmentUseCase;
import com.flowforge.domain.workflow.portal.port.in.GetPortalTaskUseCase;
import com.flowforge.domain.workflow.portal.port.in.SubmitPortalTaskUseCase;
import io.swagger.v3.oas.annotations.Hidden;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
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
 * TODO §68 — API publique du portail, servie par la chaîne anonyme {@code portalSecurityFilterChain}.
 * Le jeton n'arrive que par l'en-tête {@value #TOKEN_HEADER} (jamais en query string ni en cookie,
 * D-PORTAL-CSRF-SESSION) ; aucun identifiant de dossier, de tâche ni de pièce n'est accepté en
 * entrée — tout est dérivé du lien (anti-IDOR). Le rate-limit par IP passe avant toute requête en
 * base et avant la lecture du corps ({@link PortalPreAuthInterceptor}). Le jeton n'est jamais
 * journalisé.
 */
@RestController
@RequestMapping("/api/portal/task")
@ConditionalOnProperty(prefix = "app.portal", name = "enabled", havingValue = "true")
class PortalController {

    static final String TOKEN_HEADER = "X-Portal-Token";
    /** TODO §104 (D-BEAUTH-PROOF) — preuve d'identité, qui remplace le jeton après une authentification forte. */
    static final String IDENTITY_HEADER = "X-Portal-Identity";

    private final GetPortalTaskUseCase getUseCase;
    private final AddPortalAttachmentUseCase attachmentUseCase;
    private final SubmitPortalTaskUseCase submitUseCase;

    PortalController(
            GetPortalTaskUseCase getUseCase,
            AddPortalAttachmentUseCase attachmentUseCase,
            SubmitPortalTaskUseCase submitUseCase) {
        this.getUseCase = Objects.requireNonNull(getUseCase, "getUseCase");
        this.attachmentUseCase = Objects.requireNonNull(attachmentUseCase, "attachmentUseCase");
        this.submitUseCase = Objects.requireNonNull(submitUseCase, "submitUseCase");
    }

    @GetMapping
    @Transactional
    PortalTaskResponse get(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = IDENTITY_HEADER, required = false) String proof) {
        return PortalTaskResponse.from(getUseCase.get(PortalCredential.of(token, proof)));
    }

    @Hidden
    @TokenCheckedBeforeBody
    @PostMapping(path = "/attachments", consumes = "multipart/form-data")
    @Transactional
    ResponseEntity<PortalTaskResponse.Attachment> addAttachment(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = IDENTITY_HEADER, required = false) String proof,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "label", required = false) String label) {
        var element = attachmentUseCase.add(PortalCredential.of(token, proof), new AddPortalAttachmentUseCase.AttachmentUpload(
                label, file.getOriginalFilename(), file.getContentType(), bytes(file)));
        return ResponseEntity.status(HttpStatus.CREATED).body(PortalTaskResponse.Attachment.from(element));
    }

    @PostMapping("/submit")
    @Transactional
    ResponseEntity<Void> submit(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = IDENTITY_HEADER, required = false) String proof,
            @RequestBody SubmitRequest body) {
        submitUseCase.submit(PortalCredential.of(token, proof), body == null ? Map.of() : body.answers());
        return ResponseEntity.noContent().build();
    }

    /**
     * F-PORTAL-1 — route dont le corps peut être volumineux : {@link PortalPreAuthInterceptor} exige
     * un jeton vivant avant que Spring ne lise le corps. La limite par IP s'applique, elle, à toutes
     * les routes de ce contrôleur.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface TokenCheckedBeforeBody {}

    record SubmitRequest(Map<String, Object> answers) {
        SubmitRequest {
            answers = answers == null ? Map.of() : answers;
        }
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
