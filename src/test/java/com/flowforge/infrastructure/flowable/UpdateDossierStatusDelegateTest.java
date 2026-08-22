package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

/**
 * Couverture de branches manquante trouvée en clôturant fiabilisation-tests-plateforme
 * (D-COVERAGE, seuil BRANCH relevé) : {@link UpdateDossierStatusDelegate} n'avait aucun test.
 */
class UpdateDossierStatusDelegateTest {

    private final UpdateDossierStatusUseCase useCase = mock(UpdateDossierStatusUseCase.class);
    private final UpdateDossierStatusDelegate delegate = new UpdateDossierStatusDelegate(useCase);

    @Test
    void execute_missingDossierId_throwsIllegalArgument() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("dossierId")).thenReturn(null);
        when(execution.getVariable("targetDossierStatus")).thenReturn("OUVERT");

        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void execute_missingTargetStatus_throwsIllegalArgument() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("dossierId")).thenReturn(UUID.randomUUID().toString());
        when(execution.getVariable("targetDossierStatus")).thenReturn(null);

        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void execute_bothVariablesPresent_invokesUseCase() {
        UUID dossierId = UUID.randomUUID();
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("dossierId")).thenReturn(dossierId.toString());
        when(execution.getVariable("targetDossierStatus")).thenReturn("EN_COURS");

        delegate.execute(execution);

        verify(useCase).update(any(), any());
    }
}
