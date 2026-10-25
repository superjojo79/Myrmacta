package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AttachGeneratedDocumentTaskTest {

    @Test
    void execute_existsFalse_doesNotInsert() {
        DocumentStoragePort storage = mock(DocumentStoragePort.class);
        AddDossierElementUseCase add = mock(AddDossierElementUseCase.class);
        AttachGeneratedDocumentTask task =
                new AttachGeneratedDocumentTask(new TenantProperties("tenant_dev"), storage, add);
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("dossierId")).thenReturn(UUID.randomUUID().toString());
        when(execution.getVariable("generatedDocumentId")).thenReturn("tenant_dev/missing.pdf");
        when(storage.exists("tenant_dev", "tenant_dev/missing.pdf")).thenReturn(false);

        assertThatThrownBy(() -> task.execute(execution)).isInstanceOf(IllegalStateException.class);
        verify(add, never()).add(any(), any());
    }

    @Test
    void execute_attachesProducedDocWithMetadata() {
        DocumentStoragePort storage = mock(DocumentStoragePort.class);
        AddDossierElementUseCase add = mock(AddDossierElementUseCase.class);
        AttachGeneratedDocumentTask task =
                new AttachGeneratedDocumentTask(new TenantProperties("tenant_dev"), storage, add);
        DelegateExecution execution = mock(DelegateExecution.class);
        UUID dossierId = UUID.randomUUID();
        when(execution.getVariable("dossierId")).thenReturn(dossierId.toString());
        when(execution.getVariable("generatedDocumentId")).thenReturn("tenant_dev/a.pdf");
        when(execution.getVariable("documentType")).thenReturn("contrat");
        when(execution.getVariable("documentVariant")).thenReturn("premium");
        when(execution.getVariable("documentLocale")).thenReturn("fr");
        when(execution.getCurrentActivityName()).thenReturn("Generate PDF");
        when(execution.getCurrentActivityId()).thenReturn("genTask");
        when(storage.exists("tenant_dev", "tenant_dev/a.pdf")).thenReturn(true);

        task.execute(execution);

        ArgumentCaptor<AddDossierElementUseCase.AddCommand> captor =
                ArgumentCaptor.forClass(AddDossierElementUseCase.AddCommand.class);
        verify(add).add(captor.capture(), any());
        AddDossierElementUseCase.AddCommand cmd = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(cmd.dossierId()).isEqualTo(dossierId);
        org.assertj.core.api.Assertions.assertThat(cmd.label()).contains("Generate PDF");
        org.assertj.core.api.Assertions.assertThat(cmd.metadata())
                .containsEntry("templateKey", "contrat")
                .containsEntry("contentType", "application/pdf")
                .containsEntry("variantKey", "premium")
                .containsEntry("locale", "fr");
        org.assertj.core.api.Assertions.assertThat(cmd.workflowStepId()).isEqualTo("genTask");
        verify(storage).exists(eq("tenant_dev"), eq("tenant_dev/a.pdf"));
    }
}
