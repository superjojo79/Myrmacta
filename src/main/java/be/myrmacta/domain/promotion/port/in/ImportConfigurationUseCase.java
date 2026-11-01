package be.myrmacta.domain.promotion.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.promotion.model.ConfigImportRecord;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;
import be.myrmacta.domain.promotion.model.ImportPlan;
import be.myrmacta.domain.promotion.model.ImportReport;
import java.util.List;
import java.util.Map;

/**
 * TODO §83 (D4) — import d'un bundle : plan à blanc (aucune écriture) puis application idempotente. Réservé à
 * {@code TENANT_ADMIN}. Tout passe par les cas d'usage existants ; BPMN et DMN deviennent des brouillons à publier.
 */
public interface ImportConfigurationUseCase {

    /** Canal d'appel : l'application MCP n'accepte jamais de secret (D-PROMO-MCP). */
    enum Channel {
        ADMIN,
        MCP
    }

    /**
     * @param parameters valeurs des paramètres d'environnement : une valeur de configuration de connecteur de la forme
     *     {@code {{nom}}} est remplacée à l'import ; un paramètre non fourni bloque l'artefact
     */
    default ImportPlan plan(AccessContext.User actor, ConfigurationBundle bundle, Map<String, String> parameters) {
        return plan(actor, bundle, parameters, Channel.ADMIN);
    }

    /**
     * TODO §117 (D-PROMO-ORPHAN, issue #285) — le plan tient compte du canal qui l'appliquera : le canal {@code MCP} n'applique
     * jamais un groupe, un groupe que seul le bundle apporte n'y compte donc pas comme connu.
     */
    ImportPlan plan(AccessContext.User actor, ConfigurationBundle bundle, Map<String, String> parameters, Channel channel);

    /**
     * @param secrets secrets à la création d'un connecteur : {@code nomDuConnecteur → (champ → valeur)} ; ignorés (refusés) hors
     *     canal {@code ADMIN} ; jamais journalisés
     * @param expectedBundleSha256 somme du bundle planifié : l'application refuse un autre bundle ({@code null} : pas de garde)
     */
    ImportReport apply(
            AccessContext.User actor,
            ConfigurationBundle bundle,
            Map<String, Map<String, String>> secrets,
            Map<String, String> parameters,
            String expectedBundleSha256,
            Channel channel);

    List<ConfigImportRecord> recentImports(AccessContext.User actor, int limit);
}
