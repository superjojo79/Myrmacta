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
 */
public record PortalTaskDefinition(
        String taskDefinitionKey, String schemaKey, PortalRecipientSource recipientSource, Integer ttlDays,
        PortalIdentityField identityField, List<String> pieceTypeKeys) {

    /** TODO §106 — au plus 10 types de pièces proposés au tiers par tâche. */
    public static final int MAX_PIECE_TYPES = 10;

    public PortalTaskDefinition {
        Objects.requireNonNull(taskDefinitionKey, "taskDefinitionKey");
        Objects.requireNonNull(schemaKey, "schemaKey");
        Objects.requireNonNull(recipientSource, "recipientSource");
        if (ttlDays != null && ttlDays <= 0) {
            throw new IllegalArgumentException("ttlDays must be positive");
        }
        pieceTypeKeys = pieceTypeKeys == null ? List.of() : List.copyOf(pieceTypeKeys);
    }

    public PortalTaskDefinition(
            String taskDefinitionKey, String schemaKey, PortalRecipientSource recipientSource, Integer ttlDays) {
        this(taskDefinitionKey, schemaKey, recipientSource, ttlDays, null, List.of());
    }

    public PortalTaskDefinition(
            String taskDefinitionKey, String schemaKey, PortalRecipientSource recipientSource, Integer ttlDays,
            PortalIdentityField identityField) {
        this(taskDefinitionKey, schemaKey, recipientSource, ttlDays, identityField, List.of());
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

    /** Le tiers doit s'authentifier par FAS ou itsme avant de voir ou de soumettre la tâche (T1). */
    public boolean requiresStrongAuthentication() {
        return identityField != null;
    }
}
