package com.flowforge.domain.notification.usecase;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.notification.model.EmailAttachment;
import com.flowforge.domain.notification.model.NotificationValidationException;
import com.flowforge.domain.notification.port.in.ResolveEmailAttachmentUseCase;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Issue #183 — contrôles de la pièce jointe d'un e-mail « Notifier », faits à l'émission (dans la transaction du process,
 * donc incident rejouable ou refus 422) :
 * <ul>
 *   <li>D-NOTIF-ATTACH-PRODUCED-DOC — la référence doit être celle d'un élément {@code PRODUCED_DOC} <b>du dossier
 *       courant</b> (même règle que la signature électronique) : une variable posée par un utilisateur ne peut désigner ni
 *       le document d'un autre dossier, ni une pièce déposée, ni un objet arbitraire du stockage ;</li>
 *   <li>contenu lisible, non vide, sous le plafond (D-NOTIF-ATTACH-MAX) et PDF (signature {@code %PDF-}) ;</li>
 *   <li>D-NOTIF-ATTACH-NAME — nom de fichier assaini, extension {@code .pdf} ajoutée si absente.</li>
 * </ul>
 * Les messages nomment l'origine, jamais la référence.
 */
public final class ResolveEmailAttachmentUseCaseImpl implements ResolveEmailAttachmentUseCase {

    static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_FILE_NAME = 100;
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^\\p{L}\\p{N} ._()'-]");

    private final String tenantId;
    private final DossierElementRepository elementRepository;
    private final DocumentStoragePort storage;
    private final long maxBytes;

    public ResolveEmailAttachmentUseCaseImpl(
            String tenantId, DossierElementRepository elementRepository, DocumentStoragePort storage, long maxBytes) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.storage = Objects.requireNonNull(storage, "storage");
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public EmailAttachment resolve(UUID dossierId, String storageRef, String requestedFileName, String origin) {
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(origin, "origin");
        String ref = storageRef == null ? "" : storageRef.strip();
        String label = elementRepository.findByDossierIdOrderByAddedAtAsc(dossierId).stream()
                .filter(e -> e.type() == ElementType.PRODUCED_DOC && !ref.isEmpty() && ref.equals(e.storageRef()))
                .map(e -> e.label())
                .findFirst()
                .orElseThrow(() -> new NotificationValidationException(origin
                        + " is not a produced document of this dossier (generate it, then attach it to the dossier first)"));
        byte[] content;
        try {
            content = storage.retrieve(tenantId, ref);
        } catch (RuntimeException unreadable) {
            throw new NotificationValidationException(origin + ": the document could not be read");
        }
        if (content == null || content.length == 0) {
            throw new NotificationValidationException(origin + ": the document is empty");
        }
        if (content.length > maxBytes) {
            throw new NotificationValidationException(origin + ": the document exceeds " + maxBytes + " bytes");
        }
        if (content.length < PDF_MAGIC.length
                || !Arrays.equals(Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC)) {
            throw new NotificationValidationException(origin + ": the document is not a PDF");
        }
        return new EmailAttachment(ref, fileName(requestedFileName != null ? requestedFileName : label), PDF_CONTENT_TYPE);
    }

    /** Alphabet du nom validé au déploiement ; tout autre caractère devient {@code _}. Jamais vide, jamais un point initial. */
    static String fileName(String raw) {
        String name = NOT_ALLOWED.matcher(raw == null ? "" : raw.strip()).replaceAll("_");
        name = name.replaceFirst("^[. ]+", "");
        if (name.length() > MAX_FILE_NAME) {
            name = name.substring(0, MAX_FILE_NAME).strip();
        }
        if (name.isEmpty()) {
            name = "document";
        }
        return name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? name : name + ".pdf";
    }
}
