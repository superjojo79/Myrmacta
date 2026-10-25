package be.myrmacta.domain.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentVariableFilterTest {

    @Test
    void stripsPlatformKeys_andKeepsBusinessVariables() {
        Map<String, Object> input = new HashMap<>();
        input.put("title", "Hello");
        input.put("dossierId", "uuid");
        input.put("dossierWorkflowLinkId", "link");
        input.put("tenantId", "tenant_dev");
        input.put("generatedDocumentId", "ref");
        input.put("documentType", "contrat");
        input.put("documentVariant", "premium");
        input.put("documentLocale", "fr");
        input.put("amount", 12);

        Map<String, Object> filtered = DocumentVariableFilter.forTemplateMerge(input);

        assertThat(filtered).containsEntry("title", "Hello").containsEntry("amount", 12);
        assertThat(filtered)
                .doesNotContainKeys(
                        "dossierId",
                        "dossierWorkflowLinkId",
                        "tenantId",
                        "generatedDocumentId",
                        "documentType",
                        "documentVariant",
                        "documentLocale");
    }

    @Test
    void nullOrEmpty_returnsEmptyMap() {
        assertThat(DocumentVariableFilter.forTemplateMerge(null)).isEmpty();
        assertThat(DocumentVariableFilter.forTemplateMerge(Map.of())).isEmpty();
    }
}
