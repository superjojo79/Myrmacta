package be.myrmacta.domain.workflow.portal.model;

import be.myrmacta.domain.dossier.model.PieceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tâche externe telle que déclarée dans un BPMN (D-PORTAL-BPMN-MARKER) : {@code userTask}
 * portant {@code flowable:portalRecipient}. {@code ttlDays} est {@code null} quand
 * {@code flowable:portalLinkTtlDays} est absent (défaut tenant). {@code identityField} est {@code null} sauf pour une
 * tâche à authentification forte ({@code flowable:portalIdentityField}, TODO §104). {@code pieceTypeKeys} : types de
 * pièces proposés au tiers ({@code flowable:portalPieceTypes}, TODO §106), vide si l'attribut est absent.
 * {@code portalTitle} : libellé citoyen ({@code flowable:portalTitle}, TODO §147), {@code null} si absent (repli sur
 * le {@code name} BPMN).
 */
public record PortalTaskDefinition(
        String taskDefinitionKey, String schemaKey, PortalRecipientSource recipientSource, Integer ttlDays,
        PortalIdentityField identityField, List<String> pieceTypeKeys, String portalTitle) {

    /** TODO §106 — au plus 10 types de pièces proposés au tiers par tâche. */
    public static final int MAX_PIECE_TYPES = 10;

    /** TODO §147 — borne du libellé citoyen (e-mail et portail). */
    public static final int MAX_PORTAL_TITLE_LENGTH = 200;

    public PortalTaskDefinition {
        Objects.requireNonNull(taskDefinitionKey, "taskDefinitionKey");
        Objects.requireNonNull(schemaKey, "schemaKey");
        Objects.requireNonNull(recipientSource, "recipientSource");
        if (ttlDays != null && ttlDays <= 0) {
            throw new IllegalArgumentException("ttlDays must be positive");
        }
        pieceTypeKeys = pieceTypeKeys == null ? List.of() : List.copyOf(pieceTypeKeys);
        portalTitle = normalizeTitle(portalTitle);
    }

    public PortalTaskDefinition(
            String taskDefinitionKey, String schemaKey, PortalRecipientSource recipientSource, Integer ttlDays) {
        this(taskDefinitionKey, schemaKey, recipientSource, ttlDays, null, List.of(), null);
    }

    public PortalTaskDefinition(
            String taskDefinitionKey, String schemaKey, PortalRecipientSource recipientSource, Integer ttlDays,
            PortalIdentityField identityField) {
        this(taskDefinitionKey, schemaKey, recipientSource, ttlDays, identityField, List.of(), null);
    }

    public PortalTaskDefinition(
            String taskDefinitionKey, String schemaKey, PortalRecipientSource recipientSource, Integer ttlDays,
            PortalIdentityField identityField, List<String> pieceTypeKeys) {
        this(taskDefinitionKey, schemaKey, recipientSource, ttlDays, identityField, pieceTypeKeys, null);
    }

    /**
     * TODO §106 (D-PIECE-PORTAL) — {@code flowable:portalPieceTypes} : clés de types de pièces séparées par des virgules,
     * valides et distinctes, {@value #MAX_PIECE_TYPES} au plus. Leur existence sur le type de dossier est vérifiée à
     * l'exécution (le BPMN ne connaît pas le type), une clé inconnue n'est simplement pas proposée.
     */
    public static List<String> parsePieceTypeKeys(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("portalPieceTypes must list at least one piece type key");
        }
        List<String> keys = new ArrayList<>();
        for (String part : raw.split(",")) {
            String key = part.strip();
            if (!PieceType.isValidKey(key)) {
                throw new IllegalArgumentException("portalPieceTypes: invalid piece type key '" + key + "'");
            }
            if (keys.contains(key)) {
                throw new IllegalArgumentException("portalPieceTypes: duplicate piece type key '" + key + "'");
            }
            keys.add(key);
        }
        if (keys.size() > MAX_PIECE_TYPES) {
            throw new IllegalArgumentException("portalPieceTypes: at most " + MAX_PIECE_TYPES + " piece types");
        }
        return List.copyOf(keys);
    }

    /** TODO §147 — {@code flowable:portalTitle} : une ligne, {@value #MAX_PORTAL_TITLE_LENGTH} caractères au plus. */
    public static String parsePortalTitle(String raw) {
        String title = normalizeTitle(raw);
        if (title == null) {
            throw new IllegalArgumentException("portalTitle must not be blank");
        }
        return title;
    }

    /**
     * Libellé montré au tiers (e-mail, portail) : {@code portalTitle} s'il est posé, sinon le nom BPMN
     * (D-PORTAL-TITLE).
     */
    public String titleForCitizen(String taskName) {
        return portalTitle != null ? portalTitle : taskName;
    }

    /** Le tiers doit s'authentifier par FAS ou itsme avant de voir ou de soumettre la tâche (T1). */
    public boolean requiresStrongAuthentication() {
        return identityField != null;
    }

    private static String normalizeTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String title = raw.replaceAll("[\\r\\n\\t]+", " ").strip();
        if (title.isEmpty()) {
            return null;
        }
        if (title.length() > MAX_PORTAL_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "portalTitle must be at most " + MAX_PORTAL_TITLE_LENGTH + " characters");
        }
        return title;
    }
}
