package com.flowforge.infrastructure.persistence.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.workflow.model.BpmnDraft;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Couverture directe de {@link BpmnDraftJpaEntity.BpmnDraftId#equals}/{@code #hashCode}
 * (composite key {@code @IdClass}) et du mapping {@code fromDomain}/{@code toDomain}, à l'image
 * de {@code WorkflowIdempotencyJpaEntityTest}.
 */
class BpmnDraftJpaEntityTest {

    private static final tools.jackson.databind.ObjectMapper MAPPER = tools.jackson.databind.json.JsonMapper.builder().build();

    @Test
    void fromDomain_thenToDomain_roundTripsAllFields() {
        BpmnDraft draft = new BpmnDraft(
                "tenant_dev", "testProcess", "<bpmn/>", Instant.parse("2026-08-24T00:00:00Z"), "alice");

        BpmnDraft roundTripped = BpmnDraftJpaEntity.fromDomain(draft, MAPPER).toDomain(MAPPER);

        assertThat(roundTripped).isEqualTo(draft);
    }

    @Test
    void draftId_equals_sameInstance_isTrue() {
        var id = draftId("tenant_dev", "key-1");

        assertThat(id.equals(id)).isTrue();
    }

    @Test
    void draftId_equals_null_isFalse() {
        var id = draftId("tenant_dev", "key-1");

        assertThat(id.equals(null)).isFalse();
    }

    @Test
    void draftId_equals_differentType_isFalse() {
        var id = draftId("tenant_dev", "key-1");

        assertThat(id.equals("not-an-id")).isFalse();
    }

    @Test
    void draftId_equals_sameFields_isTrueAndHashCodeMatches() {
        var a = draftId("tenant_dev", "key-1");
        var b = draftId("tenant_dev", "key-1");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void draftId_equals_differentTenantId_isFalse() {
        var a = draftId("tenant_dev", "key-1");
        var b = draftId("tenant_other", "key-1");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void draftId_equals_differentProcessDefinitionKey_isFalse() {
        var a = draftId("tenant_dev", "key-1");
        var b = draftId("tenant_dev", "key-2");

        assertThat(a).isNotEqualTo(b);
    }

    private static BpmnDraftJpaEntity.BpmnDraftId draftId(String tenantId, String processDefinitionKey) {
        try {
            var id = new BpmnDraftJpaEntity.BpmnDraftId();
            Class<?> clazz = BpmnDraftJpaEntity.BpmnDraftId.class;
            set(clazz, id, "tenantId", tenantId);
            set(clazz, id, "processDefinitionKey", processDefinitionKey);
            return id;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Class<?> clazz, Object target, String field, Object value)
            throws ReflectiveOperationException {
        var f = clazz.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
