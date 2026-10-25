package be.myrmacta.domain.document;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class DocumentVariableFilter {

    private static final Set<String> PLATFORM_KEYS = Set.of(
            "dossierId",
            "dossierWorkflowLinkId",
            "tenantId",
            "generatedDocumentId",
            "documentType",
            "documentVariant",
            "documentLocale");

    private DocumentVariableFilter() {}

    public static Map<String, Object> forTemplateMerge(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            if (entry.getKey() != null && !PLATFORM_KEYS.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(filtered);
    }
}
