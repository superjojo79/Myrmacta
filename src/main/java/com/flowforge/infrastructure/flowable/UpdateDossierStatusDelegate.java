package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("updateDossierStatusTask")
class UpdateDossierStatusDelegate implements JavaDelegate {

    private final UpdateDossierStatusUseCase useCase;

    UpdateDossierStatusDelegate(UpdateDossierStatusUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Object dossierId = execution.getVariable("dossierId");
        Object status = DeployedActivityParameters.get(execution, "targetDossierStatus");
        if (dossierId == null || status == null) {
            throw new IllegalArgumentException("dossierId and targetDossierStatus variables are required");
        }
        useCase.update(
                new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(
                        UUID.fromString(dossierId.toString()),
                        DossierStatus.valueOf(status.toString()),
                        "workflow",
                        null),
                AccessContext.system("flowable-delegate"));
    }
}
