package be.myrmacta.domain.workflow;

import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class WorkflowVariables {

    public static final String DOSSIER_ID = "dossierId";
    public static final String DOSSIER_WORKFLOW_LINK_ID = "dossierWorkflowLinkId";
    public static final String TENANT_ID = "tenantId";

    /**
     * Task-completion variable read by {@code DocumentGenerationTask} (the {@code
     * ${documentGenerationTask} serviceTask}) to resolve which template to render — shared as a
     * constant, rather than duplicated as a string literal on both sides, after a Phase 9
     * adversarial review of TODO §52 found the frontend {@code document-generation} task form
     * emitting a different name ({@code templateKey}) than the delegate read, silently breaking
     * the whole kind.
     */
    public static final String DOCUMENT_TYPE = "documentType";

    /** Sibling constants to {@link #DOCUMENT_TYPE}, same {@code document-generation} kind (TODO §52). */
    public static final String DOCUMENT_VARIANT = "documentVariant";

    public static final String DOCUMENT_LOCALE = "documentLocale";

    /**
     * Instantané des champs dynamiques du dossier (voir {@code Dossier.fields()}), injecté
     * automatiquement à chaque démarrage de workflow — comble l'écart identifié avec le
     * {@code DossierAwareTask} décrit (mais jamais implémenté) dans
     * {@code docs/specs/04-dossiers.md} : un `serviceTask` ou un template de document n'a plus
     * besoin de propager explicitement chaque champ métier pour y accéder (`${dossierFields.x}`).
     */
    public static final String DOSSIER_FIELDS = "dossierFields";

    /**
     * Résultat du dernier appel de connecteur : écrit par {@code ConnectorServiceTask} seulement. Réservé (TODO §78, revue D3b B1) :
     * sinon une complétion de tâche pourrait forger un « résultat de connecteur » que la brique de mapping écrirait ensuite dans
     * le dossier sous l'identité système (blanchiment de provenance).
     */
    public static final String CONNECTOR_RESULT = "connectorResult";

    /**
     * Référence du PDF produit : écrite par {@code DocumentGenerationTask} seulement, lue par « Joindre le document ».
     * Réservée (issue #201, D-DOC-GENERATED-RESERVED) : sinon une complétion de tâche placée entre la génération et le
     * rattachement pourrait désigner le document d'un autre dossier, rattaché alors comme {@code PRODUCED_DOC} du dossier
     * courant (joignable à un e-mail, signable) — même blanchiment que {@link #CONNECTOR_RESULT}.
     */
    public static final String GENERATED_DOCUMENT_ID = "generatedDocumentId";

    /**
     * TODO §115 (D-DEADLINE-TIMER) — échéance écrite par la brique « Calculer une échéance » seulement : la date
     * ({@code AAAA-MM-JJ}), l'instant du rappel et l'instant où elle est dépassée (lendemain, 00:00 UTC), seuls noms qu'un
     * minuteur peut porter ({@code ${echeanceRappel}}, {@code ${echeanceDepassee}}). Réservées : sinon une complétion de
     * tâche pourrait repousser le minuteur, donc l'escalade.
     */
    public static final String ECHEANCE = "echeance";

    public static final String ECHEANCE_RAPPEL = "echeanceRappel";

    public static final String ECHEANCE_DEPASSEE = "echeanceDepassee";

    private static final Set<String> RESERVED = Set.of(
            DOSSIER_ID, DOSSIER_WORKFLOW_LINK_ID, TENANT_ID, DOSSIER_FIELDS, CONNECTOR_RESULT, GENERATED_DOCUMENT_ID, ECHEANCE,
            ECHEANCE_RAPPEL, ECHEANCE_DEPASSEE);

    /**
     * Business (non-platform) variable names already claimed by another task form kind —
     * checked by {@code TaskQuestionSchema} at save time (D-USERQ-VAR-COLLISION, TODO §55) so a
     * {@code user-question} schema field can never shadow {@code document-generation}'s
     * completion variables.
     */
    private static final Set<String> KNOWN_BUSINESS_KEYS =
            Set.of(DOCUMENT_TYPE, DOCUMENT_VARIANT, DOCUMENT_LOCALE);

    private WorkflowVariables() {}

    public static void assertNoReservedKeys(Map<String, Object> variables) {
        if (variables == null) {
            return;
        }
        for (String key : RESERVED) {
            if (variables.containsKey(key)) {
                throw new IllegalArgumentException("Workflow variable is reserved: " + key);
            }
        }
    }

    /** Noms refusés en entrée utilisateur ; le SPA en tient une copie ({@code startVariables.ts}), vérifiée par un test. */
    public static Set<String> reservedKeys() {
        return RESERVED;
    }

    public static boolean isReservedOrKnownKey(String key) {
        return RESERVED.contains(key) || KNOWN_BUSINESS_KEYS.contains(key);
    }

    /**
     * Fusionne les variables client avec les variables plateforme injectées au démarrage
     * (dossierId / dossierWorkflowLinkId / tenantId / dossierFields), partagé par les trois
     * chemins de start (API manuelle, relais outbox, réconciliation RESERVED).
     *
     * @param dossierFields l'instantané de {@code Dossier.fields()} au moment du démarrage —
     *     jamais rafraîchi automatiquement ensuite (une modification du dossier en cours de
     *     workflow n'est pas répercutée sur cette variable ; voir docs/guides/GUIDE-CONCEPTION-BPMN.md pour
     *     le mécanisme de rafraîchissement explicite si nécessaire).
     */
    public static Map<String, Object> withPlatformVariables(
            Map<String, Object> variables, DossierWorkflowLink link, Map<String, Object> dossierFields) {
        Objects.requireNonNull(link, "link");
        Map<String, Object> merged = new HashMap<>(variables == null ? Map.of() : variables);
        merged.put(DOSSIER_ID, link.dossierId().toString());
        merged.put(DOSSIER_WORKFLOW_LINK_ID, link.id().toString());
        merged.put(TENANT_ID, link.tenantId());
        merged.put(DOSSIER_FIELDS, dossierFields == null ? Map.of() : Map.copyOf(dossierFields));
        return merged;
    }
}
