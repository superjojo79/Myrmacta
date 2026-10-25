package be.myrmacta.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListSortsTest {

    @Test
    void allowListMatchesDesignSortsExactly() {
        assertThat(ListSorts.DOSSIER_TYPES).isEqualTo("code,asc");
        assertThat(ListSorts.DOCUMENT_TEMPLATES).isEqualTo("updatedAt,desc");
        assertThat(ListSorts.CONNECTORS).isEqualTo("name,asc");
        assertThat(ListSorts.WORKFLOW_TYPE_CONFIGS).isEqualTo("workflowKey,asc");
        assertThat(ListSorts.NOTIFICATION_WEBHOOKS).isEqualTo("name,asc");
        assertThat(ListSorts.PURGE_OUTBOX).isEqualTo("createdAt,desc");
    }

    @Test
    void allowListCoversContratApiClientResources() {
        assertThat(ListSorts.DOSSIER_ELEMENTS).isEqualTo("addedAt,asc");
        assertThat(ListSorts.MY_WORKFLOW_TASKS).isEqualTo("createdAt,desc");
        assertThat(ListSorts.CONNECTOR_DEFINITIONS).isEqualTo("type,asc");
        assertThat(ListSorts.DOSSIER_WORKFLOWS).isEqualTo("sequence,asc");
        assertThat(ListSorts.WORKFLOW_LINK_HISTORY).isEqualTo("startedAt,asc");
        assertThat(ListSorts.WORKFLOW_LINK_TASKS).isEqualTo("createdAt,asc");
    }
}
