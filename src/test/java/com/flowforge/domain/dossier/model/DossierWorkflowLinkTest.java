package com.flowforge.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DossierWorkflowLinkTest {

    private static final Instant T0 = Instant.parse("2026-08-02T10:00:00Z");

    @Test
    void reserved_whenNoProcessInstanceAndNotEnded() {
        DossierWorkflowLink link = link(null, null);

        assertThat(link.isReserved()).isTrue();
        assertThat(link.isActive()).isFalse();
        assertThat(link.isTerminated()).isFalse();
    }

    @Test
    void active_whenProcessInstancePresentAndNotEnded() {
        DossierWorkflowLink link = link("pi-1", null);

        assertThat(link.isReserved()).isFalse();
        assertThat(link.isActive()).isTrue();
        assertThat(link.isTerminated()).isFalse();
    }

    @Test
    void terminated_whenEndedAtPresent() {
        DossierWorkflowLink link = link("pi-1", T0.plusSeconds(60));

        assertThat(link.isTerminated()).isTrue();
        assertThat(link.isActive()).isFalse();
        assertThat(link.isReserved()).isFalse();
    }

    @Test
    void withProcessInstance_confirmsStart() {
        DossierWorkflowLink reserved = link(null, null);

        DossierWorkflowLink confirmed = reserved.withProcessInstance("pi-9");

        assertThat(confirmed.processInstanceId()).isEqualTo("pi-9");
        assertThat(confirmed.isActive()).isTrue();
        assertThat(confirmed.id()).isEqualTo(reserved.id());
        assertThat(confirmed.startedBy()).isEqualTo(reserved.startedBy());
    }

    @Test
    void startedBy_isPreservedOnAbort() {
        DossierWorkflowLink reserved = link(null, null);

        DossierWorkflowLink aborted =
                reserved.abort(AbortReservedReason.ORPHAN_START_FAILED, T0.plusSeconds(1));

        assertThat(aborted.startedBy()).isEqualTo("user-starter");
    }

    @Test
    void abort_setsEndedAtAndReason() {
        DossierWorkflowLink reserved = link(null, null);

        DossierWorkflowLink aborted =
                reserved.abort(AbortReservedReason.ORPHAN_START_FAILED, T0.plusSeconds(1));

        assertThat(aborted.isTerminated()).isTrue();
        assertThat(aborted.endReason()).isEqualTo("ORPHAN_START_FAILED");
        assertThat(aborted.endedAt()).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThatThrownBy(() -> new DossierWorkflowLink(
                        null,
                        UUID.randomUUID(),
                        "tenant_a",
                        null,
                        "smokeProcess",
                        1,
                        "INIT",
                        "manual:u1",
                        T0,
                        null,
                        null,
                        "user-starter"))
                .isInstanceOf(NullPointerException.class);
    }

    private static DossierWorkflowLink link(String processInstanceId, Instant endedAt) {
        return new DossierWorkflowLink(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tenant_a",
                processInstanceId,
                "smokeProcess",
                1,
                "INIT",
                "manual:u1",
                T0,
                endedAt,
                endedAt == null ? null : "DONE",
                "user-starter");
    }
}
