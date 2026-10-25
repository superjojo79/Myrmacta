package be.myrmacta.infrastructure.web.dossier;

import be.myrmacta.domain.dossier.AttachmentContentSniffer;
import be.myrmacta.domain.dossier.AttachmentPieceTypes;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierConflictException;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierElementView;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.model.InvalidDossierElementException;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.dossier.port.in.ClassifyDossierAttachmentUseCase;
import be.myrmacta.domain.dossier.port.out.AttachmentScanPort;
import be.myrmacta.domain.dossier.port.out.AttachmentScanResult;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.infrastructure.config.AttachmentUploadProperties;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.security.SecurityAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Dedicated multipart entry point for {@link ElementType#ATTACHMENT} — see design decision n°1. */
@RestController
@RequestMapping("/api/dossiers")
public class DossierElementUploadController {

    private final DocumentStoragePort documentStoragePort;
    private final AddDossierElementUseCase addDossierElementUseCase;
    private final TenantProperties tenantProperties;
    private final AccessContextFactory accessContextFactory;
    private final AttachmentUploadProperties uploadProperties;
    private final DossierAccessGuard accessGuard;
    private final AttachmentScanPort attachmentScanPort;
    private final AttachmentPieceTypes pieceTypes;
    private final ClassifyDossierAttachmentUseCase classifyUseCase;

    public DossierElementUploadController(
            DocumentStoragePort documentStoragePort,
            AddDossierElementUseCase addDossierElementUseCase,
            TenantProperties tenantProperties,
            AccessContextFactory accessContextFactory,
            AttachmentUploadProperties uploadProperties,
            DossierAccessGuard accessGuard,
            AttachmentScanPort attachmentScanPort,
            AttachmentPieceTypes pieceTypes,
            ClassifyDossierAttachmentUseCase classifyUseCase) {
        this.documentStoragePort = documentStoragePort;
        this.addDossierElementUseCase = addDossierElementUseCase;
        this.tenantProperties = tenantProperties;
        this.accessContextFactory = accessContextFactory;
        this.uploadProperties = uploadProperties;
        this.accessGuard = accessGuard;
        this.attachmentScanPort = attachmentScanPort;
        this.pieceTypes = pieceTypes;
        this.classifyUseCase = classifyUseCase;
    }

    @PostMapping(path = "/{id}/elements/attachment", consumes = "multipart/form-data")
    public ResponseEntity<DossierElementView> upload(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("label") String label,
            @RequestParam(value = "pieceKey", required = false) String pieceKey,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        // ACL and status checked before any storage write, so a caller without EDIT on this
        // dossier's type, or targeting a CLOTURE/ARCHIVE dossier, never causes an object to be
        // stored just to be rejected afterwards. AddDossierElementUseCase re-checks both
        // internally, which costs one extra findById + checks — accepted trade-off, not a second
        // source of truth (same policy, same rule, same DossierStatus.blocksMutation()).
        Dossier dossier = accessGuard.requireEditable(id, actor);
        if (dossier.status().blocksMutation()) {
            throw new DossierConflictException("Dossier elements cannot be added in status " + dossier.status());
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidDossierElementException("file is required");
        }
        // TODO §106 (D-PIECE-CLASSIFY) — type facultatif, vérifié avant toute écriture dans le stockage.
        String piece = pieceKey == null || pieceKey.isBlank() ? null : pieceKey.strip();
        if (piece != null) {
            pieceTypes.require(dossier, piece);
        }
        String contentType = file.getContentType();
        if (!uploadProperties.isAllowed(contentType)) {
            throw new InvalidDossierElementException("content type not allowed: " + contentType);
        }
        byte[] content = readBytes(file);
        if (!AttachmentContentSniffer.matchesDeclaredType(content, contentType)) {
            throw new InvalidDossierElementException("file content does not match declared type: " + contentType);
        }
        AttachmentScanResult scanResult = attachmentScanPort.scan(content, contentType);
        if (!scanResult.clean()) {
            throw new InvalidDossierElementException("attachment rejected: " + scanResult.reason());
        }

        String storageRef = documentStoragePort.store(tenantProperties.id(), contentType, content);
        Map<String, Object> metadata = new HashMap<>(Map.of(
                "filename", file.getOriginalFilename() == null ? "" : file.getOriginalFilename(),
                "contentType", contentType,
                "sizeBytes", file.getSize()));
        if (piece != null) {
            metadata.put(PieceType.METADATA_KEY, piece);
        }

        DossierElement element = addDossierElementUseCase.add(
                new AddDossierElementUseCase.AddCommand(
                        id, ElementType.ATTACHMENT, label, storageRef, metadata, actor.userId(), null),
                actor);
        return ResponseEntity
                .created(URI.create("/api/dossiers/" + id + "/elements/" + element.id()))
                .body(DossierElementView.from(element));
    }

    /**
     * TODO §106 (D-PIECE-CLASSIFY) — attribuer, changer ou retirer ({@code pieceKey} absent ou {@code null}) le type
     * d'une pièce jointe. EDIT sur le type du dossier ; tracé dans le journal de sécurité (clés seulement).
     */
    @PutMapping(path = "/{id}/elements/{elementId}/piece-type", consumes = "application/json")
    public DossierElementView classify(
            @PathVariable UUID id,
            @PathVariable UUID elementId,
            @RequestBody(required = false) ClassifyAttachmentRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        String pieceKey = request == null ? null : request.pieceKey();
        ClassifyDossierAttachmentUseCase.Result result = classifyUseCase.classify(id, elementId, pieceKey, actor);
        SecurityAuditLog.attachmentClassified(httpRequest, authentication, id, elementId, result.previousPieceKey(),
                result.element().metadata().get(PieceType.METADATA_KEY) instanceof String now ? now : null);
        return DossierElementView.from(result.element());
    }

    record ClassifyAttachmentRequest(String pieceKey) {}

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new InvalidDossierElementException("Failed to read uploaded file: " + ex.getMessage());
        }
    }
}
