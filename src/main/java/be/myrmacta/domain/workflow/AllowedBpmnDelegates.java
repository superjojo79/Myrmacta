package be.myrmacta.domain.workflow;

import java.util.Locale;
import java.util.Set;

/**
 * Allowlist of {@code flowable:delegateExpression} bean names that may appear in tenant-deployed
 * BPMN (TODO §57 H1). Anything else — {@code scriptTask}, {@code flowable:class},
 * {@code flowable:expression}, or an unknown delegate — is rejected at parse time.
 */
public final class AllowedBpmnDelegates {

    public static final Set<String> NAMES = Set.of(
            "connectorServiceTask",
            "documentGenerationTask",
            "attachGeneratedDocumentTask",
            "notificationDispatchTask",
            "createChildDossierTask",
            "updateDossierStatusTask",
            "updateWorkflowLinkStatusTask",
            // TODO §72 (signature-electronique) : bloc « Demander une signature ».
            "signatureRequestTask",
            // TODO §75 (langage-expression-metier, LC2) : bloc « Évaluer une expression » (JSONata borné, hors JUEL).
            "businessExpressionTask",
            // TODO §78 (D3b) : bloc « Enregistrer des valeurs dans le dossier » (expressions LC2 → champs, chemin métier).
            "fieldMappingTask",
            // TODO §102 (issue #184, lot B) : bloc « Rechercher des dossiers » (dossiers ouverts du même type).
            "dossierSearchTask",
            // TODO §106 (issue #211, lot B) : bloc « Pré-vérification IA » (asynchrone, sans outils, constats sourcés).
            "aiPrecheckTask",
            // TODO §113 (issue #217, lot 2b) : bloc « Router le courrier » (décision DMN → dossier métier lié ou file de tri).
            "routeMailTask",
            // TODO §113 (issue #217, lot 3a) : bloc « Rattacher au fil » (réponse à un e-mail connu → dossier du fil).
            "attachToThreadTask");

    private AllowedBpmnDelegates() {}

    public static boolean isAllowedDelegateExpression(String raw) {
        String bean = unwrapBeanName(raw);
        return bean != null && NAMES.contains(bean);
    }

    public static String unwrapBeanName(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}") && trimmed.length() > 3) {
            trimmed = trimmed.substring(2, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains(".") || lower.contains("(") || lower.contains(" ") || lower.contains("#")) {
            return null;
        }
        return trimmed;
    }
}
