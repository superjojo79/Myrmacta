package com.flowforge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminListSortsTest {

    @Test
    void allowListMatchesDesignSortsExactly() {
        assertThat(AdminListSorts.DOSSIER_TYPES).isEqualTo("code,asc");
        assertThat(AdminListSorts.DOCUMENT_TEMPLATES).isEqualTo("updatedAt,desc");
        assertThat(AdminListSorts.CONNECTORS).isEqualTo("name,asc");
        assertThat(AdminListSorts.WORKFLOW_TYPE_CONFIGS).isEqualTo("workflowKey,asc");
        assertThat(AdminListSorts.NOTIFICATION_WEBHOOKS).isEqualTo("name,asc");
        assertThat(AdminListSorts.PURGE_OUTBOX).isEqualTo("createdAt,desc");
    }
}
