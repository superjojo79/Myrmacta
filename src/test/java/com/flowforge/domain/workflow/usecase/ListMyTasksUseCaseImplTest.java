package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.MyWorkflowTask;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.port.out.WorkflowTaskInboxPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListMyTasksUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String SUB = "user-1";

    @Mock
    private WorkflowTaskInboxPort inboxPort;
    @Mock
    private WorkflowEnginePort workflowEnginePort;
    @Mock
    private DossierWorkflowLinkRepository linkRepository;
    @Mock
    private DossierRepository dossierRepository;

    @Test
    void list_enrichesAssignedTasksAndSkipsMissingDossierId() {
        Instant created = Instant.parse("2026-08-12T10:00:00Z");
        UUID dossierId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID linkId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        PageQuery query = PageQuery.of(0, 20);
        when(inboxPort.findActiveAssignedTo(eq(SUB), eq(query))).thenReturn(PageResult.of(
                List.of(
                        new WorkflowTaskInboxPort.ActiveTaskSnapshot(
                                "task-ok", "Instruction", SUB, created, "pi-1"),
                        new WorkflowTaskInboxPort.ActiveTaskSnapshot(
                                "task-skip", "Orphan", SUB, created, "pi-2")),
                0, 20, 2));
        when(workflowEnginePort.findDossierIdVariable("pi-1")).thenReturn(Optional.of(dossierId.toString()));
        when(workflowEnginePort.findDossierIdVariable("pi-2")).thenReturn(Optional.empty());
        when(linkRepository.findByProcessInstanceId(TENANT, "pi-1")).thenReturn(Optional.of(link(linkId, dossierId)));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId)));

        ListMyTasksUseCaseImpl useCase = new ListMyTasksUseCaseImpl(
                TENANT, inboxPort, workflowEnginePort, linkRepository, dossierRepository);

        PageResult<MyWorkflowTask> result = useCase.listMine(SUB, query);

        assertThat(result.content()).containsExactly(new MyWorkflowTask(
                "task-ok",
                "Instruction",
                SUB,
                created,
                "pi-1",
                dossierId,
                "DOS-1",
                linkId));
    }

    private static DossierWorkflowLink link(UUID linkId, UUID dossierId) {
        return new DossierWorkflowLink(
                linkId,
                dossierId,
                TENANT,
                "pi-1",
                "instruction-sinistre",
                1,
                "ACTIVE",
                "trigger",
                Instant.parse("2026-08-01T00:00:00Z"),
                null,
                null,
                SUB);
    }

    private static Dossier dossier(UUID dossierId) {
        return new Dossier(
                dossierId,
                TENANT,
                "DOS-1",
                "SINISTRE",
                "Label",
                DossierStatus.EN_COURS,
                Map.of(),
                null,
                0L,
                SUB,
                Instant.parse("2026-08-01T00:00:00Z"),
                SUB,
                Instant.parse("2026-08-01T00:00:00Z"),
                null,
                null,
                null);
    }
}
