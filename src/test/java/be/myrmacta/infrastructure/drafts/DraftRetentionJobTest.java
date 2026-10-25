package be.myrmacta.infrastructure.drafts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import be.myrmacta.domain.workflow.port.out.BpmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.DmnDraftRepository;
import be.myrmacta.infrastructure.config.DraftRetentionProperties;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** TODO §82 (D6, D-SOCLE-RETENTION) — la purge vise les brouillons automatisés ; 0 jour = jamais. */
class DraftRetentionJobTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void purgesAutomatedDraftsOlderThanTheRetention() {
        BpmnDraftRepository bpmn = mock(BpmnDraftRepository.class);
        DmnDraftRepository dmn = mock(DmnDraftRepository.class);
        new DraftRetentionJob(bpmn, dmn, new TenantProperties("tenant_dev"), new DraftRetentionProperties(30), CLOCK)
                .purgeStaleAutomatedDrafts();
        Instant cutoff = Instant.parse("2026-10-01T12:00:00Z");
        verify(bpmn).deleteAutomatedOlderThan("tenant_dev", cutoff);
        verify(dmn).deleteAutomatedOlderThan("tenant_dev", cutoff);
    }

    @Test
    void zeroDaysMeansNeverPurge() {
        BpmnDraftRepository bpmn = mock(BpmnDraftRepository.class);
        DmnDraftRepository dmn = mock(DmnDraftRepository.class);
        new DraftRetentionJob(bpmn, dmn, new TenantProperties("tenant_dev"), new DraftRetentionProperties(0), CLOCK)
                .purgeStaleAutomatedDrafts();
        verifyNoInteractions(bpmn, dmn);
        org.mockito.Mockito.verify(bpmn, org.mockito.Mockito.never()).deleteAutomatedOlderThan(any(), any());
    }
}
