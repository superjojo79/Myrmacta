package be.myrmacta.domain.user.model;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TODO §114 (D-GROUP-SOURCE) — groupe d'utilisateurs (un service : RH, comptabilité…). Ses membres sont listés ici, ou
 * viennent du groupe {@code idpGroup} porté par le jeton OIDC (claim de groupes enregistrée à la connexion). Le code est
 * la valeur posée comme groupe candidat d'une tâche (D-GROUP-ASSIGN).
 */
public record UserGroup(String tenantId, String code, String label, String idpGroup, Set<String> memberIds) {

    public static final int MAX_CODE_CHARS = 64;
    public static final int MAX_LABEL_CHARS = 200;
    public static final int MAX_IDP_GROUP_CHARS = 256;
    public static final int MAX_MEMBERS = 500;

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{1," + (MAX_CODE_CHARS - 1) + "}");

    public UserGroup {
        Objects.requireNonNull(tenantId, "tenantId");
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("code must be 2 to " + MAX_CODE_CHARS + " characters: A-Z, 0-9 or _, starting with a letter");
        }
        label = label == null ? "" : label.strip();
        if (label.isEmpty() || label.length() > MAX_LABEL_CHARS || hasControl(label)) {
            throw new IllegalArgumentException("label is required, at most " + MAX_LABEL_CHARS + " characters, without control characters");
        }
        idpGroup = idpGroup == null || idpGroup.isBlank() ? null : idpGroup.strip();
        if (idpGroup != null && (idpGroup.length() > MAX_IDP_GROUP_CHARS || hasControl(idpGroup))) {
            throw new IllegalArgumentException("idpGroup must be at most " + MAX_IDP_GROUP_CHARS + " characters, without control characters");
        }
        memberIds = memberIds == null ? Set.of() : Set.copyOf(memberIds);
        if (memberIds.size() > MAX_MEMBERS) {
            throw new IllegalArgumentException("a group has at most " + MAX_MEMBERS + " listed members");
        }
    }

    /** Membre listé ici, ou porteur du groupe d'IdP repris. */
    public boolean includes(String userId, Set<String> userIdpGroups) {
        return memberIds.contains(userId) || (idpGroup != null && userIdpGroups != null && userIdpGroups.contains(idpGroup));
    }

    public UserGroup withMembers(Set<String> members) {
        return new UserGroup(tenantId, code, label, idpGroup, members);
    }

    private static boolean hasControl(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
