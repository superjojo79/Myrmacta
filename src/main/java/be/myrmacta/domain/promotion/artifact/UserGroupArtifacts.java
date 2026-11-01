package be.myrmacta.domain.promotion.artifact;

import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.PlanAction;
import be.myrmacta.domain.promotion.model.PlanEntry;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.UserGroupDoc;
import be.myrmacta.domain.promotion.port.out.ArtifactCodec;
import be.myrmacta.domain.user.model.UserGroup;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TODO §117 (D-PROMO-GROUP) — groupes d'utilisateurs dans un bundle : code, libellé et groupe de l'IdP, <b>jamais les membres</b>.
 * {@code idpGroup} accepte {@code {{paramètre}}} (un paramètre absent bloque) et n'est appliqué qu'à la création : en mise à jour,
 * seul le libellé change, le groupe de l'IdP de la cible est conservé et le plan signale qu'il diffère.
 */
public final class UserGroupArtifacts {

    private final UserGroupsUseCase groups;
    private final ArtifactCodec codec;

    public UserGroupArtifacts(UserGroupsUseCase groups, ArtifactCodec codec) {
        this.groups = Objects.requireNonNull(groups, "groups");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public List<BundleEntry> export() {
        return groups.list().stream()
                .sorted(Comparator.comparing(UserGroup::code))
                .map(g -> new BundleEntry(ArtifactType.USER_GROUP, g.code(),
                        codec.encode(new UserGroupDoc(g.code(), g.label(), g.idpGroup()))))
                .toList();
    }

    /** Codes des groupes de la cible. */
    public Set<String> existingCodes() {
        return groups.list().stream().map(UserGroup::code).collect(Collectors.toUnmodifiableSet());
    }

    public PlanEntry plan(BundleEntry entry, Map<String, String> parameters, String tenantId) {
        UserGroupDoc raw = PromotionSupport.decode(codec, entry, UserGroupDoc.class);
        PromotionSupport.requireSameKey(entry, raw.code());
        UserGroup doc;
        try {
            // comparé tel que le domaine l'enregistrerait : libellé et groupe de l'IdP sans blancs (revue de clôture #279, B7)
            UserGroupDoc substituted = substitute(raw, parameters);
            doc = new UserGroup(tenantId, substituted.code(), substituted.label(), substituted.idpGroup(), Set.of());
        } catch (EnvironmentParameters.MissingParameterException | IllegalArgumentException e) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED, PromotionSupport.cap(e.getMessage()));
        }
        Optional<UserGroup> current = find(doc.code());
        if (current.isEmpty()) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.CREATE, "created without members: add them on this instance");
        }
        String idpNote = Objects.equals(current.get().idpGroup(), doc.idpGroup())
                ? ""
                : "identity-provider group of this instance kept ("
                        + (current.get().idpGroup() == null ? "none" : "'" + current.get().idpGroup() + "'") + " here, "
                        + (doc.idpGroup() == null ? "none" : "'" + doc.idpGroup() + "'") + " in the bundle)";
        if (current.get().label().equals(doc.label())) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, idpNote);
        }
        String reason = "label updated (members kept)" + (idpNote.isEmpty() ? "" : "; " + idpNote);
        return new PlanEntry(entry.type(), entry.key(), PlanAction.UPDATE, PromotionSupport.cap(reason));
    }

    public String apply(BundleEntry entry, PlanAction action, Map<String, String> parameters) {
        UserGroupDoc doc = substitute(PromotionSupport.decode(codec, entry, UserGroupDoc.class), parameters);
        if (action == PlanAction.CREATE) {
            groups.create(doc.code(), doc.label(), doc.idpGroup());
            return "created without members: add them on this instance";
        }
        UserGroup current = groups.get(doc.code());
        groups.update(doc.code(), doc.label(), current.idpGroup());
        return "label updated (members and identity-provider group kept)";
    }

    private Optional<UserGroup> find(String code) {
        return groups.list().stream().filter(g -> g.code().equals(code)).findFirst();
    }

    private static UserGroupDoc substitute(UserGroupDoc doc, Map<String, String> parameters) {
        String idp = doc.idpGroup() == null
                ? null
                : (String) EnvironmentParameters.substitute(doc.idpGroup(), parameters, "user group '" + doc.code() + "'");
        return new UserGroupDoc(doc.code(), doc.label(), idp);
    }
}
