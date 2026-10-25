package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.port.in.GenerateDocumentUseCase;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.HashMap;
import java.util.Map;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

class DocumentGenerationTaskTest {

    @Test
    void execute_generatesStoresAndSetsVariable() {
        GenerateDocumentUseCase generate = mock(GenerateDocumentUseCase.class);
        DocumentStoragePort storage = mock(DocumentStoragePort.class);
        DocumentGenerationTask task =
                new DocumentGenerationTask(new TenantProperties("tenant_dev"), generate, storage);
        DelegateExecution execution = mock(DelegateExecution.class);
        Map<String, Object> vars = new HashMap<>();
        vars.put("documentType", "contrat");
        vars.put("title", "Hi");
        when(execution.getVariables()).thenReturn(vars);
        when(execution.getVariable("documentType")).thenReturn("contrat");
        when(execution.getVariable("documentVariant")).thenReturn(null);
        when(execution.getVariable("documentLocale")).thenReturn(null);
        when(generate.generate(any())).thenReturn("%PDF".getBytes());
        when(storage.store(eq("tenant_dev"), eq("application/pdf"), any())).thenReturn("tenant_dev/a.pdf");

        task.execute(execution);

        verify(storage).store(eq("tenant_dev"), eq("application/pdf"), any());
        verify(execution).setVariable("generatedDocumentId", "tenant_dev/a.pdf");
    }

    @Test
    void execute_missingDocumentType_fails() {
        DocumentGenerationTask task = new DocumentGenerationTask(
                new TenantProperties("tenant_dev"),
                mock(GenerateDocumentUseCase.class),
                mock(DocumentStoragePort.class));
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("documentType")).thenReturn(null);
        assertThatThrownBy(() -> task.execute(execution)).isInstanceOf(IllegalArgumentException.class);
    }
}
